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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import java.io.InputStream;
import java.util.List;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.ImageInfo;
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

    @Value("${apm.edge-repo-password}")
    private String edgeRepoPassword;

    @Value("${apm.edge-repo-username:}")
    private String edgeRepoUsername;

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
            dbService.createAppPackage(tenantId, appPackageDto);
            dbService.createHost(tenantId, appPackageDto);
        } catch (ApmException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        String packageId = appPackageDto.getAppPkgId();
        List<ImageInfo> imageInfoList;
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

        for (MecHostDto host : appPackageDto.getMecHostInfo()) {
            String distributionStatus = "Distributed";
            String error = "";
            if (pushImage) {
                try {
                    String repo = apmService.getRepoInfoOfHost(host.getHostIp(), tenantId, accessToken);
                    downloadAppImage(repo, imageInfoList, tenantId, packageId, host.getHostIp());
                }  catch (ApmException e) {
                    distributionStatus = ERROR;
                    error = e.getMessage();
                    LOGGER.error(DISTRIBUTION_IN_HOST_FAILED, packageId, host.getHostIp());
                }
            }
            dbService.updateDistributionStatusOfHost(tenantId, packageId, host.getHostIp(), distributionStatus, error);
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

    /**
     * Downloads app image from repo.
     *
     * @param repositoryInfo edge repository info
     * @param imageInfoList list of images
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param hostIp host IP
     */
    public void downloadAppImage(String repositoryInfo, List<ImageInfo> imageInfoList,
                                 String tenantId, String packageId, String hostIp) {
        for (ImageInfo image : imageInfoList) {
            DockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    .withRegistryUsername(edgeRepoUsername)
                    .withRegistryPassword(edgeRepoPassword)
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

            String imageName = new StringBuilder(repositoryInfo)
                    .append("/").append(image.getName()).append(":")
                    .append(image.getVersion()).toString();
            LOGGER.info("image name to download {} ", imageName);

            PullImageResultCallback resultCallback = new PullImageResultCallback() {
                @Override
                public void onError(Throwable throwable) {
                    super.onError(throwable);
                    LOGGER.error("failed to pull image {}, {}", imageName, throwable.getMessage());
                    dbService.updateDistributionStatusOfHost(tenantId, packageId, hostIp, ERROR,
                            "failed to pull image from edge repo");
                }
            };

            try {
                dockerClient.pullImageCmd(imageName)
                        .exec(resultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApmException("failed to download image");
            } catch (NotFoundException e) {
                LOGGER.error("failed to download image {}, image not found in repository, {}", imageName,
                        e.getMessage());
            } catch (InternalServerErrorException e) {
                LOGGER.error("internal server error while downloading image {},{}", imageName, e.getMessage());
            }
        }
    }
}
