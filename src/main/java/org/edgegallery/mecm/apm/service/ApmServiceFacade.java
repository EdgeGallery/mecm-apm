/*
 *  Copyright 2020 Huawei Technologies Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.edgegallery.mecm.apm.service;

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getProtocol;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.isSuffixExist;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.saveInputStreamToFile;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.ERROR;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.edgegallery.mecm.apm.apihandler.ApmSyncHandler;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackageInfo;
import org.edgegallery.mecm.apm.model.AppPackageSyncInfo;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.AppStore;
import org.edgegallery.mecm.apm.model.AppTemplate;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.SwImageDescr;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageInfoDto;
import org.edgegallery.mecm.apm.model.dto.AppTemplateDto;
import org.edgegallery.mecm.apm.model.dto.AppTemplateInputAttrDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.model.dto.templatedto.Cpu;
import org.edgegallery.mecm.apm.model.dto.templatedto.Disk;
import org.edgegallery.mecm.apm.model.dto.templatedto.EdgeResourceInfo;
import org.edgegallery.mecm.apm.model.dto.templatedto.Mem;
import org.edgegallery.mecm.apm.model.dto.templatedto.Resource;
import org.edgegallery.mecm.apm.model.dto.templatedto.ResourceInfo;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.edgegallery.mecm.apm.utils.ApmV2Response;
import org.edgegallery.mecm.apm.utils.CompressUtility;
import org.edgegallery.mecm.apm.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Getter
@Setter
@Service("ApmServiceFacade")
public class ApmServiceFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApmServiceFacade.class);
    private static final String TAR_GZ = ".tar.gz";
    private static final String LCMCONTROLLER_URL = "/lcmcontroller/v2/tenants/";
    private static final String PACKAGES_URL = "/packages/";
    private static final String HTTPS = "https://";
    private static final String PATH_DELIMITER = "/";
    private static final String CSAR = ".csar";

    @Autowired
    private ApmService apmService;

    @Autowired
    private ApmSyncHandler apmSyncHandler;

    @Autowired
    private DbService dbService;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

    private String localPackagePath;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${apm.mecm-repo-endpoint:}")
    private String mecmRepoEndpoint;

    @Value("${apm.push-image:}")
    private String uploadDockerImage;

    @Value("${server.ssl.enabled:false}")
    private String isSslEnabled;

    /**
     * Updates Db and distributes docker application image to host.
     *
     * @param accessToken   access token
     * @param tenantId      tenant ID
     * @param appPackageDto appPackage details
     * @param syncAppPkg    app package sync info
     */
    @Async
    public void onboardApplication(String accessToken, String tenantId, AppPackageDto appPackageDto,
                                   PkgSyncInfo syncAppPkg) {
        String packageId = appPackageDto.getAppPkgId();
        List<SwImageDescr> imageInfoList;

        try {
            InputStream stream = apmService.downloadAppPackage(appPackageDto.getAppPkgPath(), packageId, accessToken);
            String localFilePath = saveInputStreamToFile(stream, packageId, tenantId, localDirPath);

            imageInfoList = apmService.getAppImageInfo(tenantId, localFilePath, appPackageDto.getAppPkgId());
            String appDeployType = apmService.getAppPackageDeploymentType(tenantId, appPackageDto.getAppPkgId());

            AppTemplate appTemplate = apmService.getApplicationTemplateInfo(appPackageDto, tenantId, appDeployType);
            dbService.createOrUpdateAppTemplate(tenantId, appTemplate);

            if ("container".equalsIgnoreCase(appDeployType)) {
                onboardContainerBasedAppPkg(accessToken, tenantId, appPackageDto, syncAppPkg, imageInfoList);
                addAppSyncInfoDb(appPackageDto, syncAppPkg, Constants.SUCCESS);
            } else if ("vm".equalsIgnoreCase(appDeployType)) {
                onboardVmBasedAppPkg(accessToken, tenantId, appPackageDto);
                addAppSyncInfoDb(appPackageDto, syncAppPkg, Constants.SUCCESS);
            }
        } catch (ApmException ex) {
            LOGGER.error(DISTRIBUTION_FAILED, ex.getMessage());
            return;
        }
        LOGGER.info("On-boading completed...");
    }

    /**
     * Distributes docker application image to host.
     *
     * @param accessToken   access token
     * @param tenantId      tenant ID
     * @param appPackageDto appPackage details
     * @param localFilePath local package path
     * @param syncAppPkg    sync application package
     */
    @Async
    public void onboardApplication(String accessToken, String tenantId, AppPackageDto appPackageDto,
                                   String localFilePath, PkgSyncInfo syncAppPkg) {

        try {
            List<SwImageDescr> imageInfoList = apmService.getAppImageInfo(tenantId, localFilePath,
                                                                          appPackageDto.getAppPkgId());
            String appDeployType = apmService.getAppPackageDeploymentType(tenantId, appPackageDto.getAppPkgId());

            AppTemplate appTemplate = apmService.getApplicationTemplateInfo(appPackageDto, tenantId, appDeployType);
            dbService.createOrUpdateAppTemplate(tenantId, appTemplate);

            if ("container".equalsIgnoreCase(appDeployType)) {
                onboardContainerBasedAppPkg(accessToken, tenantId, appPackageDto, syncAppPkg, imageInfoList);
            } else if ("vm".equalsIgnoreCase(appDeployType)) {
                onboardVmBasedAppPkg(accessToken, tenantId, appPackageDto);
            }
        } catch (ApmException | IllegalArgumentException ex) {
            LOGGER.error(DISTRIBUTION_FAILED, ex.getMessage());
            return;
        }
        LOGGER.info("On-boading completed...");
    }

    private boolean isDockerImageAvailableInPkg(String dockerImage) {
        return isSuffixExist(dockerImage, ".tar") || isSuffixExist(dockerImage, TAR_GZ)
                || isSuffixExist(dockerImage, ".tgz");
    }

    private void onboardContainerBasedAppPkg(String accessToken, String tenantId, AppPackageDto appPackageDto,
                                             PkgSyncInfo syncAppPkg, List<SwImageDescr> imageInfoList) {
        String packageId = appPackageDto.getAppPkgId();
        String dockerImgspath;
        boolean downloadImg = true;
        Set<String> loadedImgs = new HashSet<>();
        try {
            for (SwImageDescr imageDescr : imageInfoList) {
                if (isDockerImageAvailableInPkg(imageDescr.getSwImage())) {
                    downloadImg = false;

                    LOGGER.info("application package contains docker images...");
                    dockerImgspath = apmService.unzipDockerImages(appPackageDto.getAppPkgId(), tenantId);
                    apmService.loadDockerImages(packageId, imageInfoList, loadedImgs);

                    FileUtils.deleteQuietly(new File(dockerImgspath + ".zip"));
                    FileUtils.deleteQuietly(new File(dockerImgspath));

                    break;
                }
            }

            syncDockerImagesFromSrcToMecmRepo(appPackageDto, imageInfoList, syncAppPkg, downloadImg, accessToken);

            apmService.updateAppPackageWithRepoInfo(tenantId, packageId);

            String sourceDir = apmService.getLocalIntendedDir(packageId, tenantId);
            CompressUtility.compressAppPackage(sourceDir, sourceDir + File.separator + packageId + CSAR);
        } catch (ApmException | IllegalArgumentException ex) {
            LOGGER.error(DISTRIBUTION_FAILED, ex.getMessage());
            apmService.deleteAppPkgDockerImages(loadedImgs);
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, ex.getMessage());
            throw new ApmException(ex.getMessage());
        }

        distributeApplication(tenantId, appPackageDto, accessToken);
    }

    private void onboardVmBasedAppPkg(String accessToken, String tenantId, AppPackageDto appPackageDto) {

        String sourceDir = apmService.getLocalIntendedDir(appPackageDto.getAppPkgId(), tenantId);
        CompressUtility.compressAppPackage(sourceDir, sourceDir + File.separator + appPackageDto.getAppPkgId() + CSAR);

        distributeApplication(tenantId, appPackageDto, accessToken);

        LOGGER.info("On-boading vm based applicaiton package completed...");
    }

    private void addAppSyncInfoDb(AppPackageDto appPackageDto, PkgSyncInfo syncInfo, String operationalInfo) {

        //Check if app package sync info exist in DB.
        if (!dbService.isAppPackageSyncInfoExistInDb(appPackageDto.getAppPkgId())) {
            AppPackageInfo pkgInfo = new AppPackageInfo();
            pkgInfo.setAppPkgInfoId(appPackageDto.getAppPkgId());
            pkgInfo.setAppId(appPackageDto.getAppId());
            pkgInfo.setPackageId(syncInfo.getPackageId());
            pkgInfo.setName(appPackageDto.getAppPkgName());
            pkgInfo.setSyncStatus(Constants.APP_IN_SYNC);
            pkgInfo.setAppstoreIp(syncInfo.getAppstoreIp());
            pkgInfo.setOperationalInfo(operationalInfo);
            pkgInfo.setShortDesc(appPackageDto.getAppPkgDesc());
            pkgInfo.setProvider(appPackageDto.getAppProvider());
            pkgInfo.setAffinity(appPackageDto.getAppPkgAffinity());
            pkgInfo.setVersion(appPackageDto.getAppPkgVersion());
            pkgInfo.setAppstoreEndpoint(syncInfo.getAppstoreIp() + ":" + syncInfo.getAppstorePort());
            dbService.addAppSyncPackageInfoDB(pkgInfo);
        }
    }

    /**
     * Returns app package info.
     *
     * @param tenantId     tenant ID
     * @param appPackageId app package ID
     * @return app package info
     */
    public AppPackageDto getAppPackageInfo(String tenantId, String appPackageId) {
        return dbService.getAppPackageWithHost(tenantId, appPackageId);
    }

    /**
     * Returns app template info.
     *
     * @param tenantId     tenant ID
     * @param appPackageId app package ID
     * @return app package info
     */
    public AppTemplate getAppTemplateInfo(String tenantId, String appPackageId) {
        return dbService.getApplicationTemplate(tenantId, appPackageId);
    }

    /**
     * Deletes app package.
     *
     * @param tenantId     tenant ID
     * @param appPackageId app package ID
     * @return hosts on which app package to be deleted
     */
    public List<String> deleteAppPackage(String tenantId, String appPackageId) {
        dbService.deleteAppPackage(tenantId, appPackageId);
        List<String> hosts = dbService.deleteHost(tenantId, appPackageId);
        apmService.deleteAppPackageFile(ApmServiceHelper.getPackageDirPath(localDirPath, appPackageId, tenantId));
        return hosts;
    }

    /**
     * Deletes distributed application package on host.
     *
     * @param tenantId    tenant ID
     * @param hostIp      host ip
     * @param packageId   package ID
     * @param accessToken access token
     * @throws ApmException exception if failed to get edge repository details
     */
    public void deleteDistributedAppPackageOnHost(String tenantId, String hostIp,
                                                  String packageId, String accessToken) {
        try {
            String mepmEndPoint = apmService.getMepmCfgOfHost(tenantId, hostIp, accessToken);

            String url = new StringBuilder(getProtocol(isSslEnabled)).append(mepmEndPoint)
                    .append(LCMCONTROLLER_URL).append(tenantId)
                    .append(PACKAGES_URL).append(packageId).append("/hosts/").append(hostIp).toString();

            apmService.sendDeleteRequest(url, accessToken);
        } catch (NoSuchElementException | ApmException ex) {
            LOGGER.error("failed to delete package on host {}", hostIp);
        }
    }

    /**
     * Deletes distributed application package.
     *
     * @param tenantId    tenant ID
     * @param hostIp      host ip
     * @param packageId   package ID
     * @param accessToken access token
     * @throws ApmException exception if failed to get edge repository details
     */
    public void deleteDistributedAppPackage(String tenantId, String hostIp, String packageId, String accessToken) {
        try {
            String mepmEndPoint = apmService.getMepmCfgOfHost(tenantId, hostIp, accessToken);

            String url = new StringBuilder(getProtocol(isSslEnabled)).append(mepmEndPoint)
                    .append(LCMCONTROLLER_URL).append(tenantId)
                    .append(PACKAGES_URL).append(packageId).toString();

            apmService.sendDeleteRequest(url, accessToken);
        } catch (NoSuchElementException | ApmException ex) {
            LOGGER.error("failed to delete package on host {}", hostIp);
        }
    }

    /**
     * Returns list of app package info.
     *
     * @param tenantId tenant ID
     * @return list of app package info
     */
    public List<AppPackageDto> getAllAppPackageInfo(String tenantId) {
        return dbService.getAllAppPackage(tenantId);
    }

    /**
     * Deletes app package in host.
     *
     * @param tenantId     tenant ID
     * @param appPackageId app package ID
     * @param hostIp       host Ip
     */
    public void deleteAppPackageInHost(String tenantId, String appPackageId,
                                       String hostIp) {
        dbService.deleteHostWithIp(tenantId, appPackageId, hostIp);
    }

    /**
     * Returns app package csar file.
     *
     * @param tenantId  tenant ID
     * @param packageId package ID
     * @return app package csar file
     */
    public InputStream getAppPackageFile(String tenantId, String packageId) {
        return apmService.getAppPackageFile(ApmServiceHelper.getLocalFilePath(localDirPath, packageId, tenantId));
    }

    /**
     * Create app package record in db.
     *
     * @param tenantId      tenant ID
     * @param appPackageDto app package dto
     */
    public void createAppPackageEntryInDb(String tenantId, AppPackageDto appPackageDto) {
        dbService.createAppPackage(tenantId, appPackageDto);
        dbService.createHost(tenantId, appPackageDto);
    }

    private void syncDockerImagesFromSrcToMecmRepo(AppPackageDto appPackageDto,
                                                   List<SwImageDescr> imageInfoList, PkgSyncInfo syncAppPkg,
                                                   boolean downloadImage, String accessToken) {
        String packageId = appPackageDto.getAppPkgId();
        Set<String> downloadedImgs = null;
        Set<String> uploadedImgs = null;
        boolean imagesInSync = false;

        try {
            AppPackageInfo appPkinfoDb = dbService.getAppPackageSyncInfo(packageId);
            if (Constants.APP_IN_SYNC.equals(appPkinfoDb.getSyncStatus())) {
                imagesInSync = true;
            }
        } catch (NoSuchElementException ex) {
            LOGGER.error("Image is not in sync");
        }

        try {
            if (!imagesInSync && Boolean.parseBoolean(uploadDockerImage)) {
                LOGGER.info("application package not in sync, download images and upload to mecm repo");

                imageInfoList = getImagesExcludingAlreadyUploaded(imageInfoList, accessToken);

                //Download docker images if path in swImagewDescr refers to remote
                if (downloadImage) {
                    downloadedImgs = new HashSet<>();
                    apmService.downloadAppImage(syncAppPkg, imageInfoList, downloadedImgs);
                }

                uploadedImgs = new HashSet<>();
                apmService.uploadAppImage(imageInfoList, uploadedImgs);
            }
        } catch (ApmException e) {
            throw new ApmException(e.getMessage());
        } finally {
            apmService.deleteAppPkgDockerImages(downloadedImgs);
            apmService.deleteAppPkgDockerImages(uploadedImgs);
        }
    }

    private void distributeApplication(String tenantId, AppPackageDto appPackageDto, String accessToken) {
        String packageId = appPackageDto.getAppPkgId();
        for (MecHostDto host : appPackageDto.getMecHostInfo()) {
            String distributionStatus;
            String error = "";
            try {
                LOGGER.info("Entering distribution flow");
                uploadAndDistributeApplicationPackage(accessToken, host.getHostIp(), tenantId,
                        appPackageDto.getAppId(), packageId);
                //  wait for distribution status to fetch from aapplcm
                String mepmEndPoint = apmService.getMepmCfgOfHost(tenantId, host.getHostIp(), accessToken);
                updateDistributionStatus(mepmEndPoint, tenantId, packageId, accessToken, error, host.getHostIp());

            } catch (ApmException e) {
                distributionStatus = ERROR;
                error = e.getMessage();
                LOGGER.error(Constants.DISTRIBUTION_IN_HOST_FAILED, packageId, host.getHostIp());
                dbService.updateDistributionStatusOfHost(tenantId, packageId, host.getHostIp(), distributionStatus,
                        error);
                throw new ApmException(e.getMessage());
            }
        }
    }

    private void updateDistributionStatus(String mepmEndPoint, String tenantId, String packageId, String accessToken,
                                          String error, String host) {
        String status = "";
        String response = "";
        boolean timeout = false;
        JsonArray jsonarray = new JsonArray();

        for (int i = 0; i < 20; i++) {
            response = getAppPkgDistributionStatus(mepmEndPoint, tenantId,
                    packageId, accessToken);
            LOGGER.info("response is : {} attempt no. {}", response, i);
            JsonObject json = new JsonParser().parse(response).getAsJsonObject();
            JsonArray output = json.get("data").getAsJsonArray();

            for (JsonElement hosts : output) {
                jsonarray = hosts.getAsJsonObject().get("mecHostInfo").getAsJsonArray();
            }
            for (JsonElement element : jsonarray) {
                status = element.getAsJsonObject().get("status").getAsString();
            }

            if (status.equalsIgnoreCase("Distributing")
                    || status.equalsIgnoreCase("uploading")) {
                try {
                    Thread.sleep(30 * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("InterruptedException in updateDistributionStatus");
                }
                timeout = true;
            }
            if (status.equalsIgnoreCase("Distributed")
                    || status.equalsIgnoreCase("Error")
                    || status.equalsIgnoreCase("uploaded")) {
                timeout = false;
                LOGGER.info("status is : {} attempt no. {}", status, i);
                break;
            }
            LOGGER.info("status is : {} attempt no. {}", status, i);
        }

        if (timeout) {
            status = "Timeout";
        }
        dbService.updateDistributionStatusOfHost(tenantId, packageId, host,
                status, error);
        LOGGER.info("Application package {}, on-boarding on {} completed...", packageId, host);
    }

    private String getAppPkgDistributionStatus(String mepmEndPoint, String tenantId,
                                               String pkgId, String accessToken) {
        LOGGER.info("distribute application package ");
        String url = new StringBuilder(getProtocol(isSslEnabled)).append(mepmEndPoint)
                .append(LCMCONTROLLER_URL).append(tenantId)
                .append(PACKAGES_URL).append(pkgId).toString();
        return apmService.sendGetRequest(url, accessToken);
    }

    /**
     * Returns manifest for a given docker image reference.
     *
     * @param repo        repository endpoint
     * @param repository  docker repository
     * @param tag         docker image tag
     * @param accessToken access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public String checkIfManifestPresentRepo(String repo, String repository, String tag, String accessToken) {
        String url;
        String[] repos = repo.split(":");

        if (repos.length == 1) {
            url = new StringBuilder(getProtocol(isSslEnabled)).append(repo).append(":")
                    .append("443").append("/v2")
                    .append(repository)
                    .append("/manifests/")
                    .append(tag).toString();
        } else {
            url = new StringBuilder(getProtocol(isSslEnabled)).append(repo).append("/v2/")
                    .append(repository)
                    .append("/manifests/")
                    .append(tag).toString();
        }
        LOGGER.info("check if manifest available in repo: {}", url);

        String response = apmService.sendGetRequest(url, accessToken);
        LOGGER.info("manifest query result {}", response);
        return response;
    }

    private List<SwImageDescr> getImagesExcludingAlreadyUploaded(List<SwImageDescr> imageInfoList,
                                                                 String accessToken) {

        List<SwImageDescr> imagesLstExcImgsInRepo = new LinkedList<>();
        if (imageInfoList == null) {
            LOGGER.error("swImageDescr image info list is null");
            throw new ApmException("swImageDescr image info list is null");
        }

        for (SwImageDescr imageInfo : imageInfoList) {
            try {
                String tag = imageInfo.getVersion();
                String name = imageInfo.getName();
                if (tag == null || name == null) {
                    LOGGER.error("could not find image name or image version in descriptor");
                    throw new ApmException("could not find image name or image version in descriptor");
                }
                String[] imageName = name.split(":");
                String mecmRepository = "/mecm/" + imageName[0];

                checkIfManifestPresentRepo(mecmRepoEndpoint, mecmRepository, tag, accessToken);

                LOGGER.info("image is available in repo, skip download/upload {}", imageInfo.getSwImage());
            } catch (ApmException | NoSuchElementException ex) {
                imagesLstExcImgsInRepo.add(imageInfo);
                LOGGER.error("image is not available in repo, download image: {}", imageInfo.getSwImage());
            }
        }
        return imagesLstExcImgsInRepo;
    }

    /**
     * Upload and distribute application package on the edge host.
     *
     * @param accessToken access token
     * @param hostIp      host IP
     * @param tenantId    tenant ID
     * @param appId       add ID
     * @param packageId   package ID
     */
    @Async
    public void uploadAndDistributeApplicationPackage(String accessToken, String hostIp, String tenantId,
                                                      String appId, String packageId) {
        try {
            String mepmEndPoint = apmService.getMepmCfgOfHost(tenantId, hostIp, accessToken);

            uploadApplicationPackage(mepmEndPoint, tenantId, appId, packageId, accessToken);

            distributeApplicationPackage(mepmEndPoint, tenantId, packageId, hostIp, accessToken);
        } catch (ApmException | NoSuchElementException ex) {
            LOGGER.error("failed to upload and distribute application package {} on host {}", packageId, hostIp);
            throw new ApmException("failed to upload and distribute application");
        }
    }

    private void distributeApplicationPackage(String mepmEndPoint, String tenantId,
                                              String pkgId, String hostIp, String accessToken) {
        LOGGER.info("distribute application package");
        String url = new StringBuilder(getProtocol(isSslEnabled)).append(mepmEndPoint)
                .append(LCMCONTROLLER_URL).append(tenantId)
                .append(PACKAGES_URL).append(pkgId).toString();

        List<String> hosts = new LinkedList<>();
        hosts.add(hostIp);
        Map<String, List<String>> hostsMap = new HashMap<>();
        hostsMap.put("hostIp", hosts);
        apmService.sendPostRequest(url, new Gson().toJson(hostsMap).toString(), accessToken);
    }

    private void uploadApplicationPackage(String mepmEndPoint, String tenantId,
                                          String appId, String pkgId, String accessToken) {
        LOGGER.info("upload application package");
        String url = new StringBuilder(getProtocol(isSslEnabled)).append(mepmEndPoint)
                .append(LCMCONTROLLER_URL).append(tenantId).append("/packages").toString();
        try {
            String packagePath = new StringBuilder(localDirPath).append(File.separator).append(pkgId)
                    .append(tenantId).append(PATH_DELIMITER).append(pkgId).append(CSAR).toString();
            FileSystemResource appPkgRes = new FileSystemResource(new File(packagePath));

            // Preparing request parts.
            LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("package", appPkgRes);
            parts.add("packageId", pkgId);
            parts.add("appId", appId);

            sendRequestWithMultipartFormData(url, parts, accessToken);
        } catch (InvalidPathException e) {
            LOGGER.error("package ID is invalid");
            throw new ApmException("invalid package path");
        } catch (ApmException e) {
            LOGGER.error("failed to upload package  {}", e.getMessage());
            throw new ApmException("upload package failed " + e.getMessage());
        }
    }

    /**
     * Send request to remote entity.
     *
     * @param url         request url
     * @param data        multipart request details
     * @param accessToken access token
     */
    private void sendRequestWithMultipartFormData(String url, LinkedMultiValueMap<String, Object> data,
                                                  String accessToken) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("access_token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(data, headers);
        try {
            LOGGER.info("upload app package {}", url);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!HttpStatus.OK.equals(response.getStatusCode())) {
                LOGGER.error("upload failed, return code {}", response.getStatusCode());
                throw new ApmException("returned error from remote entity, error code {}" + response.getStatusCode());
            }
        } catch (ResourceAccessException ex) {
            LOGGER.error("upload failed, resource exception {}", ex.getMessage());
            throw new ApmException("Resource access exception" + ex.getMessage());
        } catch (HttpServerErrorException | HttpClientErrorException ex) {
            LOGGER.error("upload failed, http error exception {}", ex.getMessage());
            throw new ApmException("failed to connect" + ex.getMessage());
        }
        LOGGER.info("application package uploaded successfully");
    }

    /**
     * Returns application store configuration.
     *
     * @param appstoreIp  appstore IP
     * @param accessToken access token
     * @return app store configuration
     */
    public AppStore getAppstoreConfig(String appstoreIp, String accessToken) {

        return apmService.getAppStoreCfgFromInventory(appstoreIp, accessToken);
    }

    /**
     * Returns application repo configuration.
     *
     * @param accessToken access token
     * @return app store configuration
     */
    public List<AppRepo> getAllAppRepoConfig(String accessToken) {

        return apmService.getAllAppRepoCfgFromInventory(accessToken);
    }

    /**
     * Returns application package info.
     *
     * @param appstoreEndPoint appstore end point
     * @param accessToken      access token
     * @return app store configuration
     */
    public List<AppPackageInfoDto> getAppPackagesInfo(String appstoreEndPoint, String accessToken) {

        return apmService.getAppPackagesInfoFromAppStore(appstoreEndPoint, accessToken);
    }

    /**
     * Retrieve app package info.
     *
     * @param appstoreEndPoint app store end point
     * @param appId            app ID
     * @param packageId        app package ID
     * @param accessToken      access token
     * @return app package info
     */
    public AppPackageInfoDto getAppPackageInfoFromAppStore(String appstoreEndPoint, String appId,
                                                           String packageId, String accessToken) {
        return apmService.getAppPkgInfoFromAppStore(appstoreEndPoint, appId, packageId, accessToken);
    }

    /**
     * Adds application package info.
     *
     * @param appstoreIp appstore end point
     * @param apps       application package infos
     */
    public void deleteNonExistingPackages(String appstoreIp, List<AppPackageInfoDto> apps) {
        dbService.deleteNonExistingPackages(appstoreIp, apps);
    }

    /**
     * Adds application package info.
     *
     * @param appstoreIp   appstore IP
     * @param appstorePort appstore port
     * @param appPkgInfo   application package info
     */
    public void addAppSyncPackageInfoDB(String appstoreIp, String appstorePort, AppPackageInfoDto appPkgInfo) {
        dbService.addAppSyncPackageInfoDB(appstoreIp, appstorePort, appPkgInfo);
    }

    /**
     * Retrieves all application package info.
     *
     * @return list of application package info
     */
    public List<AppPackageInfo> getAppPackageInfoDB() {
        return dbService.getAppPackageSyncInfo();
    }

    /**
     * Retrieve application package info.
     *
     * @param id ID
     * @return application package info
     */
    public AppPackageInfo getAppPackageInfoDB(String id) {
        return dbService.getAppPackageSyncInfo(id);
    }

    /**
     * Returns true if package info exist in DB.
     *
     * @param id ID
     * @return true if available, otherwise false
     */
    public boolean isAppPackageInfoExistInDB(String id) {
        return dbService.isAppPackageSyncInfoExistInDb(id);
    }

    /**
     * Updates Db and distributes docker application image to host.
     *
     * @param accessToken access token
     * @param syncInfos   sync appPackage details
     */
    @Async
    public void syncApplicationPackages(String accessToken, AppPackageSyncInfo syncInfos) {
        List<PkgSyncInfo> pkgInfos = syncInfos.getSyncInfo();
        for (PkgSyncInfo syncInfo : pkgInfos) {
            syncInfo.setRepoInfo(syncInfos.getRepoInfo());
            syncAppPkgFromAppstoreToMecmRepo(accessToken, syncInfo);
        }
    }

    private void syncAppPkgFromAppstoreToMecmRepo(String accessToken, PkgSyncInfo syncInfo) {

        String host = syncInfo.getAppstoreIp() + ":" + syncInfo.getAppstorePort();
        String appPackageId = syncInfo.getAppId() + syncInfo.getPackageId();

        String appPkgPath = new StringBuilder(getProtocol(isSslEnabled)).append(host).append("/mec/appstore/v1/apps/")
                .append(syncInfo.getAppId()).append(PACKAGES_URL)
                .append(syncInfo.getPackageId()).append("/action/download").toString();

        Set<String> uploadedImgs = new HashSet<>();
        Set<String> downloadedImgs = new HashSet<>();
        boolean isDockerImgAvailable = false;
        List<SwImageDescr> imageInfoList;
        String dockerImgPath;
        InputStream stream;
        try {
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                    Constants.APP_SYNC_INPROGRESS, "");

            stream = apmService.downloadAppPackage(appPkgPath, syncInfo.getPackageId(), accessToken);
            String localFilePath = saveInputStreamToFile(stream, appPackageId, null, localDirPath);

            imageInfoList = apmService.getAppImageInfo(null, localFilePath, appPackageId);
            String appDeployType = apmService.getAppPackageDeploymentType(null, appPackageId);

            if ("vm".equalsIgnoreCase(appDeployType)) {
                dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                        Constants.APP_IN_SYNC, Constants.SUCCESS);
                return;
            }

            if (Boolean.parseBoolean(uploadDockerImage)) {
                for (SwImageDescr imageDescr : imageInfoList) {
                    if (isDockerImageAvailableInPkg(imageDescr.getSwImage())) {
                        isDockerImgAvailable = true;
                        break;
                    }
                }

                if (isDockerImgAvailable) {
                    LOGGER.info("application package contains docker images...");
                    dockerImgPath = apmService.unzipDockerImages(appPackageId, null);
                    apmService.loadDockerImages(appPackageId, imageInfoList, downloadedImgs);

                    FileUtils.deleteQuietly(new File(dockerImgPath + ".zip"));
                    FileUtils.deleteQuietly(new File(dockerImgPath));

                } else {
                    LOGGER.info("application package has image repo info to download...");
                    apmService.downloadAppImage(syncInfo, imageInfoList, downloadedImgs);
                }
                apmService.uploadAppImage(imageInfoList, uploadedImgs);
            }

            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                    Constants.APP_IN_SYNC, Constants.SUCCESS);
        } catch (ApmException | IllegalArgumentException | NoSuchElementException e) {
            LOGGER.error(Constants.SYNC_APP_FAILED, appPackageId);
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(),
                    syncInfo.getPackageId(), Constants.APP_SYNC_FAILED, e.getMessage());
        } finally {
            apmService.deleteAppPkgDockerImages(downloadedImgs);
            apmService.deleteAppPkgDockerImages(uploadedImgs);
            apmService.deleteAppPackageFile(appPkgPath);
        }
    }

    /**
     * resourceInfo.
     */
    public ApmV2Response resourceInfo(String accessToken, String tenantId, String packageId,
                                               String appPkgPath) {
        LOGGER.info("inside resoruce function");

        AppTemplateDto appTemplateDto = new AppTemplateDto();

        try {
            InputStream stream = apmService.downloadAppPackage(appPkgPath, packageId, accessToken);
            String localFilePath = saveInputStreamToFile(stream, packageId, tenantId, localDirPath);
            LOGGER.info("localPath: {}", localFilePath);


            //unzip app package
            String intendedDir = apmService.getLocalIntendedDir(packageId, tenantId);
            LOGGER.info("intendeDir: {}", intendedDir);
            CompressUtility.unzipApplicationPacakge(localFilePath, intendedDir);

            ResourceInfo resourceInfo = apmService.getVduComputeInfo(tenantId, appPkgPath, packageId, appTemplateDto,
                    false);
            LOGGER.info("resourceInfo: {}", resourceInfo);


            String appDeployType = apmService.getAppPackageDeploymentType(tenantId, packageId);

            List<EdgeResourceInfo> queryKpiList = Collections.EMPTY_LIST;

            if ("container".equalsIgnoreCase(appDeployType)) {
                LOGGER.info("container based not yet there");
                throw new ApmException(Constants.MAX_LIMIT_REACHED_ERROR);
            } else if ("vm".equalsIgnoreCase(appDeployType)) {
                Map<String, String> queryKpi = apmSyncHandler.queryKpi(tenantId, accessToken, "openstack");
                queryKpiList = getResourceUsedInfo(queryKpi, resourceInfo);
            }

            return new ApmV2Response(queryKpiList, HttpStatus.OK.value(), "success");

        } catch (ApmException ex) {
            LOGGER.error(DISTRIBUTION_FAILED, ex.getMessage());
            return new ApmV2Response(null, HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        }
    }

    /**
     * getResourceUsedInfo.
     */
    public static List<EdgeResourceInfo> getResourceUsedInfo(Map<String, String> queryKpi, ResourceInfo resourceInfo) {
        List<EdgeResourceInfo> edgeDetails = new LinkedList<>();
        EdgeResourceInfo edgeResourceInfo;
        Resource resource;
        Cpu cpu;
        Mem mem;
        Disk disk;

        for (Map.Entry map : queryKpi.entrySet()) {

            edgeResourceInfo = new EdgeResourceInfo();
            resource = new Resource();

            edgeResourceInfo.setEdge(map.getKey().toString());

            JsonObject jsonObject = new JsonParser().parse(map.getValue().toString()).getAsJsonObject();
            for (String keys : jsonObject.keySet()) {

                if (keys.equals("virtual_cpu_total")) {
                    int cpuRemain = jsonObject.get("virtual_cpu_total").getAsInt()
                            - jsonObject.get("virtual_cpu_used").getAsInt();
                    if (cpuRemain > resourceInfo.getNumVirtualCpu()) {
                        cpu = new Cpu(jsonObject.get("virtual_cpu_used").toString(), jsonObject
                                .get("virtual_cpu_total").toString(), cpuRemain,
                                resourceInfo.getNumVirtualCpu());
                    } else {
                        cpu = new Cpu(jsonObject.get("virtual_cpu_used").toString(),
                                jsonObject.get("virtual_cpu_total").toString(), cpuRemain,
                                resourceInfo.getNumVirtualCpu());
                    }
                    resource.setCpu(cpu);
                }
                if (keys.equals("virtual_local_storage_total")) {
                    int localStorageRemain = jsonObject.get("virtual_local_storage_total").getAsInt()
                            - jsonObject.get("virtual_local_storage_used").getAsInt();
                    if (localStorageRemain > resourceInfo.getSizeOfStorage()) {
                        disk = new Disk(jsonObject.get("virtual_local_storage_used").toString(),
                                jsonObject.get("virtual_local_storage_total").toString(), localStorageRemain,
                                resourceInfo.getSizeOfStorage());
                    } else {
                        disk = new Disk(jsonObject.get("virtual_local_storage_used").toString(),
                                jsonObject.get("virtual_local_storage_total").toString(), localStorageRemain,
                                resourceInfo.getSizeOfStorage());
                    }
                    resource.setDisk(disk);
                }
                if (keys.equals("virtual_mem_total")) {
                    int memRemain = jsonObject.get("virtual_mem_total").getAsInt()
                            - jsonObject.get("virtual_mem_used").getAsInt();
                    if (memRemain > resourceInfo.getVirtualMemSize()) {
                        mem = new Mem(jsonObject.get("virtual_mem_used").toString(),
                                jsonObject.get("virtual_mem_total").toString(), memRemain,
                                resourceInfo.getVirtualMemSize());
                    } else {
                        mem = new Mem(jsonObject.get("virtual_mem_used").toString(),
                                jsonObject.get("virtual_mem_total").toString(), memRemain,
                                resourceInfo.getVirtualMemSize());
                    }
                    resource.setMem(mem);
                }
            }
            edgeResourceInfo.setResource(resource);
            edgeDetails.add(edgeResourceInfo);
        }
        LOGGER.info("edgeDetails: {}", edgeDetails);
        return edgeDetails;
    }

    /**
     * getResourceTemplateInfo.
     */
    public ApmV2Response getResourceTemplateInfo(String accessToken, String tenantId, String packageId,
                                        String appPkgPath) {
        LOGGER.info("inside getResourceTemplateInfo");
        LOGGER.info("apppkgPath: {}", appPkgPath);

        String localFilePath = ApmServiceHelper.getCsarPath(packageId, tenantId, appPkgPath);

        try {
            LOGGER.info("localFilePath: {}", localFilePath);
            //unzip app package
            String intendedDir = apmService.getLocalIntendedDir(packageId, tenantId);
            CompressUtility.unzipApplicationPacakge(localFilePath, intendedDir);

            AppTemplateDto appTemplateDto = apmService.getVduComputeTemplateInfo(tenantId, appPkgPath, packageId);
            appTemplateDto.setAppPackageId(packageId);

            Set<AppTemplateInputAttrDto> sortMap = new TreeSet<>(
                (o1, o2) -> o1.getName().compareTo(o2.getName()));
            sortMap.addAll(appTemplateDto.getInputs());
            appTemplateDto.setInputs(sortMap);

            return new ApmV2Response(appTemplateDto, HttpStatus.OK.value(), "success");
        } catch (ApmException ex) {
            LOGGER.error("failed to resource template from the package", ex.getMessage());
            return new ApmV2Response(null, HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        }
    }

    /**
     * updateResourceTemplateInfo.
     */
    public ApmV2Response updateResourceTemplateInfo(String accessToken, String tenantId, AppTemplateDto appTemplateDto,
                                                   String appPkgPath) {

        try {
            String localFilePath = ApmServiceHelper.getCsarPath(appTemplateDto.getAppPackageId(), tenantId,
                    localDirPath);

            ResourceInfo resourceInfo = apmService.getVduComputeInfo(tenantId, localFilePath,
                    appTemplateDto.getAppPackageId(), appTemplateDto, true);

            Map<String, String> queryKpi = apmSyncHandler.queryKpi(tenantId, accessToken, "openstack");
            List<EdgeResourceInfo> queryKpiList = getResourceUsedInfo(queryKpi, resourceInfo);

            return new ApmV2Response(queryKpiList, HttpStatus.OK.value(), "success");

        } catch (ApmException ex) {
            LOGGER.error("failed to customize resources", ex.getMessage());
            return new ApmV2Response(null, HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        }
    }

}
