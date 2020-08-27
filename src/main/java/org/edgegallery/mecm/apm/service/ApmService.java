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
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.saveInputStreamToFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.ImageInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service("ApmService")
public class ApmService {

    @Value("${apm.esr-ip}")
    private String esrIp;

    @Value("${apm.esr-port}")
    private String esrPort;

    /**
     * Downloads app package csar from app store and stores it locally.
     *
     * @param appPkgPath app package path
     * @param packageId  package ID
     * @param tenantId   tenant ID
     */
    public String downloadAppPackage(String appPkgPath, String packageId, String tenantId) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<InputStreamResource> response = restTemplate
                .getForEntity(appPkgPath, InputStreamResource.class);
        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            throw new ApmException("failed to download app package from app store");
        }
        return saveInputStreamToFile(response.getBody(), packageId, tenantId);
    }

    /**
     * Returns list of image info.
     *
     * @param localFilePath csar file path
     * @return list of image info
     */
    public List<ImageInfo> getAppImageInfo(String localFilePath) {
        String yaml = getMainServiceYaml(localFilePath);
        return getImageInfo(yaml);
    }

    /**
     * Returns edge repository address.
     *
     * @param hostIp host ip
     * @param tenantId tenant ID
     * @return edge repository address
     * @throws ApmException exception if failed to get edge repository details
     */
    public String getRepoInfoOfHost(String hostIp, String tenantId) {
        RestTemplate restTemplate = new RestTemplate();
        String url = new StringBuilder("https://").append(esrIp).append(":")
                .append(esrPort).append("/tenants/").append(tenantId)
                .append("/mechosts/").append(hostIp).toString();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            throw new ApmException("failed to get repository information from host");
        }
        JsonObject jsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
        String edgeRepoIp = jsonObject.get("edgeRepoIp").getAsString();
        String edgeRepoPort = jsonObject.get("edgeRepoPort").getAsString();
        if (edgeRepoIp == null || edgeRepoPort == null) {
            throw new ApmException("edge repository address is null");
        }
        return edgeRepoIp + ":" + edgeRepoPort;
    }

    /**
     * Downloads app image from repo.
     *
     * @param repoAddress edge repository address
     * @param imageInfoList list of images
     */
    public void downloadAppImage(String repoAddress, List<ImageInfo> imageInfoList) {
        // TODO: download image from repo
    }
}
