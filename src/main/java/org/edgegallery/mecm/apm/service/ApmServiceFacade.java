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
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.saveInputStreamToFile;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_IN_HOST_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.ERROR;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackageInfo;
import org.edgegallery.mecm.apm.model.AppPackageSyncInfo;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.AppStore;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Getter
@Setter
@Service("ApmServiceFacade")
public class ApmServiceFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApmServiceFacade.class);

    @Autowired
    private ApmService apmService;

    @Autowired
    private DbService dbService;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

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
        boolean isPkgInSync = false;
        boolean isPkgExist = false;
        try {
            AppPackageInfo appPkinfoDb = dbService.getAppPackageSyncInfo(packageId);
            if ("SYNC".equals(appPkinfoDb.getSyncStatus())) {
                isPkgInSync = true;
            }
        } catch (IllegalArgumentException ex) {
            LOGGER.info("application package not synchronized...");
        }

        File packageFile = new File(localDirPath + File.separator + packageId);
        if (packageFile.exists()) {
            isPkgExist = true;
        }

        if (!isPkgInSync || !isPkgExist) {
            try {
                InputStream stream = apmService.downloadAppPackage(appPackageDto.getAppPkgPath(),
                        packageId, accessToken);
                String localFilePath = saveInputStreamToFile(stream, packageId, null, localDirPath);
                imageInfoList = apmService.getAppImageInfo(localFilePath, packageId);

                apmService.updateAppPackageWithRepoInfo(packageId, true);
            } catch (ApmException | IllegalArgumentException e) {
                LOGGER.error(DISTRIBUTION_FAILED, packageId);
                dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, e.getMessage());
                return;
            }
        }

        distributeApplication(tenantId, appPackageDto, imageInfoList, syncAppPkg, false);

        if (!isPkgExist) {
            apmService.compressAppPackage(packageId);
        }

        if (!isPkgInSync) {
            addAppSyncInfoDb(appPackageDto, syncAppPkg, Constants.SUCCESS);
        }
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
        String packageId = appPackageDto.getAppPkgId();
        List<SwImageDescr> imageInfoList;
        String dockerImgspath = null;
        boolean isDockerImgTarPresent = false;

        try {
            imageInfoList = apmService.getAppImageInfo(localFilePath, packageId);
            for (SwImageDescr imageDescr : imageInfoList) {
                if (imageDescr.getSwImage().contains("tar") || imageDescr.getSwImage().contains("tar.gz")
                        || imageDescr.getSwImage().contains(".tgz")) {
                    isDockerImgTarPresent = true;
                }
            }

            if (isDockerImgTarPresent) {
                dockerImgspath = apmService.unzipDockerImages(appPackageDto.getAppPkgId());
                apmService.loadDockerImages(packageId, imageInfoList);
                apmService.updateAppPackageWithRepoInfo(packageId, false);
            } else {
                apmService.updateAppPackageWithRepoInfo(packageId, true);
            }
        } catch (ApmException | IllegalArgumentException ex) {
            LOGGER.error(DISTRIBUTION_FAILED, ex.getMessage());
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, ex.getMessage());
            return;
        }

        if (dockerImgspath != null) {
            try {
                FileUtils.forceDelete(new File(dockerImgspath));
            } catch (IOException ex) {
                LOGGER.debug("failed to delete docker images {}", ex.getMessage());
            }
        }

        distributeApplication(tenantId, appPackageDto, imageInfoList, syncAppPkg, isDockerImgTarPresent);
        apmService.compressAppPackage(packageId);

        addAppSyncInfoDb(appPackageDto, syncAppPkg, Constants.SUCCESS);
    }

    private void addAppSyncInfoDb(AppPackageDto appPackageDto, PkgSyncInfo syncInfo, String operationalInfo) {
        AppPackageInfo pkgInfo = new AppPackageInfo();
        pkgInfo.setAppPkgInfoId(appPackageDto.getAppPkgId());
        pkgInfo.setAppId(appPackageDto.getAppId());
        pkgInfo.setPackageId(syncInfo.getPackageId());
        pkgInfo.setName(appPackageDto.getAppPkgName());
        pkgInfo.setSyncStatus("SYNC");
        pkgInfo.setAppstoreIp(syncInfo.getAppstoreIp());
        pkgInfo.setOperationalInfo(operationalInfo);

        dbService.addAppPackageInfoDB(pkgInfo);
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
     * Deletes app package.
     *
     * @param tenantId     tenant ID
     * @param appPackageId app package ID
     */
    public void deleteAppPackage(String tenantId, String appPackageId) {
        dbService.deleteAppPackage(tenantId, appPackageId);
        dbService.deleteHost(tenantId, appPackageId);
        apmService.deleteAppPackageFile(getLocalFilePath(localDirPath, tenantId, appPackageId));
        dbService.deleteAppPackageSyncInfoDb(appPackageId);
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
        return apmService.getAppPackageFile(getLocalFilePath(localDirPath, tenantId, packageId));
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

    private void distributeApplication(String tenantId, AppPackageDto appPackageDto,
                                       List<SwImageDescr> imageInfoList, PkgSyncInfo syncAppPkg,
                                       boolean skipDownload) {
        String packageId = appPackageDto.getAppPkgId();
        Set<String> downloadedImgs = null;
        Set<String> uploadedImgs = null;

        for (MecHostDto host : appPackageDto.getMecHostInfo()) {
            String distributionStatus = "Distributed";
            String error = "";
            try {
                if (imageInfoList != null) {
                    if (!skipDownload) {
                        downloadedImgs = new HashSet<>();
                        apmService.downloadAppImage(syncAppPkg, imageInfoList, downloadedImgs);
                    }

                    uploadedImgs = new HashSet<>();
                    apmService.uploadAppImage(syncAppPkg, imageInfoList, uploadedImgs);
                }
            } catch (ApmException e) {
                distributionStatus = ERROR;
                error = e.getMessage();
                LOGGER.error(DISTRIBUTION_IN_HOST_FAILED, packageId, host.getHostIp());
            } finally {
                apmService.deleteAppPkgDockerImages(downloadedImgs);
                apmService.deleteAppPkgDockerImages(uploadedImgs);
            }

            dbService.updateDistributionStatusOfHost(tenantId, packageId, host.getHostIp(), distributionStatus, error);
        }
    }

    /**
     * Returns application store configuration.
     *
     * @param tenantId   tenant ID
     * @param appstoreIp appstore IP
     * @return app store configuration
     */
    public AppStore getAppstoreConfig(String tenantId, String appstoreIp, String accessToken) {

        return apmService.getAppStoreCfgFromInventory(appstoreIp, tenantId, accessToken);
    }

    /**
     * Returns application store configuration.
     *
     * @param tenantId tenant ID
     * @return app store configuration
     */
    public List<AppStore> getAllAppstoreConfig(String tenantId, String accessToken) {

        return apmService.getAppStoreCfgFromInventory(tenantId, accessToken);
    }

    /**
     * Returns application repo configuration.
     *
     * @param tenantId tenant ID
     * @return app store configuration
     */
    public List<AppRepo> getAllAppRepoConfig(String tenantId, String accessToken) {

        return apmService.getAllAppRepoCfgFromInventory(tenantId, accessToken);
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
     * @param tenantId         tenant ID
     * @param appstoreEndPoint appstore end point
     * @return app store configuration
     */
    public List<AppPackageInfoDto> getAppPackagesInfo(String tenantId, String appstoreEndPoint, String accessToken) {

        return apmService.getAppPackagesInfoFromAppStore(appstoreEndPoint, tenantId, accessToken);
    }

    /**
     * Adds application package info.
     *
     * @param appstoreIp appstore end point
     * @param apps       application package infos
     */
    public void updateAppPackageInfoDB(String appstoreIp, List<AppPackageInfoDto> apps) {
        dbService.updateAppPackageInfoDB(appstoreIp, apps);
    }

    /**
     * Retrieves al application package info.
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
        List<SwImageDescr> imageInfoList;
        InputStream stream;
        String host = syncInfo.getAppstoreIp() + ":" + syncInfo.getAppstorePort();
        String appPackageId = syncInfo.getAppId() + syncInfo.getPackageId();

        String appPkgPath = "https://" + host + "/mec/appstore/v1/apps/" + syncInfo.getAppId()
                + "/packages/" + syncInfo.getPackageId() + "/action/download";

        Set<String> uploadedImgs = new HashSet();
        Set<String> downloadedImgs = new HashSet();
        try {
            stream = apmService.downloadAppPackage(appPkgPath, syncInfo.getPackageId(), accessToken);
            String localFilePath = saveInputStreamToFile(stream, appPackageId, null, localDirPath);

            imageInfoList = apmService.getAppImageInfo(localFilePath, appPackageId);

            apmService.downloadAppImage(syncInfo, imageInfoList, downloadedImgs);
            apmService.uploadAppImage(syncInfo, imageInfoList, uploadedImgs);

            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(), syncInfo.getPackageId(),
                    "SYNC", Constants.SUCCESS);
            apmService.updateAppPackageWithRepoInfo(appPackageId, true);
        } catch (ApmException | IllegalArgumentException e) {
            LOGGER.error(Constants.SYNC_APP_FAILED, appPackageId);
            dbService.updateAppPackageSyncStatus(syncInfo.getAppId(),
                    syncInfo.getPackageId(), "SYNC_FAILED", e.getMessage());
        } finally {
            apmService.deleteAppPkgDockerImages(downloadedImgs);
            apmService.deleteAppPkgDockerImages(uploadedImgs);
        }
    }
}
