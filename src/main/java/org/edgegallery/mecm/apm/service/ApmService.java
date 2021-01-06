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

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getImageInfo;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getMainServiceYaml;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.isRegexMatched;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service("ApmService")
public class ApmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApmService.class);

    @Value("${apm.inventory-endpoint}")
    private String inventoryIp;

    @Value("${apm.inventory-port}")
    private String inventoryPort;

    @Value("${apm.edge-repo-password:}")
    private String edgeRepoPassword;

    @Value("${apm.edge-repo-username:}")
    private String edgeRepoUsername;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

    /**
     * Downloads app package csar from app store and stores it locally.
     *
     * @param appPkgPath app package path
     * @param packageId  package ID
     * @param accessToken access token
     * @return downloaded input stream
     */
    public InputStream downloadAppPackage(String appPkgPath, String packageId, String accessToken) {
        ResponseEntity<Resource> response;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("access_token", accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(appPkgPath, HttpMethod.GET, entity, Resource.class);
        } catch (ResourceAccessException ex) {
            LOGGER.error(Constants.FAILED_TO_CONNECT_APPSTORE);
            throw new ApmException(Constants.FAILED_TO_CONNECT_APPSTORE);
        } catch (HttpClientErrorException ex) {
            LOGGER.error("client error while downloading app package {}", ex.getMessage());
            throw new ApmException(Constants.ERROR_IN_DOWNLOADING_CSAR);
        } catch (HttpServerErrorException ex) {
            LOGGER.error("server error while downloading app package {}", ex.getMessage());
            throw new ApmException(Constants.ERROR_IN_DOWNLOADING_CSAR);
        }

        Resource responseBody = response.getBody();
        if (!HttpStatus.OK.equals(response.getStatusCode()) || responseBody == null) {
            LOGGER.error(Constants.CSAR_DOWNLOAD_FAILED, packageId);
            throw new ApmException("failed to download app package for package " + packageId);
        }

        try {
            return responseBody.getInputStream();
        } catch (IOException e) {
            LOGGER.error(Constants.GET_INPUTSTREAM_FAILED, packageId);
            throw new ApmException("failed to get input stream from app store response for package " + packageId);
        }
    }

    /**
     * Returns list of image info.
     *
     * @param localFilePath csar file path
     * @param packageId package Id
     * @param tenantId tenant Id
     * @return list of image info
     */
    public List<String> getAppImageInfo(String localFilePath, String packageId, String tenantId) {
        String yaml = getMainServiceYaml(localFilePath, getLocalIntendedDir(packageId, tenantId));
        return getImageInfo(yaml);
    }

    /**
     * Returns local intended dir path.
     *
     * @param packageId package id
     * @param tenantId tenantId
     * @return returns local intended dir path
     */
    private String getLocalIntendedDir(String packageId, String tenantId) {
        return localDirPath + File.separator + packageId + tenantId;
    }

    /**
     * Returns edge repository address.
     *
     * @param hostIp host ip
     * @param tenantId tenant ID
     * @param accessToken access token
     * @return returns edge repository info
     * @throws ApmException exception if failed to get edge repository details
     */
    public String getRepoInfoOfHost(String hostIp, String tenantId, String accessToken) {
        String url = new StringBuilder("https://").append(inventoryIp).append(":")
                .append(inventoryPort).append("/inventory/v1/tenants/").append(tenantId)
                .append("/mechosts/").append(hostIp).toString();

        ResponseEntity<String> response;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("access_token", accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (ResourceAccessException ex) {
            LOGGER.error(Constants.FAILED_TO_CONNECT_INVENTORY, ex.getMessage());
            throw new ApmException(Constants.FAILED_TO_CONNECT_INVENTORY);
        } catch (HttpClientErrorException ex) {
            LOGGER.error(Constants.ERROR_FROM_INVENTORY, hostIp, ex.getMessage());
            throw new ApmException("error while fetching host record from inventory");
        }

        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            LOGGER.error(Constants.FAILED_TO_GET_REPO_INFO, hostIp);
            throw new ApmException("failed to get repository information of host " + hostIp);
        }

        JsonObject jsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
        JsonElement edgeRepoIp = jsonObject.get("edgerepoIp");
        JsonElement edgeRepoPort = jsonObject.get("edgerepoPort");
        if (edgeRepoIp == null || edgeRepoPort == null) {
            LOGGER.error(Constants.REPO_INFO_NULL, hostIp);
            throw new ApmException("edge repository information is null for host " + hostIp);
        }

        String ip = edgeRepoIp.getAsString();
        if (!isRegexMatched(Constants.IP_REGEX, ip)) {
            LOGGER.error(Constants.REPO_IP_INVALID, hostIp);
            throw new ApmException("edge repo ip is invalid for host " + hostIp);
        }

        String port = edgeRepoPort.getAsString();
        if (!isRegexMatched(Constants.PORT_REGEX, port)) {
            LOGGER.error(Constants.REPO_PORT_INVALID, hostIp);
            throw new ApmException("edge repo port is invalid for host " + hostIp);
        }

        return edgeRepoIp.getAsString() + ":" + edgeRepoPort.getAsString();
    }

    /**
     * Returns app package csar file.
     *
     * @param localFilePath local file path
     * @return app package csar file
     */
    public InputStream getAppPackageFile(String localFilePath) {
        try {
            return new BufferedInputStream(new FileInputStream(localFilePath));
        } catch (FileNotFoundException e) {
            LOGGER.error(Constants.CSAR_NOT_EXIST);
            throw new ApmException(Constants.CSAR_NOT_EXIST);
        }
    }

    /**
     * Returns app package csar file.
     *
     * @param localFilePath local file path
     */
    public void deleteAppPackageFile(String localFilePath) {
        if (localFilePath == null) {
            LOGGER.error(Constants.LOCAL_FILE_PATH_NULL);
            throw new ApmException(Constants.LOCAL_FILE_PATH_NULL);
        }
        try {
            Files.deleteIfExists(Paths.get(localFilePath));
        } catch (IOException e) {
            LOGGER.error("failed to delete csar file");
        }
    }

    /**
     * Downloads app image from repo.
     *
     * @param repositoryInfo edge repository info
     * @param imageInfoList list of images
     */
    public void downloadAppImage(String repositoryInfo, List<String> imageInfoList) {
        for (String image : imageInfoList) {
            DockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    .withRegistryUsername(edgeRepoUsername)
                    .withRegistryPassword(edgeRepoPassword)
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

            String imageName = new StringBuilder(repositoryInfo)
                    .append("/").append(image).toString();
            LOGGER.info("image name to download {} ", imageName);

            try {
                dockerClient.pullImageCmd(imageName)
                        .exec(new PullImageResultCallback()).awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApmException("failed to download image");
            } catch (NotFoundException e) {
                LOGGER.error("failed to download image {}, image not found in repository, {}", imageName,
                        e.getMessage());
                throw new ApmException("failed to push image to edge repo");
            } catch (InternalServerErrorException e) {
                LOGGER.error("internal server error while downloading image {},{}", imageName, e.getMessage());
                throw new ApmException("failed to push image to edge repo");
            }
        }
        LOGGER.info("images to edge repo downloaded successfully");
    }
}
