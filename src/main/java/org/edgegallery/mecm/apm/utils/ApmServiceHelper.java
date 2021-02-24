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

import static org.edgegallery.mecm.apm.utils.FileChecker.sanitizeFileName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.SwImageDescr;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public final class ApmServiceHelper {

    static final int TOO_MANY = 1024;
    static final int TOO_BIG = 104857600;
    private static final Logger LOGGER = LoggerFactory.getLogger(ApmServiceHelper.class);

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
        try {
            if (localFileDir.exists() && localFileDir.isDirectory()) {
                return localFileDir.getCanonicalPath();
            }
            if (!localFileDir.mkdir()) {
                LOGGER.info(Constants.FAILED_TO_CREATE_DIR);
                throw new ApmException(Constants.FAILED_TO_CREATE_DIR);
            }
            return localFileDir.getCanonicalPath();
        } catch (IOException e) {
            LOGGER.info(Constants.FAILED_TO_GET_FAIL_PATH);
            throw new ApmException(Constants.FAILED_TO_GET_FAIL_PATH);
        }
    }

    /**
     * Returns local file path based on tenant Id and package Id.
     *
     * @param tenantId  tenant ID
     * @param packageId package ID
     * @return local file path
     */
    public static String getLocalFilePath(String baseDirPath, String tenantId, String packageId) {
        return new StringBuilder(baseDirPath).append(File.separator)
                .append(packageId).append(tenantId).append(File.separator)
                .append(packageId + ".csar").toString();
    }

    /**
     * Saves input stream to file.
     *
     * @param resourceStream   input resource stream
     * @param packageId        package ID
     * @param tenantId         tenant ID
     * @param localDirBasePath local dir path
     * @return file path
     */
    public static String saveInputStreamToFile(InputStream resourceStream, String packageId, String tenantId,
                                               String localDirBasePath) {
        if (resourceStream == null) {
            LOGGER.info(Constants.FAILED_TO_READ_INPUTSTREAM, packageId);
            throw new ApmException("failed to read input stream from app store for package " + packageId);
        }
        String localDirPath;
        if (tenantId != null) {
            localDirPath = createDir(localDirBasePath + File.separator + packageId + tenantId);
        } else if (localDirBasePath == null) {
            localDirPath = createDir(packageId);
        } else {
            localDirPath = createDir(localDirBasePath + File.separator + packageId);
        }

        String localFilePath = localDirPath + File.separator + packageId + ".csar";
        File file = new File(localFilePath);
        try {
            FileUtils.copyInputStreamToFile(resourceStream, file);
            FileChecker.check(file);
            LOGGER.info("app package {} downloaded from appstore successfully", packageId);
            return file.getCanonicalPath();
        } catch (IOException e) {
            LOGGER.error(Constants.FAILED_TO_CREATE_CSAR, packageId);
            throw new ApmException("failed to create csar file for package " + packageId);
        }
    }

    /**
     * Save app package file locally.
     *
     * @param multipartFile    save app Package file
     * @param packageId        package ID
     * @param tenantId         tenant ID
     * @param localDirBasePath base directory
     * @return file saved path
     */
    public static String saveMultipartFile(MultipartFile multipartFile, String packageId, String tenantId,
                                           String localDirBasePath) {
        FileChecker.check(multipartFile);
        if (tenantId == null) {
            String localDirPath = createDir(localDirBasePath + File.separator + packageId);
        } else {
            String localDirPath = createDir(localDirBasePath + File.separator + packageId + tenantId);
        }

        String localFilePath = localDirBasePath + File.separator + packageId + File.separator + packageId + ".csar";
        File file = new File(localFilePath);
        try {
            multipartFile.transferTo(file);
            LOGGER.info("app package saved locally ", packageId);
            return file.getCanonicalPath();
        } catch (IOException e) {
            LOGGER.error(Constants.FAILED_TO_SAVE_CSAR, packageId);
            throw new ApmException("failed to save csar package locally for package " + packageId);
        }
    }

    /**
     * Returns main service template content in string format.
     *
     * @param localFilePath CSAR file path
     * @return main service template content in string
     */
    public static String getMainServiceYaml(String localFilePath, String intendedDir) {
        ZipEntry mainServiceYaml = null;
        try (ZipFile zipFile = new ZipFile(localFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int entriesCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entriesCount++;
                // sanitize file path
                String fileName = sanitizeFileName(entry.getName(), intendedDir);
                if (!entry.isDirectory() && fileName.contains("APPD/Definition/MainServiceTemplate.yaml")) {
                    mainServiceYaml = entry;
                    break;
                }
                if (entriesCount > TOO_MANY) {
                    throw new IllegalStateException("too many files to unzip");
                }
            }

            if (mainServiceYaml == null) {
                LOGGER.error(Constants.SERVICE_YAML_NOT_FOUND);
                throw new ApmException(Constants.SERVICE_YAML_NOT_FOUND);
            }

            try (InputStream inputStream = zipFile.getInputStream(mainServiceYaml)) {
                byte[] byteArray = IOUtils.toByteArray(inputStream);
                if (byteArray.length > TOO_BIG) {
                    throw new IllegalStateException("file being unzipped is too big");
                }
                return new String(byteArray, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error(Constants.FAILED_TO_UNZIP_CSAR);
            throw new ApmException(Constants.FAILED_TO_UNZIP_CSAR);
        }
    }

    /**
     * Returns software image descriptor content in string format.
     *
     * @param localFilePath CSAR file path
     * @param intendedDir   intended directory
     */
    public static void unzipApplicationPacakge(String localFilePath, String intendedDir) {

        try (ZipFile zipFile = new ZipFile(localFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int entriesCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entriesCount > TOO_MANY) {
                    throw new IllegalStateException("too many files to unzip");
                }
                entriesCount++;
                // sanitize file path
                String fileName = sanitizeFileName(entry.getName(), intendedDir);
                if (!entry.isDirectory()) {
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        if (inputStream.available() > TOO_BIG) {
                            throw new IllegalStateException("file being unzipped is too big");
                        }
                        FileUtils.copyInputStreamToFile(inputStream, new File(fileName));
                        LOGGER.info("unzip package... {}", entry.getName());
                    }
                } else {

                    File dir = new File(fileName);
                    boolean dirStatus = dir.mkdirs();
                    LOGGER.debug("creating dir {}, status {}", fileName, dirStatus);
                }
            }
        } catch (IOException e) {
            LOGGER.error(Constants.FAILED_TO_UNZIP_CSAR);
            throw new ApmException(Constants.FAILED_TO_UNZIP_CSAR);
        }
    }

    /**
     * Returns list of image details.
     *
     * @param mainServiceYaml main service template file content
     * @return list of image details
     */
    public static List<String> getImageInfo(String mainServiceYaml) {
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        ObjectMapper jsonWriter = new ObjectMapper();
        String response;
        try {
            response = jsonWriter.writeValueAsString(om.readValue(mainServiceYaml, Object.class));
        } catch (JsonProcessingException e) {
            LOGGER.error(Constants.FAILED_TO_CONVERT_YAML_TO_JSON, e.getMessage());
            throw new ApmException(Constants.FAILED_TO_CONVERT_YAML_TO_JSON);
        }

        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        JsonObject topologyTemplate = getChildJsonObject(jsonObject, "topology_template");
        JsonObject nodeTemplates = getChildJsonObject(topologyTemplate, "node_templates");

        Set<Entry<String, JsonElement>> entrySet = nodeTemplates.entrySet();
        List<JsonObject> vdus = new LinkedList<>();
        for (Map.Entry<String, JsonElement> entry : entrySet) {
            String type = entry.getValue().getAsJsonObject().get("type").getAsString();
            if ("tosca.nodes.nfv.Vdu.Compute".equals(type)) {
                vdus.add(entry.getValue().getAsJsonObject());
            }
        }

        List<String> imageList = new LinkedList<>();
        for (JsonObject vdu : vdus) {
            JsonObject properties = getChildJsonObject(vdu, "properties");
            JsonObject swImageData = getChildJsonObject(properties, "sw_image_data");
            String imageName = getChildJsonObjectValue(swImageData, "name");
            imageList.add(imageName);
        }
        return imageList;
    }

    /**
     * Returns list of image details.
     *
     * @param swImageDescr software image descriptor file content
     * @return list of image details
     */
    public static List<SwImageDescr> getSwImageDescrInfo(String swImageDescr) {

        List<SwImageDescr> swImgDescrs = new LinkedList<>();
        JsonArray swImgDescrArray = new JsonParser().parse(swImageDescr).getAsJsonArray();
        SwImageDescr swDescr;
        for (JsonElement descr : swImgDescrArray) {
            swDescr = new Gson().fromJson(descr.getAsJsonObject().toString(), SwImageDescr.class);
            swImgDescrs.add(swDescr);
        }
        LOGGER.info("sw image descriptors: {}", swImgDescrs);
        return swImgDescrs;
    }

    /**
     * Updates software image descriptor with docker repo info.
     *
     * @param swImageDescr software image descriptor file content
     */
    public static void updateRepoInfoInSwImageDescr(File swImageDescr, String mecmRepoEndpoint) {

        try {
            String descrStr = FileUtils.readFileToString(swImageDescr, StandardCharsets.UTF_8);
            JsonArray swImgDescrArray = new JsonParser().parse(descrStr).getAsJsonArray();

            for (JsonElement descr : swImgDescrArray) {
                JsonObject jsonObject = descr.getAsJsonObject();
                String swImage = jsonObject.get("swImage").getAsString();
                String[] image = swImage.split("/");

                if (image.length > 1) {
                    jsonObject.addProperty("swImage", mecmRepoEndpoint + "/mecm/" + image[image.length - 1]);
                } else {
                    jsonObject.addProperty("swImage", mecmRepoEndpoint + "/mecm/" + image[0]);
                }
            }
            FileUtils.writeStringToFile(swImageDescr, swImgDescrArray.toString(), StandardCharsets.UTF_8.name());
            LOGGER.info("Updated swImages : {}", swImgDescrArray);
        } catch (IOException e) {
            LOGGER.info("failed to update sw image descriptor");
            throw new ApmException("Failed to update repo info to image descriptor file");
        }
    }

    private static JsonObject getChildJsonObject(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || element instanceof JsonNull) {
            LOGGER.error(Constants.ERROR_ELEMENT_NOT_FOUND, key);
            throw new ApmException(key + Constants.ELEMENT_NOT_FOUND);
        }
        return element.getAsJsonObject();
    }

    private static String getChildJsonObjectValue(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || element instanceof JsonNull) {
            LOGGER.error(Constants.ERROR_ELEMENT_NOT_FOUND, key);
            throw new ApmException(key + Constants.ELEMENT_NOT_FOUND);
        }
        return element.getAsString();
    }

    /**
     * Validates given string against regex.
     *
     * @param pattern pattern to match
     * @param param   stirng to be validated
     * @return true if param is matched with pattern
     */
    public static boolean isRegexMatched(String pattern, String param) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(param);
        return m.matches();
    }

    /**
     * Generates random app ID.
     *
     * @return random app ID
     */
    public static String generateAppId() {
        String appPkgIdRandom = UUID.randomUUID().toString();
        return appPkgIdRandom.replace("-", "");
    }

    /**
     * Returns list of MecHostDto.
     *
     * @param hostList host list
     * @return list of MecHostDto
     */
    public static List<MecHostDto> getHostList(String hostList) {
        if (hostList == null) {
            LOGGER.error("host list is empty");
            throw new ApmException("host list is empty");
        }
        List<MecHostDto> mecHostDtos = new LinkedList<>();
        List<String> newHostList = Arrays.asList(hostList.trim().split("\\s*,\\s*"));
        for (String host : newHostList) {
            MecHostDto dto = new MecHostDto();
            dto.setHostIp(host);
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<MecHostDto>> violations = validator.validate(dto);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            mecHostDtos.add(dto);
        }
        return mecHostDtos;
    }
}
