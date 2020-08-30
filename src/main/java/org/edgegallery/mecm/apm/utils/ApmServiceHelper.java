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

package org.edgegallery.mecm.apm.utils;

import static org.edgegallery.mecm.apm.utils.Constants.FAILED_TO_CONVERT_YAML_TO_JSON;
import static org.edgegallery.mecm.apm.utils.Constants.FAILED_TO_CREATE_CSAR;
import static org.edgegallery.mecm.apm.utils.Constants.FAILED_TO_CREATE_DIR;
import static org.edgegallery.mecm.apm.utils.Constants.FAILED_TO_GET_FAIL_PATH;
import static org.edgegallery.mecm.apm.utils.Constants.FAILED_TO_READ_INPUTSTREAM;
import static org.edgegallery.mecm.apm.utils.Constants.FAILED_TO_UNZIP_CSAR;
import static org.edgegallery.mecm.apm.utils.Constants.SERVICE_YAML_NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.ImageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;

public final class ApmServiceHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApmServiceHelper.class);
    private static final String APM_ROOT = "/home";
    private static final String SLASH = "/";
    static final int TOO_MANY = 1024;
    static final int TOO_BIG = 104857600;

    private ApmServiceHelper() {
    }

    /**
     * Creates directory to save CSAR.
     *
     * @param dirPath directory path to be created
     * @return directory's canonical path
     */
    public static String createDir(String dirPath) {
        File localFileDir = new File(dirPath);
        if (!localFileDir.mkdir()) {
            LOGGER.info(FAILED_TO_CREATE_DIR);
            throw new ApmException(FAILED_TO_CREATE_DIR);
        }

        try {
            return localFileDir.getCanonicalPath();
        } catch (IOException e) {
            LOGGER.info(FAILED_TO_GET_FAIL_PATH, e.getMessage());
            throw new ApmException(FAILED_TO_GET_FAIL_PATH);
        }
    }

    /**
     * Saves input stream to file.
     *
     * @param resourceStream input resource stream
     * @param packageId package ID
     * @param tenantId tenant ID
     * @return file path
     */
    public static String saveInputStreamToFile(InputStreamResource resourceStream, String packageId, String tenantId) {
        if (resourceStream == null) {
            LOGGER.info(FAILED_TO_READ_INPUTSTREAM, packageId);
            throw new ApmException("failed to read input stream from app store for package " + packageId);
        }
        String localDirPath = createDir(APM_ROOT + SLASH + packageId + tenantId);
        String localFilePath = localDirPath + SLASH + packageId;
        File file = new File(localFilePath);
        try {
            FileUtils.copyInputStreamToFile(resourceStream.getInputStream(), file);
            FileChecker.check(file);
            return file.getCanonicalPath();
        } catch (IOException e) {
            LOGGER.error(FAILED_TO_CREATE_CSAR, packageId, e.getMessage());
            throw new ApmException("failed to create csar file for package " + packageId);
        }
    }

    /**
     * Returns main service template content in string format.
     *
     * @param localFilePath CSAR file path
     * @return main service template content in string
     */
    public static String getMainServiceYaml(String localFilePath) {
        ZipEntry mainServiceYaml = null;
        try (ZipFile zipFile = new ZipFile(localFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int entriesCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entriesCount++;
                if (!entry.isDirectory() && entry.getName().contains("/Definitions/MainServiceTemplate.yaml")) {
                    mainServiceYaml = entry;
                    break;
                }
                if (entriesCount > TOO_MANY) {
                    throw new IllegalStateException("too many files to unzip");
                }
            }

            if (mainServiceYaml == null) {
                LOGGER.error(SERVICE_YAML_NOT_FOUND);
                throw new ApmException(SERVICE_YAML_NOT_FOUND);
            }

            try (InputStream inputStream = zipFile.getInputStream(mainServiceYaml)) {
                byte[] byteArray = IOUtils.toByteArray(inputStream);
                if (byteArray.length > TOO_BIG) {
                    throw new IllegalStateException("file being unzipped is too big");
                }
                return new String(byteArray, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error(FAILED_TO_UNZIP_CSAR, e.getMessage());
            throw new ApmException(FAILED_TO_UNZIP_CSAR);
        }
    }

    /**
     * Returns list of image details.
     *
     * @param mainServiceYaml main service template file content
     * @return list of image details
     */
    public static List<ImageInfo> getImageInfo(String mainServiceYaml) {
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        ObjectMapper jsonWriter = new ObjectMapper();
        String response = null;
        try {
            response = jsonWriter.writeValueAsString(om.readValue(mainServiceYaml, Object.class));
        } catch (JsonProcessingException e) {
            LOGGER.error(FAILED_TO_CONVERT_YAML_TO_JSON, e.getMessage());
            throw new ApmException(FAILED_TO_CONVERT_YAML_TO_JSON);
        }

        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        JsonObject topologyTemplate = jsonObject.get("topology_template").getAsJsonObject();
        JsonObject nodeTemplates = topologyTemplate.get("node_templates").getAsJsonObject();

        Set<Entry<String, JsonElement>> entrySet = nodeTemplates.entrySet();
        List<JsonObject> vdus = new LinkedList<>();
        for (Map.Entry<String, JsonElement> entry : entrySet) {
            String type = entry.getValue().getAsJsonObject().get("type").getAsString();
            if ("tosca.nodes.nfv.Vdu.Compute".equals(type)) {
                vdus.add(entry.getValue().getAsJsonObject());
            }
        }

        List<ImageInfo> imageInfoList = new LinkedList<>();
        for (JsonObject vdu : vdus) {
            String imageName = vdu.get("properties").getAsJsonObject().get("image").getAsString();
            String imageVersion = vdu.get("properties").getAsJsonObject().get("image_version").getAsString();
            ImageInfo imageInfo = new ImageInfo(imageName, imageVersion);
            imageInfoList.add(imageInfo);
        }
        return imageInfoList;
    }
}
