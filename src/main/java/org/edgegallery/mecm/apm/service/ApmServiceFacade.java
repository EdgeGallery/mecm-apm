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

import java.util.List;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.ImageInfo;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service("ApmServiceFacade")
public class ApmServiceFacade {

    @Autowired
    private ApmService apmService;

    @Autowired
    private DbService dbService;

    /**
     * Updates Db and distributes docker application image to host.
     *
     * @param tenantId tenant ID
     * @param appPackageDto appPackage details
     */
    @Async
    public void onboardApplication(String tenantId, AppPackageDto appPackageDto) {
        dbService.createAppPackage(tenantId, appPackageDto);
        dbService.createHost(tenantId, appPackageDto);

        String packageId = appPackageDto.getAppPkgId();
        List<ImageInfo> imageInfoList;
        try {
            String localFilePath = apmService.downloadAppPackage(appPackageDto.getAppPkgPath(), packageId, tenantId);
            dbService.updateLocalFilePathOfAppPackage(tenantId, packageId, localFilePath);

            imageInfoList = apmService.getAppImageInfo(localFilePath);
        } catch (ApmException e) {
            dbService.updateDistributionStatusOfAllHost(tenantId, packageId, "Error");
            return;
        }

        for (String hostIp : appPackageDto.getMecHost()) {
            String distributionStatus = "Distributed";
            try {
                String repoAddress = apmService.getRepoInfoOfHost(hostIp, tenantId);
                apmService.downloadAppImage(repoAddress, imageInfoList);
            }  catch (ApmException e) {
                distributionStatus = "Error";
            }
            dbService.updateDistributionStatusOfHost(tenantId, packageId, hostIp, distributionStatus);
        }
    }

    /**
     * Returns app package info.
     *
     * @param tenantId tenant ID
     * @param appPackageId app package ID
     * @return app package info
     */
    public AppPackageDto getAppPackageInfo(String tenantId, String appPackageId) {
        return dbService.getAppPackage(tenantId, appPackageId);
    }

    /**
     * Deletes app package.
     *
     * @param tenantId tenant ID
     * @param appPackageId app package ID
     */
    public void deleteAppPackage(String tenantId, String appPackageId) {
        dbService.deleteAppPackage(tenantId, appPackageId);
        dbService.deleteHost(tenantId, appPackageId);
        // TODO: delete local file stored
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
}
