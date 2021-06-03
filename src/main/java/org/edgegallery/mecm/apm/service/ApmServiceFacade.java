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

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getLocalFilePath;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getPackageDirPath;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.isSuffixExist;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.saveInputStreamToFile;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_IN_HOST_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.ERROR;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
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
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
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
    private static final String LCMCONTROLLER_URL = "/lcmcontroller/v1/tenants/";
    private static final String PACKAGES_URL = "/packages/";
    private static final String HTTPS = "https://";
    private static final String PATH_DELIMITER = "/";

    @Autowired
    private ApmService apmService;

    @Autowired
    private DbService dbService;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${apm.mecm-repo-endpoint:}")
    private String mecmRepoEndpoint;

    @Value("${apm.push-image:}")
    private String uploadDockerImage;

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
        List<SwImageDescr> imageInfoList = null;

        try {
            InputStream stream = apmService.downloadAppPackage(appPackageDto.getAppPkgPath(), packageId, accessToken);
            String localFilePath = saveInputStreamToFile(stream, packageId, tenantId, localDirPath);

            imageInfoList = apmService.getAppImageInfo(tenantId, localFilePath, appPackageDto.getAppPkgId());
            String appDeployType = apmService.getAppPackageDeploymentType(tenantId, appPackageDto.getAppPkgId());

            AppTemplate appTemplate = apmService.getApplicationTemplateInfo(appPackageDto, tenantId);
            dbService.createOrUpdateAppTemplate(tenantId, appTemplate);

            if ("container".equalsIgnoreCase(appDeployType)) {
                onboardContainerBasedAppPkg(accessToken, tenantId, appPackageDto, syncAppPkg, imageInfoList);
                addAppSyncInfoDb(appPackageDto, syncAppPkg, Constants.SUCCESS);
            } else if ("vm".equalsIgnoreCase(appDeployType)) {
                onboardVmBasedAppPkg(accessToken, tenantId, appPackageDto);
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
     */
    @Async
    public void onboardApplication(String accessToken, String tenantId, AppPackageDto appPackageDto,
                                   String localFilePath, PkgSyncInfo syncAppPkg) {

        try {
            List<SwImageDescr> imageInfoList = apmService.getAppImageInfo(tenantId, localFilePath,
                                                                          appPackageDto.getAppPkgId());
            String appDeployType = apmService.getAppPackageDeploymentType(tenantId, appPackageDto.getAppPkgId());

            AppTemplate appTemplate = apmService.getApplicationTemplateInfo(appPackageDto, tenantId);
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

    private void onboardContainerBasedAppPkg(String accessToken, String tenantId, AppPackageDto appPackageDto,
                                             PkgSyncInfo syncAppPkg, List<SwImageDescr> imageInfoList) {
        String packageId = appPackageDto.getAppPkgId();
        String dockerImgspath = null;
        boolean downloadImg = true;
        Set<String> loadedImgs = new HashSet<>();
        try {
            for (SwImageDescr imageDescr : imageInfoList) {
                if (isSuffixExist(imageDescr.getSwImage(), ".tar") || isSuffixExist(imageDescr.getSwImage(), TAR_GZ)
                        || isSuffixExist(imageDescr.getSwImage(), ".tgz")) {
                    downloadImg = false;

                    LOGGER.info("application package contains docker images...");
                    dockerImgspath = apmService.unzipDockerImages(appPackageDto.getAppPkgId(), tenantId);
                    apmService.loadDockerImages(packageId, imageInfoList, loadedImgs);

                    FileUtils.forceDeleteOnExit(new File(dockerImgspath + ".zip"));
                    FileUtils.forceDeleteOnExit(new File(dockerImgspath));

                    break;
                }
            }

            syncDockerImagesFromSrcToMecmRepo(appPackageDto, imageInfoList, syncAppPkg, downloadImg, accessToken);

            apmService.updateAppPackageWithRepoInfo(tenantId, packageId);
            apmService.compressAppPackage(tenantId, packageId);
        } catch (ApmException | IllegalArgumentException ex) {
            LOGGER.error(DISTRIBUTION_FAILED, ex.getMessage());
            apmService.deleteAppPkgDockerImages(loadedImgs);
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, ex.getMessage());
            throw new ApmException(ex.getMessage());
        } catch (IOException e) {
            LOGGER.error("docker images sync failed due to IO exception");
            apmService.deleteAppPkgDockerImages(loadedImgs);
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, e.getMessage());
            throw new ApmException("docker images sync failed due to IO exception");
        }

        distributeApplication(tenantId, appPackageDto, accessToken);
    }

    private void onboardVmBasedAppPkg(String accessToken, String tenantId, AppPackageDto appPackageDto) {

        apmService.compressAppPackage(tenantId, appPackageDto.getAppPkgId());

        distributeApplication(tenantId, appPackageDto, accessToken);

        LOGGER.info("On-boading vm based applicaiton package completed...");
    }

    private void addAppSyncInfoDb(AppPackageDto appPackageDto, PkgSyncInfo syncInfo, String operationalInfo) {
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
        apmService.deleteAppPackageFile(getPackageDirPath(localDirPath, appPackageId, tenantId));
        return hosts;
    }

    /**
     * Deletes distributed application package on host.
     *
     * @param hostIp      host ip
     * @param packageId   package ID
     * @param accessToken access token
     * @throws ApmException exception if failed to get edge repository details
     */
    public void deleteDistributedAppPackageOnHost(String tenantId, String hostIp,
                                                  String packageId, String accessToken) {
        try {
            String mepmEndPoint = apmService.getMepmCfgOfHost(hostIp, accessToken);

            String url = new StringBuilder(Constants.HTTPS_PROTO).append(mepmEndPoint)
                    .append(LCMCONTROLLER_URL).append(tenantId)
                    .append(PACKAGES_URL).append(packageId).append("/hosts/").append(hostIp).toString();

            apmService.sendDeleteRequest(url, accessToken);
        } catch (NoSuchElementException | ApmException ex) {
            LOGGER.info("failed to delete package on host {}", hostIp);
        }
    }

    /**
     * Deletes application package on host.
     *
     * @param hostIp      host ip
     * @param packageId   package ID
     * @param accessToken access token
     * @throws ApmException exception if failed to get edge repository details
     */
    public void deleteDistributedAppPackage(String tenantId, String hostIp, String packageId, String accessToken) {
        try {
            String mepmEndPoint = apmService.getMepmCfgOfHost(hostIp, accessToken);

            String url = new StringBuilder(Constants.HTTPS_PROTO).append(mepmEndPoint)
                    .append(LCMCONTROLLER_URL).append(tenantId)
                    .append(PACKAGES_URL).append(packageId).toString();

            apmService.sendDeleteRequest(url, accessToken);
        } catch (NoSuchElementException | ApmException ex) {
            LOGGER.info("failed to delete package on host {}", hostIp);
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
        return apmService.getAppPackageFile(getLocalFilePath(localDirPath, packageId, tenantId));
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
            imagesInSync = false;
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
                apmService.uploadAppImage(syncAppPkg, imageInfoList, uploadedImgs);
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
            String distributionStatus = "Distributed";
            String error = "";

            try {
                uploadAndDistributeApplicationPackage(accessToken, host.getHostIp(), tenantId,
                        appPackageDto.getAppId(), packageId);

                dbService.updateDistributionStatusOfHost(tenantId, packageId, host.getHostIp(),
                        distributionStatus, error);

                LOGGER.info("Application package {}, on-boading on {} completed...", packageId, host.getHostIp());

            } catch (ApmException e) {
                distributionStatus = ERROR;
                error = e.getMessage();
                LOGGER.error(DISTRIBUTION_IN_HOST_FAILED, packageId, host.getHostIp());
                dbService.updateDistributionStatusOfHost(tenantId, packageId, host.getHostIp(), distributionStatus,
                        error);
                throw new ApmException(e.getMessage());
            }
        }
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
            url = new StringBuilder(Constants.HTTPS_PROTO).append(repo).append(":")
                    .append("443").append("/v2")
                    .append(repository)
                    .append("/manifests/")
                    .append(tag).toString();
        } else {
            url = new StringBuilder(Constants.HTTPS_PROTO).append(repo).append("/v2/")
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
                    throw new ApmException("could not find image name or image version in descriptor");
                }
                String[] imageName = name.split(":");
                String mecmRepository = "/mecm/" + imageName[0];

                checkIfManifestPresentRepo(mecmRepoEndpoint, mecmRepository, tag, accessToken);

                LOGGER.info("image is available in repo, skip download/upload {}", imageInfo.getSwImage());
            } catch (ApmException | NoSuchElementException ex) {
                imagesLstExcImgsInRepo.add(imageInfo);
                LOGGER.info("image is not available in repo, download image: {}", imageInfo.getSwImage());
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
            String mepmEndPoint = apmService.getMepmCfgOfHost(hostIp, accessToken);

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
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(mepmEndPoint)
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
        String url = HTTPS + mepmEndPoint + LCMCONTROLLER_URL + tenantId + "/packages";
        try {
            String packagePath = localDirPath + File.separator + pkgId + tenantId + PATH_DELIMITER + pkgId + ".csar";
            FileSystemResource appPkgRes = new FileSystemResource(new File(packagePath));

            // Preparing request parts.
            LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("package", appPkgRes);
            parts.add("packageId", pkgId);
            parts.add("appId", appId);

            sendRequestWithMultipartFormData(url, parts, accessToken);
        } catch (InvalidPathException e) {
            LOGGER.info("package ID is invalid");
            throw new ApmException("invalid package path");
        } catch (ApmException e) {
            LOGGER.info("failed to upload package  {}", e.getMessage());
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
     * @param appstoreIp appstore IP
     * @return app store configuration
     */
    public AppStore getAppstoreConfig(String appstoreIp, String accessToken) {

        return apmService.getAppStoreCfgFromInventory(appstoreIp, accessToken);
    }

    /**
     * Returns application store configuration.
     *
     * @return app store configuration
     */
    public List<AppStore> getAllAppstoreConfig(String accessToken) {

        return apmService.getAppStoreCfgFromInventory(accessToken);
    }

    /**
     * Returns application repo configuration.
     *
     * @return app store configuration
     */
    public List<AppRepo> getAllAppRepoConfig(String accessToken) {

        return apmService.getAllAppRepoCfgFromInventory(accessToken);
    }

    /**
     * Returns application repo configuration.
     *
     * @param tenantId tenant ID
     * @param host     repo host
     * @return app store configuration
     */
    public AppRepo getAppRepoConfig(String tenantId, String host, String accessToken) {

        return apmService.getAppRepoCfgFromInventory(tenantId, host, accessToken);
    }

    /**
     * Returns application package info.
     *
     * @param appstoreEndPoint appstore end point
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
     */
    public List<AppPackageInfo> getAppPackageInfoDB() {
        return dbService.getAppPackageSyncInfo();
    }

    /**
     * Retrieve application package info.
     *
     * @return application package info
     */
    public AppPackageInfo getAppPackageInfoDB(String id) {
        return dbService.getAppPackageSyncInfo(id);
    }

    /**
     * Returns true if package info exist in DB.
     *
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

        String appPkgPath = HTTPS + host + "/mec/appstore/v1/apps/" + syncInfo.getAppId()
                + PACKAGES_URL + syncInfo.getPackageId() + "/action/download";

        Set<String> uploadedImgs = new HashSet<>();
        Set<String> downloadedImgs = new HashSet<>();
        boolean isDockerImgAvailable = false;
        List<SwImageDescr> imageInfoList;
        String dockerImgPath = null;
        InputStream stream;
        try {
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                    Constants.APP_SYNC_INPROGRESS, "");

            stream = apmService.downloadAppPackage(appPkgPath, syncInfo.getPackageId(), accessToken);
            String localFilePath = saveInputStreamToFile(stream, appPackageId, null, localDirPath);

            imageInfoList = apmService.getAppImageInfo(null, localFilePath, appPackageId);
            String appDeployType = apmService.getAppPackageDeploymentType(null, appPackageId);

            if ("vm".equalsIgnoreCase(appDeployType)) {
                apmService.compressAppPackage(null, appPackageId);
                dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                        Constants.APP_IN_SYNC, Constants.SUCCESS);
                return;
            }

            for (SwImageDescr imageDescr : imageInfoList) {
                if (isSuffixExist(imageDescr.getSwImage(), ".tar") || isSuffixExist(imageDescr.getSwImage(), TAR_GZ)
                        || isSuffixExist(imageDescr.getSwImage(), ".tgz")) {
                    isDockerImgAvailable = true;
                    break;
                }
            }

            if (isDockerImgAvailable) {
                LOGGER.info("application package contains docker images...");
                dockerImgPath = apmService.unzipDockerImages(appPackageId, null);
                apmService.loadDockerImages(appPackageId, imageInfoList, downloadedImgs);

                FileUtils.forceDeleteOnExit(new File(dockerImgPath + ".zip"));
                FileUtils.forceDeleteOnExit(new File(dockerImgPath));

            } else {
                LOGGER.info("application package has image repo info to download...");
                apmService.downloadAppImage(syncInfo, imageInfoList, downloadedImgs);

            }
            apmService.updateAppPackageWithRepoInfo(null, appPackageId);
            apmService.uploadAppImage(syncInfo, imageInfoList, uploadedImgs);

            apmService.updateAppPackageWithRepoInfo(null, appPackageId);
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                    Constants.APP_IN_SYNC, Constants.SUCCESS);
        } catch (ApmException | IllegalArgumentException | NoSuchElementException e) {
            LOGGER.error(Constants.SYNC_APP_FAILED, appPackageId);
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(),
                    syncInfo.getPackageId(), Constants.APP_SYNC_FAILED, e.getMessage());
        } catch (IOException e) {
            LOGGER.debug("file operation failed");
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(),
                    syncInfo.getPackageId(), Constants.APP_SYNC_FAILED, e.getMessage());
        } finally {
            apmService.deleteAppPkgDockerImages(downloadedImgs);
            apmService.deleteAppPkgDockerImages(uploadedImgs);
        }

        apmService.compressAppPackage(null, appPackageId);
    }
}
