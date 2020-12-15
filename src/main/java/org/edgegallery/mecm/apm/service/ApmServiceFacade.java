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

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.saveInputStreamToFile;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.DISTRIBUTION_IN_HOST_FAILED;
import static org.edgegallery.mecm.apm.utils.Constants.ERROR;

import java.io.InputStream;
import java.util.List;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service("ApmServiceFacade")
public class ApmServiceFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApmServiceFacade.class);

    @Autowired
    private ApmService apmService;

    @Autowired
    private DbService dbService;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

    @Value("${apm.push-image}")
    private boolean pushImage;

    /**
     * Updates Db and distributes docker application image to host.
     *
     * @param accessToken access token
     * @param tenantId tenant ID
     * @param appPackageDto appPackage details
     */
    @Async
    public void onboardApplication(String accessToken, String tenantId, AppPackageDto appPackageDto) {
        try {
            createAppPackageEntryInDb(tenantId, appPackageDto);
        } catch (ApmException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        String packageId = appPackageDto.getAppPkgId();
        List<String> imageInfoList;
        try {
            InputStream stream = apmService.downloadAppPackage(appPackageDto.getAppPkgPath(), packageId,
                    accessToken);
            String localFilePath = saveInputStreamToFile(stream, packageId, tenantId, localDirPath);
            dbService.updateLocalFilePathOfAppPackage(tenantId, packageId, localFilePath);

            imageInfoList = apmService.getAppImageInfo(localFilePath);
        } catch (ApmException | IllegalArgumentException e) {
            LOGGER.error(DISTRIBUTION_FAILED, packageId);
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, e.getMessage());
            return;
        }

        distributeApplication(tenantId, appPackageDto, accessToken, imageInfoList);
    }

    /**
     * Updates Db and distributes docker application image to host.
     *
     * @param accessToken access token
     * @param tenantId tenant ID
     * @param appPackageDto appPackage details
     * @param localFilePath local package path
     */
    @Async
    public void onboardApplication(String accessToken, String tenantId, AppPackageDto appPackageDto,
                                   String localFilePath) {
        try {
            createAppPackageEntryInDb(tenantId, appPackageDto);
        } catch (ApmException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        String packageId = appPackageDto.getAppPkgId();
        List<String> imageInfoList;
        try {
            dbService.updateLocalFilePathOfAppPackage(tenantId, packageId, localFilePath);
            imageInfoList = apmService.getAppImageInfo(localFilePath);
        } catch (ApmException | IllegalArgumentException e) {
            LOGGER.error(DISTRIBUTION_FAILED, e);
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, ERROR, e.getMessage());
            return;
        }

        distributeApplication(tenantId, appPackageDto, accessToken, imageInfoList);
    }

    /**
     * Returns app package info.
     *
     * @param tenantId tenant ID
     * @param appPackageId app package ID
     * @return app package info
     */
    public AppPackageDto getAppPackageInfo(String tenantId, String appPackageId) {
        return dbService.getAppPackageWithHost(tenantId, appPackageId);
    }

    /**
     * Deletes app package.
     *
     * @param tenantId tenant ID
     * @param appPackageId app package ID
     */
    public void deleteAppPackage(String tenantId, String appPackageId) {
        AppPackage appPackage = dbService.getAppPackage(tenantId, appPackageId);
        dbService.deleteAppPackage(tenantId, appPackageId);
        dbService.deleteHost(tenantId, appPackageId);
        apmService.deleteAppPackageFile(appPackage.getLocalFilePath());
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
     * @param tenantId tenant ID
     * @param appPackageId app package ID
     * @param hostIp host Ip
     */
    public void deleteAppPackageInHost(String tenantId, String appPackageId,
                                       String hostIp) {
        dbService.deleteHostWithIp(tenantId, appPackageId, hostIp);
    }

    /**
     * Returns app package csar file.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @return app package csar file
     */
    public InputStream getAppPackageFile(String tenantId, String packageId) {
        AppPackage appPackage = dbService.getAppPackage(tenantId, packageId);
        return apmService.getAppPackageFile(appPackage.getLocalFilePath());
    }

    private void createAppPackageEntryInDb(String tenantId, AppPackageDto appPackageDto) {
        dbService.createAppPackage(tenantId, appPackageDto);
        dbService.createHost(tenantId, appPackageDto);
    }

    private void distributeApplication(String tenantId, AppPackageDto appPackageDto, String accessToken,
                                       List<String> imageInfoList) {
        String packageId = appPackageDto.getAppPkgId();
        for (MecHostDto host : appPackageDto.getMecHostInfo()) {
            String distributionStatus = "Distributed";
            String error = "";
            if (pushImage) {
                try {
                    String repo = apmService.getRepoInfoOfHost(host.getHostIp(), tenantId, accessToken);
                    apmService.downloadAppImage(repo, imageInfoList);
                }  catch (ApmException e) {
                    distributionStatus = ERROR;
                    error = e.getMessage();
                    LOGGER.error(DISTRIBUTION_IN_HOST_FAILED, packageId, host.getHostIp());
                }
            }
            dbService.updateDistributionStatusOfHost(tenantId, packageId, host.getHostIp(), distributionStatus, error);
        }
    }
}
