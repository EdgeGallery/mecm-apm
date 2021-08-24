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

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.isRegexMatched;


import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackageMf;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.AppStore;
import org.edgegallery.mecm.apm.model.AppTemplate;
import org.edgegallery.mecm.apm.model.ImageLocation;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.SwImageDescr;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageInfoDto;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.edgegallery.mecm.apm.utils.CompressUtility;
import org.edgegallery.mecm.apm.utils.Constants;
import org.modelmapper.ModelMapper;
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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

@Service("ApmService")
public class ApmService {

    static final int TOO_BIG = 104857600;

    private static final Logger LOGGER = LoggerFactory.getLogger(ApmService.class);

    private static final String ACCESS_TOKEN = "access_token";

    private static final String INVENTORY_URL = "/inventory/v1";

    private static final String EMPTY_RESPONSE = "response: {}";

    private static final String CONNECTION_FALED = "connection failed {}";

    private static final String FAILED_TO_CONNECT = "failed to connect ";

    private static final String FAILED = "failed {}";

    private static final String DATA_NOT_FOUND = "data not found sttaus {}";

    private static final String NOT_FOUND_STATUS = "not found status ";

    private static final String FAILURE_RESPONSE_STATUS = "received failure response status {}";

    private static final String FAILURE_RESPONSE_STATUS_CODE = "received failure response status ";

    private static final String FAILED_TO_GET_SW_IMAGE_FILE = "failed to get sw image descriptor file {}";

    private static final String SW_IMAGE_FILE_FAILURE = "failed to get sw image descriptor file";

    private static final String HTTPS = "https://";

    private static final String SSL = "/usr/app/ssl";

    private static final int BOUNDED_INPUTSTREAM_SIZE = 8 * 1024;

    private static final int BUFFER_READER_SIZE = 2 * 1024;

    private static final int LINE_MAX_LEN = 4 * 1024;

    private static final int READ_MAX_LONG = 10;

    private static final String MF_VERSION_META = "app_package_version";

    private static final String MF_PRODUCT_NAME = "app_product_name";

    private static final String MF_PROVIDER_META = "app_provider_id";

    private static final String MF_APP_DATETIME = "app_release_data_time";

    private static final String MF_APP_CLASS = "app_class";

    private static final String MF_APP_TYPE = "app_type";

    private static final String MF_APP_DESCRIPTION = "app_package_description";

    @Value("${apm.inventory-endpoint}")
    private String inventoryIp;

    @Value("${apm.inventory-port}")
    private String inventoryPort;

    @Value("${apm.mecm-repo-password:}")
    private String mecmRepoPassword;

    @Value("${apm.mecm-repo-username:}")
    private String mecmRepoUsername;

    @Value("${apm.mecm-repo-endpoint:}")
    private String mecmRepoEndpoint;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

    /**
     * Downloads app package csar from app store and stores it locally.
     *
     * @param appPkgPath app package path
     * @param packageId package ID
     * @param accessToken access token
     * @return downloaded input stream
     */
    public InputStream downloadAppPackage(String appPkgPath, String packageId, String accessToken) {
        ResponseEntity<Resource> response;

        LOGGER.info("Download application package from: {}", appPkgPath);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("ACCESS_TOKEN", accessToken);
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

    private DockerClient getDockerClient(String repo, String userName, String password) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerTlsVerify(true)
            .withDockerCertPath(SSL).withRegistryUrl(Constants.HTTPS_PROTO + repo).withRegistryUsername(userName)
            .withRegistryPassword(password).build();

        return DockerClientBuilder.getInstance(config).build();
    }

    /**
     * Downloads app image from repo.
     *
     * @param syncInfo sync app package details
     * @param imageInfoList list of images
     * @param downloadedImgs downloaded images
     */
    public void downloadAppImage(PkgSyncInfo syncInfo, List<SwImageDescr> imageInfoList, Set<String> downloadedImgs) {

        String[] sourceRepoHost;
        Map<String, AppRepo> repoInfo = syncInfo.getRepoInfo();
        for (SwImageDescr imageInfo : imageInfoList) {
            LOGGER.info("Download docker image {} ", imageInfo.getSwImage());

            sourceRepoHost = imageInfo.getSwImage().split("/");
            AppRepo repo = repoInfo.get(sourceRepoHost[0]);
            if (repo == null) {
                LOGGER.error("Download failed, source repo not configured: {}", sourceRepoHost[0]);
                throw new ApmException("docker image download failed source repo not configured " + sourceRepoHost[0]);
            }

            LOGGER.info("download docker image {}", imageInfo.getSwImage());

            DockerClient dockerClient = getDockerClient(sourceRepoHost[0], repo.getRepoUserName(),
                repo.getRepoPassword());

            try {
                dockerClient.pullImageCmd(imageInfo.getSwImage()).exec(new PullImageResultCallback()).awaitCompletion();
                downloadedImgs.add(imageInfo.getSwImage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApmException("failed to download image");
            } catch (NotFoundException e) {
                LOGGER.error("failed to download image {}, image not found in repository, {}", imageInfo.getSwImage(),
                    e.getMessage());
                throw new ApmException("failed to pull image from source repo");
            } catch (InternalServerErrorException e) {
                LOGGER.error("internal server error while downloading image {},{}", imageInfo.getSwImage(),
                    e.getMessage());
                throw new ApmException("failed to download docker image from source repo");
            }
        }

        LOGGER.info("images downloaded successfully");
    }

    /**
     * Returns list of image info.
     *
     * @param localFilePath csar file path
     * @param packageId package Id
     * @param tenantId tenant Id
     * @return list of image info
     */
    public List<String> getAppImageInfoFromMainService(String localFilePath, String packageId, String tenantId) {
        String yaml = ApmServiceHelper.getMainServiceYaml(localFilePath, getLocalIntendedDir(packageId, tenantId));
        return ApmServiceHelper.getImageInfo(yaml);
    }

    private String getEntryDefinitionFromMetadata(String appPkgDir, String metaFile) {
        List<File> files = (List<File>) FileUtils.listFiles(new File(appPkgDir), null, true);
        for (File file : files) {
            if (ApmServiceHelper.isSuffixExist(file.getName(), metaFile)) {
                try (InputStream inputStream = new FileInputStream(file)) {
                    Yaml yaml = new Yaml(new SafeConstructor());
                    Map<String, Object> meatData = yaml.load(inputStream);
                    return meatData.get("Entry-Definitions").toString();
                } catch (IOException e) {
                    throw new ApmException("failed to read metadata from app package");
                }
            }
        }
        throw new ApmException("failed, main service yaml not available in app package");
    }

    /**
     * Returns application template.
     *
     * @param appPackageDto application package info
     * @param tenantId tenant Id
     * @param appDeployType deploy type
     * @return list of image info
     */
    public AppTemplate getApplicationTemplateInfo(AppPackageDto appPackageDto, String tenantId, String appDeployType) {
        File yamlFile;

        try {
            String appPkgDir = getLocalIntendedDir(appPackageDto.getAppPkgId(), tenantId);

            String mainServiceYaml = appPkgDir + File.separator + getEntryDefinitionFromMetadata(appPkgDir,
                "TOSCA.meta");

            String appDefnDir = FilenameUtils.removeExtension(mainServiceYaml);
            CompressUtility.unzipApplicationPacakge(mainServiceYaml, appDefnDir);

            yamlFile = new File(
                appDefnDir + File.separator + getEntryDefinitionFromMetadata(appDefnDir, "TOSCA_VNFD.meta"));
        } catch (ApmException e) {
            LOGGER.error("failed to get main service template yaml {}", e.getMessage());
            throw new ApmException("failed to get main service template yaml");
        }

        try (InputStream inputStream = new FileInputStream(yamlFile)) {
            byte[] byteArray = IOUtils.toByteArray(inputStream);
            if (byteArray.length > TOO_BIG) {
                throw new IllegalStateException("file being unzipped is too big");
            }
            AppTemplate appTemplate = ApmServiceHelper
                .getApplicationTemplate(new String(byteArray, StandardCharsets.UTF_8));

            appTemplate.setAppId(appPackageDto.getAppId());
            appTemplate.setAppPkgName(appPackageDto.getAppPkgName());
            appTemplate.setVersion(appPackageDto.getAppPkgVersion());
            appTemplate.setTenantId(tenantId);
            String appPkgId = appPackageDto.getAppPkgId().substring(appPackageDto.getAppPkgId().length() - 32);
            appTemplate.setAppPackageId(appPkgId);
            appTemplate.setDeployType(appDeployType);

            return appTemplate;
        } catch (IOException e) {
            LOGGER.error("failed to get app template {}", e.getMessage());
            throw new ApmException("failed to get app template");
        }
    }

    /**
     * Returns list of image info.
     *
     * @param tenantId tenant ID
     * @param localFilePath csar file path
     * @param packageId package Id
     * @return list of image info
     */
    public List<SwImageDescr> getAppImageInfo(String tenantId, String localFilePath, String packageId) {
        String intendedDir = getLocalIntendedDir(packageId, tenantId);
        CompressUtility.unzipApplicationPacakge(localFilePath, intendedDir);

        FileUtils.deleteQuietly(new File(localFilePath));

        File swImageDesc = getFileFromPackage(tenantId, packageId, "Image/SwImageDesc", "json");
        try {
            return ApmServiceHelper
                .getSwImageDescrInfo(FileUtils.readFileToString(swImageDesc, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error(FAILED_TO_GET_SW_IMAGE_FILE, e.getMessage());
            throw new ApmException(SW_IMAGE_FILE_FAILURE);
        }
    }

    /**
     * Returns application deployment type.
     *
     * @param tenantId tenant ID
     * @param packageId package Id
     * @return app package deployment type
     */
    public String getAppPackageDeploymentType(String tenantId, String packageId) {
        File mf;
        try {
            mf = getFileFromPackage(tenantId, packageId, ".mf", "mf");
        } catch (ApmException e) {
            LOGGER.error("failed to get deployment type {}", e.getMessage());
            throw new ApmException(SW_IMAGE_FILE_FAILURE);
        }
        AppPackageMf appPkgMf = new AppPackageMf();
        try {
            readManifest(new File(mf.getPath()), appPkgMf);
        } catch (ApmException | YAMLException e) {
            LOGGER.error("failed to get deployment type {}", e.getMessage());
            throw new ApmException(SW_IMAGE_FILE_FAILURE);
        }
        return appPkgMf.getApp_class();
    }

    private void readManifest(File file, AppPackageMf appPkgMf) {
        // Fix the package type to CSAR, temporary
        try (BoundedInputStream boundedInput = new BoundedInputStream(FileUtils.openInputStream(file),
            BOUNDED_INPUTSTREAM_SIZE);
             InputStreamReader isr = new InputStreamReader(boundedInput, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr, BUFFER_READER_SIZE);) {
            for (String tempString; (tempString = readLine(reader)) != null; ) {
                // If line is empty, ignore
                if ("".equals(tempString) || !tempString.contains(":")) {
                    continue;
                }
                checkLines(tempString, appPkgMf);
            }
        } catch (IOException e) {
            LOGGER.error("Exception while parsing manifest file: {}", e.getMessage());
        }
    }

    private void checkLines(String tempString, AppPackageMf appPkgMf) {
        try {
            int count1 = tempString.indexOf(':');
            String meta = tempString.substring(0, count1).trim();
            int count = tempString.indexOf(':') + 1;
            String temp = tempString.substring(count).trim();
            switch (meta) {
                case MF_VERSION_META:
                    appPkgMf.setApp_package_version(temp);
                    break;
                case MF_PRODUCT_NAME:
                    appPkgMf.setApp_product_name(temp);
                    break;
                case MF_PROVIDER_META:
                    appPkgMf.setApp_provider_id(temp);
                    break;
                case MF_APP_DATETIME:
                    appPkgMf.setApp_release_data_time(temp);
                    break;
                case MF_APP_CLASS:
                    appPkgMf.setApp_class(temp);
                    break;
                case MF_APP_TYPE:
                    appPkgMf.setApp_type(temp);
                    break;
                case MF_APP_DESCRIPTION:
                    appPkgMf.setApp_package_description(temp);
                    break;
                default:
                    break;
            }
        } catch (StringIndexOutOfBoundsException e) {
            LOGGER.error("Nonstandard format: {}", e.getMessage());
        }
    }

    private String readLine(BufferedReader br) throws IOException {
        return read(br, LINE_MAX_LEN);
    }

    private String read(BufferedReader br, int lineMaxLen) throws IOException {
        int intC = br.read();
        if (-1 == intC) {
            return null;
        }
        StringBuilder sb = new StringBuilder(READ_MAX_LONG);
        while (intC != -1) {
            char c = (char) intC;
            if (c == '\n') {
                break;
            }
            if (sb.length() >= lineMaxLen) {
                throw new IOException("line too long");
            }
            sb.append(c);
            intC = br.read();
        }
        String str = sb.toString();
        if (!str.isEmpty() && str.endsWith("\r")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    /**
     * Update application package with MECM repo info.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     */
    public void updateAppPackageWithRepoInfo(String tenantId, String packageId) {

        File swImageDesc = getFileFromPackage(tenantId, packageId, "Image/SwImageDesc", "json");
        ApmServiceHelper.updateRepoInfoInSwImageDescr(swImageDesc, mecmRepoEndpoint);

        File chartsTar = getFileFromPackage(tenantId, packageId, "/Artifacts/Deployment/Charts/", "tar");
        try {
            CompressUtility.deCompress(chartsTar.getCanonicalFile().toString(),
                new File(chartsTar.getCanonicalFile().getParent()));

            FileUtils.deleteQuietly(chartsTar);

            File valuesYaml = getFileFromPackage(tenantId, packageId, "/values.yaml", "yaml");

            //update values.yaml
            Map<String, Object> values = loadvaluesYaml(valuesYaml);
            ImageLocation imageLocn = null;
            for (String key : values.keySet()) {
                if (key.equals(Constants.IMAGE_LOCATION)) {
                    ModelMapper mapper = new ModelMapper();
                    imageLocn = mapper.map(values.get(Constants.IMAGE_LOCATION), ImageLocation.class);
                    imageLocn.setDomainname(mecmRepoEndpoint);
                    imageLocn.setProject("mecm");
                    break;
                }
            }
            if (imageLocn != null) {
                values.put(Constants.IMAGE_LOCATION, imageLocn);
            } else {
                LOGGER.error("missing image location parameters ");
                throw new ApmException("failed to update values yaml, missing image location parameters");
            }
            String json = new Gson().toJson(values);
            FileUtils.writeStringToFile(valuesYaml, json, StandardCharsets.UTF_8.name());
            LOGGER.info("imageLocation updated in values yaml {}", json);

            CompressUtility.compress(valuesYaml.getParent(), valuesYaml.getParent() + ".tgz");

            FileUtils.deleteQuietly(new File(valuesYaml.getParent()));

            LOGGER.info("updated application package charts with repo details");
        } catch (IOException e) {
            throw new ApmException("failed to update repo info in charts, IO Exception ");
        }
    }

    private Map<String, Object> loadvaluesYaml(File valuesYaml) {

        Map<String, Object> valuesYamlMap;
        Yaml yaml = new Yaml(new SafeConstructor());
        try (InputStream inputStream = new FileInputStream(valuesYaml)) {
            valuesYamlMap = yaml.load(inputStream);
        } catch (FileNotFoundException e) {
            throw new ApmException("failed to find values yaml in app package");
        } catch (IOException e) {
            throw new ApmException("failed to load value yaml form charts");
        }
        return valuesYamlMap;
    }

    /**
     * Unzip docker images from application package.
     *
     * @param packageId package Id
     * @param tenantId tenant ID
     * @return docker image path
     */
    public String unzipDockerImages(String packageId, String tenantId) {
        String intendedDir = getLocalIntendedDir(packageId, tenantId);
        File dockerZip = getFileFromPackage(tenantId, packageId + Constants.IMAGE_INPATH, Constants.IMAGE_INPATH,
            "zip");

        try {
            CompressUtility.unzipApplicationPacakge(dockerZip.getCanonicalPath(), intendedDir + Constants.IMAGE_INPATH);
            return FilenameUtils.removeExtension(dockerZip.getCanonicalPath());
        } catch (IOException e) {
            LOGGER.error(FAILED_TO_GET_SW_IMAGE_FILE, e.getMessage());
            throw new ApmException(SW_IMAGE_FILE_FAILURE);
        }
    }

    /**
     * Loads docker images from application package to docker system.
     *
     * @param packageId package Id
     * @param loadDockerImages image descriptors
     * @param downloadedImgs docker images loaded
     */
    public void loadDockerImages(String packageId, List<SwImageDescr> loadDockerImages, Set<String> downloadedImgs) {
        String intendedDir = getLocalIntendedDir(packageId, null);

        for (SwImageDescr imgDescr : loadDockerImages) {

            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

            LOGGER.info("image to load {} ", imgDescr.getSwImage());

            try {
                dockerClient.loadImageCmd(new FileInputStream(intendedDir + "/" + imgDescr.getSwImage())).exec();
                imgDescr.setSwImage(imgDescr.getName());
                downloadedImgs.add(imgDescr.getName());
            } catch (NotFoundException e) {
                LOGGER.error("failed to load docker image tar, image not found {}", e.getMessage());
                throw new ApmException("failed to docker image, not found");
            } catch (InternalServerErrorException | FileNotFoundException e) {
                LOGGER.error("internal server error while downloading image,{}", e.getMessage());
                throw new ApmException("failed to load docker image from tar");
            }
        }
        LOGGER.info("image load complete successfully");
    }

    /**
     * Returns file from the package.
     *
     * @param tenantId tenant ID
     * @param packageId package Id
     * @param file file/path to search
     * @param extension file extension
     * @return file,
     */
    public File getFileFromPackage(String tenantId, String packageId, String file, String extension) {
        String dir = getLocalIntendedDir(packageId, tenantId);
        String ext;

        List<File> files = (List<File>) FileUtils.listFiles(new File(dir), null, true);
        try {
            for (File f : files) {
                if (f.getCanonicalPath().contains(file)) {
                    ext = getFileExtension(f.getCanonicalPath());
                    if (ext.equals(extension)) {
                        return f;
                    }
                    if (extension.equals("tar") && (ext.equals("tgz") || ext.equals("tar.gz") || ext.equals("tar"))) {
                        return f;
                    }
                }
            }
        } catch (IOException | ApmException e) {
            throw new ApmException(file + e.getMessage());
        }
        throw new ApmException(file + " file not found");
    }

    private String getFileExtension(String file) {
        List<String> extensions = Arrays.asList("tar", "tar.gz", "tgz", "gz", "zip", "json", "yaml", "yml", "mf");
        for (String ext : extensions) {
            if (file.endsWith("." + ext)) {
                return ext;
            }
        }
        throw new ApmException(file + " file not found");
    }

    /**
     * Returns local intended dir path.
     *
     * @param packageId package id
     * @param tenantId tenantId
     * @return returns local intended dir path
     */
    public String getLocalIntendedDir(String packageId, String tenantId) {
        if (tenantId != null) {
            return localDirPath + File.separator + packageId + tenantId;
        }
        return localDirPath + File.separator + packageId;
    }

    /**
     * Returns edge repository address.
     *
     * @param hostIp host ip
     * @param accessToken access token
     * @return returns edge repository info
     * @throws ApmException exception if failed to get edge repository details
     */
    public String getRepoInfoOfHost(String hostIp, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":").append(inventoryPort)
            .append(INVENTORY_URL).append("/mechosts/").append(hostIp).toString();

        String response = sendGetRequest(url, accessToken);

        LOGGER.info(EMPTY_RESPONSE, response);

        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
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
     * Gets MEPM endpoint from inventory.
     *
     * @param hostIp host ip
     * @param accessToken access token
     * @return returns MEPM config info
     * @throws ApmException exception if failed to get MEPM config details
     */
    public String getMepmCfgOfHost(String hostIp, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":").append(inventoryPort)
            .append(INVENTORY_URL).append("/mechosts/").append(hostIp).toString();

        String response = sendGetRequest(url, accessToken);

        LOGGER.info(EMPTY_RESPONSE, response);

        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        JsonElement mepmIp = jsonObject.get("mepmIp");
        if (mepmIp == null) {
            LOGGER.error(Constants.REPO_INFO_NULL, hostIp);
            throw new ApmException("MEPM IP is null for host " + hostIp);
        }

        String ip = mepmIp.getAsString();
        if (!isRegexMatched(Constants.IP_REGEX, ip)) {
            LOGGER.error(Constants.REPO_IP_INVALID, hostIp);
            throw new ApmException("MEPM ip is invalid for host " + hostIp);
        }

        url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":").append(inventoryPort)
            .append(INVENTORY_URL).append("/mepms/").append(ip).toString();
        response = sendGetRequest(url, accessToken);

        LOGGER.info(EMPTY_RESPONSE, response);

        jsonObject = new JsonParser().parse(response).getAsJsonObject();
        JsonElement mepmPort = jsonObject.get("mepmPort");
        if (mepmPort == null) {
            LOGGER.error(Constants.REPO_INFO_NULL, hostIp);
            throw new ApmException("MEPM port is null for host " + hostIp);
        }

        String port = mepmPort.getAsString();
        if (!isRegexMatched(Constants.PORT_REGEX, port)) {
            LOGGER.error(Constants.REPO_PORT_INVALID, hostIp);
            throw new ApmException("MEPM port is invalid for host " + hostIp);
        }

        return mepmIp.getAsString() + ":" + mepmPort.getAsString();
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
     * Deletes app package csar file.
     *
     * @param localFilePath local file path
     */
    public void deleteAppPackageFile(String localFilePath) {
        if (localFilePath == null) {
            LOGGER.error(Constants.LOCAL_FILE_PATH_NULL);
            throw new ApmException(Constants.LOCAL_FILE_PATH_NULL);
        }
        FileUtils.deleteQuietly(new File(localFilePath));
    }

    /**
     * Returns edge repository address.
     *
     * @param appstoreIp appstore ip
     * @param accessToken access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public AppStore getAppStoreCfgFromInventory(String appstoreIp, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":").append(inventoryPort)
            .append(INVENTORY_URL).append("/appstore/").append(appstoreIp).toString();

        String response = sendGetRequest(url, accessToken);

        return new Gson().fromJson(response, AppStore.class);
    }

    /**
     * Returns edge repository address.
     *
     * @param accessToken access token
     * @return returns all appstore configurations
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public List<AppRepo> getAllAppRepoCfgFromInventory(String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":").append(inventoryPort)
            .append(INVENTORY_URL).append("/apprepos").toString();

        List<AppRepo> appRepoRecords = new LinkedList<>();
        try {
            String response = sendGetRequest(url, accessToken);
            JsonArray appRepoRecs = new JsonParser().parse(response).getAsJsonArray();
            for (JsonElement appRepoRec : appRepoRecs) {
                AppRepo apprepo = new Gson().fromJson(appRepoRec, AppRepo.class);
                appRepoRecords.add(apprepo);
            }
        } catch (NoSuchElementException | ApmException ex) {
            LOGGER.error("failed to fetch app source repositories");
        }
        return appRepoRecords;
    }

    /**
     * Returns edge repository address.
     *
     * @param url URL
     * @param accessToken access token
     * @return returns all appstore configurations
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public String sendGetRequest(String url, String accessToken) {

        LOGGER.info("GET request: {}", url);
        ResponseEntity<String> response;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(ACCESS_TOKEN, accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (ResourceAccessException ex) {
            LOGGER.error(CONNECTION_FALED, ex.getMessage());
            throw new ApmException(FAILED_TO_CONNECT + ex.getMessage());
        } catch (HttpClientErrorException ex) {
            LOGGER.error(FAILED, ex.getMessage());
            throw new ApmException("error while fetching " + ex.getMessage());
        }

        if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
            LOGGER.error(DATA_NOT_FOUND, response.getStatusCode());
            throw new NoSuchElementException(NOT_FOUND_STATUS + response.getStatusCode());
        }

        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            LOGGER.error(FAILURE_RESPONSE_STATUS, response.getStatusCode());
            throw new ApmException(FAILURE_RESPONSE_STATUS_CODE + response.getStatusCode());
        }

        return response.getBody();
    }

    /**
     * Sends delete request.
     *
     * @param url URL
     * @param accessToken access token
     * @throws ApmException exception if failed to delete
     */
    public void sendDeleteRequest(String url, String accessToken) {

        LOGGER.info("DELETE request: {}", url);
        ResponseEntity<String> response;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(ACCESS_TOKEN, accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (ResourceAccessException ex) {
            LOGGER.error(CONNECTION_FALED, ex.getMessage());
            throw new ApmException(FAILED_TO_CONNECT + ex.getMessage());
        } catch (HttpClientErrorException ex) {
            LOGGER.error(FAILED, ex.getMessage());
            throw new ApmException("error while delete " + ex.getMessage());
        }

        if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
            LOGGER.error(DATA_NOT_FOUND, response.getStatusCode());
            throw new NoSuchElementException(NOT_FOUND_STATUS + response.getStatusCode());
        }

        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            LOGGER.error(FAILURE_RESPONSE_STATUS, response.getStatusCode());
            throw new ApmException(FAILURE_RESPONSE_STATUS_CODE + response.getStatusCode());
        }
    }

    /**
     * Sends post request.
     *
     * @param url URL
     * @param reqBody request body
     * @param accessToken access token
     * @throws ApmException exception if failed to delete
     */
    public void sendPostRequest(String url, String reqBody, String accessToken) {

        LOGGER.info("POST request: {}", url);
        ResponseEntity<String> response;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(ACCESS_TOKEN, accessToken);
            HttpEntity<String> entity = new HttpEntity<>(reqBody, headers);
            response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (ResourceAccessException ex) {
            LOGGER.error(CONNECTION_FALED, ex.getMessage());
            throw new ApmException(FAILED_TO_CONNECT + ex.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            LOGGER.error(FAILED, ex.getMessage());
            throw new ApmException("error while delete " + ex.getMessage());
        }

        if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
            LOGGER.error(DATA_NOT_FOUND, response.getStatusCode());
            throw new NoSuchElementException(NOT_FOUND_STATUS + response.getStatusCode());
        }

        if (!HttpStatus.OK.equals(response.getStatusCode())) {
            LOGGER.error(FAILURE_RESPONSE_STATUS, response.getStatusCode());
            throw new ApmException(FAILURE_RESPONSE_STATUS_CODE + response.getStatusCode());
        }
    }

    /**
     * Returns application package info.
     *
     * @param appstoreEndpoint appstore endpoint
     * @param accessToken access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public List<AppPackageInfoDto> getAppPackagesInfoFromAppStore(String appstoreEndpoint, String accessToken) {
        String appsUrl = new StringBuilder(HTTPS).append(appstoreEndpoint).append("/mec/appstore/v1/apps").toString();

        String response = sendGetRequest(appsUrl, accessToken);

        List<String> appIds = new LinkedList<>();
        JsonArray appsArray = new JsonParser().parse(response).getAsJsonArray();
        for (JsonElement appElement : appsArray) {
            JsonObject app = appElement.getAsJsonObject();
            appIds.add(app.get("appId").getAsString());
        }

        List<AppPackageInfoDto> appPkgInfos = new LinkedList<>();
        for (String appId : appIds) {
            try {
                List<AppPackageInfoDto> pkgInfos = getAppPackagesInfoBasedOnAppId(appstoreEndpoint, appId, accessToken);
                appPkgInfos.addAll(pkgInfos);
            } catch (NoSuchElementException ex) {
                LOGGER.error("failed to get app package info {}", ex.getMessage());
            }
        }

        if (appPkgInfos.isEmpty()) {
            throw new NoSuchElementException("app package record does not exist");
        }
        return appPkgInfos;
    }

    /**
     * Returns application package info from app store.
     *
     * @param appstoreEndpoint appstore endpoint
     * @param appId app ID
     * @param packageId package ID
     * @param accessToken access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public AppPackageInfoDto getAppPkgInfoFromAppStore(String appstoreEndpoint, String appId, String packageId,
        String accessToken) {
        String appsUrl = new StringBuilder(HTTPS).append(appstoreEndpoint).append("/mec/appstore/v1/apps/")
            .append(appId).append("/packages/").append(packageId).toString();

        String response = sendGetRequest(appsUrl, accessToken);
        LOGGER.info("applications package info response: {}", response);

        return new Gson().fromJson(response, AppPackageInfoDto.class);
    }

    /**
     * Returns application package info.
     *
     * @param appstoreEndpoint appstore endpoint
     * @param accessToken access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    private List<AppPackageInfoDto> getAppPackagesInfoBasedOnAppId(String appstoreEndpoint, String appId,
        String accessToken) {
        String appsUrl = new StringBuilder(HTTPS).append(appstoreEndpoint).append("/mec/appstore/v1/apps/")
            .append(appId).append("/packages").toString();

        String response = sendGetRequest(appsUrl, accessToken);

        List<AppPackageInfoDto> appPackageInfos = new LinkedList<>();
        JsonArray appsArray = new JsonParser().parse(response).getAsJsonArray();
        for (JsonElement app : appsArray) {
            AppPackageInfoDto dto = new Gson().fromJson(app.getAsJsonObject().toString(), AppPackageInfoDto.class);
            dto.setSyncStatus(Constants.APP_NOT_IN_SYNC);
            appPackageInfos.add(dto);
        }
        LOGGER.info("applications packages: {}", response);
        return appPackageInfos;
    }

    /**
     * Uploads app image from repo.
     *
     * @param imageInfoList list of images
     * @param uploadedImgs uploaded images
     */
    public void uploadAppImage(List<SwImageDescr> imageInfoList, Set<String> uploadedImgs) {

        for (SwImageDescr imageInfo : imageInfoList) {
            LOGGER.info("Docker image to  upload: {}", imageInfo.getSwImage());

            DockerClient dockerClient = getDockerClient(mecmRepoEndpoint, mecmRepoUsername, mecmRepoPassword);

            String[] dockerImageNames = imageInfo.getSwImage().split("/");
            String uploadImgName;
            if (dockerImageNames.length > 1) {
                uploadImgName = new StringBuilder(mecmRepoEndpoint).append("/mecm/")
                    .append(dockerImageNames[dockerImageNames.length - 1]).toString();
            } else {
                uploadImgName = new StringBuilder(mecmRepoEndpoint).append("/mecm/").append(dockerImageNames[0])
                    .toString();
            }

            LOGGER.info("tagged image upload: {}", uploadImgName);
            String id = dockerClient.inspectImageCmd(imageInfo.getSwImage()).exec().getId();
            dockerClient.tagImageCmd(id, uploadImgName, imageInfo.getVersion()).withForce().exec();

            uploadedImgs.add(uploadImgName);
            try {
                dockerClient.pushImageCmd(uploadImgName).exec(new PushImageResultCallback()).awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApmException("failed to upload image");
            } catch (NotFoundException e) {
                LOGGER.error("failed to upload image {}, image not found in repository, {}", uploadImgName,
                    e.getMessage());
                throw new ApmException("failed to push image to edge repo");
            } catch (InternalServerErrorException e) {
                LOGGER.error("internal server error while uploading image {},{}", uploadImgName, e.getMessage());
                throw new ApmException("failed to push image to edge repo");
            }
        }
        LOGGER.info("images uploaded successfully");
    }

    /**
     * Deletes app package docker images.
     *
     * @param imageInfoList list of images
     */
    public void deleteAppPkgDockerImages(Set<String> imageInfoList) {
        if (imageInfoList == null || imageInfoList.isEmpty()) {
            return;
        }
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        String id;
        for (String image : imageInfoList) {
            try {
                id = dockerClient.inspectImageCmd(image).exec().getId();
                if (id != null) {
                    LOGGER.debug("delete docker image  {}", image);
                    dockerClient.removeImageCmd(id).withForce(true).exec();
                }
            } catch (NotFoundException | ConflictException ex) {
                LOGGER.error("docker image {} not found {}", image, ex.getMessage());
            }
        }
    }

    /**
     * Deletes docker images from repo.
     *
     * @param imageInfoList list of images
     */
    public void deleteAppPkgDockerImagesFromRepo(Set<String> imageInfoList) {
        if (imageInfoList == null || imageInfoList.isEmpty()) {
            return;
        }
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withRegistryUsername(mecmRepoUsername).withRegistryPassword(mecmRepoPassword).build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        String id;
        for (String image : imageInfoList) {
            try {
                id = dockerClient.inspectImageCmd(image).exec().getId();
                if (id != null) {
                    LOGGER.debug("delete docker image from repo {}", image);
                }
            } catch (NotFoundException | ConflictException ex) {
                LOGGER.error("docker image {} not found {}", image, ex.getMessage());
            }
        }
    }
}
