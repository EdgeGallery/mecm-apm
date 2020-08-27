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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
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
import org.springframework.core.io.InputStreamResource;

public final class ApmServiceHelper {
    private static final String APM_ROOT = "/home";
    private static final String SLASH = "/";

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
            throw new ApmException("failed to create local directory");
        }

        try {
            return localFileDir.getCanonicalPath();
        } catch (IOException e) {
            throw new ApmException("failed to get local directory path");
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
            throw new ApmException("failed to read input stream from app store");
        }
        String localDirPath = createDir(APM_ROOT + SLASH + packageId + tenantId);
        String localFilePath = localDirPath + SLASH + packageId;
        File file = new File(localFilePath);
        try {
            FileUtils.copyInputStreamToFile(resourceStream.getInputStream(), file);
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new ApmException("failed to create csar file");
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
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().contains("/Definitions/MainServiceTemplate.yaml")) {
                    mainServiceYaml = entry;
                    break;
                }
            }

            if (mainServiceYaml == null) {
                throw new ApmException("Main Service Yaml not found in CSAR");
            }

            try (InputStream inputStream = zipFile.getInputStream(mainServiceYaml)) {
                StringWriter writer = new StringWriter();
                IOUtils.copy(inputStream, writer, StandardCharsets.UTF_8);
                return writer.toString();
            }
        } catch (IOException e) {
            throw new ApmException("failed to unzip the csar file");
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
            throw new ApmException("failed to convert main service yaml to json");
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
