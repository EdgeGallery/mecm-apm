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
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getSwImageDescrInfo;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.isRegexMatched;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.unzipApplicationPacakge;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.updateRepoInfoInSwImageDescr;

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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.AppStore;
import org.edgegallery.mecm.apm.model.ImageLocation;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.SwImageDescr;
import org.edgegallery.mecm.apm.model.dto.AppPackageInfoDto;
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

@Service("ApmService")
public class ApmService {

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
    private static final String HTTPS = "https://";

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
     * @param appPkgPath  app package path
     * @param packageId   package ID
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
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerTlsVerify(true)
                .withDockerCertPath("/usr/app/ssl")
                .withRegistryUrl(Constants.HTTPS_PROTO + repo)
                .withRegistryUsername(userName)
                .withRegistryPassword(password)
                .build();

        return DockerClientBuilder.getInstance(config).build();
    }

    /**
     * Downloads app image from repo.
     *
     * @param syncInfo      sync app package details
     * @param imageInfoList list of images
     */
    public void downloadAppImage(PkgSyncInfo syncInfo, List<SwImageDescr> imageInfoList,
                                 Set<String> downloadedImgs) {

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
                dockerClient.pullImageCmd(imageInfo.getSwImage())
                        .exec(new PullImageResultCallback()).awaitCompletion();
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
     * @param packageId     package Id
     * @param tenantId      tenant Id
     * @return list of image info
     */
    public List<String> getAppImageInfo(String localFilePath, String packageId, String tenantId) {
        String yaml = getMainServiceYaml(localFilePath, getLocalIntendedDir(packageId, tenantId));
        return getImageInfo(yaml);
    }

    /**
     * Returns list of image info.
     *
     * @param localFilePath csar file path
     * @param packageId     package Id
     * @return list of image info
     */
    public List<SwImageDescr> getAppImageInfo(String localFilePath, String packageId) {
        String intendedDir = getLocalIntendedDir(packageId, null);
        unzipApplicationPacakge(localFilePath, intendedDir);
        try {
            FileUtils.forceDelete(new File(localFilePath));
        } catch (IOException ex) {
            LOGGER.debug("failed to delete csar package {}", ex.getMessage());
        }

        File swImageDesc = getFileFromPackage(packageId, "Image/SwImageDesc", "json");
        try {
            return getSwImageDescrInfo(FileUtils.readFileToString(swImageDesc, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("failed to get sw image descriptor file {}", e.getMessage());
            throw new ApmException("failed to get sw image descriptor file");
        }
    }

    /**
     * Update application package with MECM repo info.
     *
     * @param packageId package ID
     */
    public void updateAppPackageWithRepoInfo(String packageId) {

        File swImageDesc = getFileFromPackage(packageId, "Image/SwImageDesc", "json");
        updateRepoInfoInSwImageDescr(swImageDesc, mecmRepoEndpoint);

        File chartsTar = getFileFromPackage(packageId, "/Artifacts/Deployment/Charts/", "tar");
        try {
            deCompress(chartsTar.getCanonicalFile().toString(),
                    new File(chartsTar.getCanonicalFile().getParent()));

            FileUtils.forceDelete(chartsTar);
            File valuesYaml = getFileFromPackage(packageId, "/values.yaml", "yaml");

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

            compress(valuesYaml.getParent());

            FileUtils.deleteDirectory(new File(valuesYaml.getParent()));

            LOGGER.info("updateed application package charts with repo details");
        } catch (IOException e) {
            throw new ApmException("failed to find charts directory");
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
     * ZIP application package.
     *
     * @param packageId application package ID
     */
    public void compressAppPackage(String packageId) {
        LOGGER.info("Compress application package to csar...");
        String intendedDir = getLocalIntendedDir(packageId, null);
        final Path srcDir = Paths.get(intendedDir);
        String zipFileName = intendedDir.concat(".csar");
        try (ZipOutputStream os = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                        throws IOException {
                    if (!srcDir.equals(dir)) {
                        os.putNextEntry(new ZipEntry(srcDir.relativize(dir).toString() + "/"));
                        os.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    try {
                        Path targetFile = srcDir.relativize(file);
                        os.putNextEntry(new ZipEntry(targetFile.toString()));
                        byte[] bytes = Files.readAllBytes(file);
                        os.write(bytes, 0, bytes.length);
                        os.closeEntry();
                    } catch (IOException e) {
                        throw new ApmException("failed to zip application package");
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ApmException("failed to zip application package IO exception");
        }
        try {
            FileUtils.deleteDirectory(new File(intendedDir));
            FileUtils.moveFileToDirectory(new File(zipFileName), new File(intendedDir), true);
        } catch (IOException e) {
            throw new ApmException("failed to delete redundant files from app package");
        }
    }

    /**
     * Unzip docker images from application package.
     *
     * @param packageId package Id
     * @return docker image path
     */
    public String unzipDockerImages(String packageId) {
        String intendedDir = getLocalIntendedDir(packageId, null);
        File dockerZip = getFileFromPackage(packageId + Constants.IMAGE_INPATH, Constants.IMAGE_INPATH, "zip");

        try {
            unzipApplicationPacakge(dockerZip.getCanonicalPath(), intendedDir + Constants.IMAGE_INPATH);
            return FilenameUtils.removeExtension(dockerZip.getCanonicalPath());
        } catch (IOException e) {
            LOGGER.error("failed to get sw image descriptor file {}", e.getMessage());
            throw new ApmException("failed to get sw image descriptor file");
        }
    }

    /**
     * Loads docker images from application package to docker system.
     *
     * @param packageId        package Id
     * @param loadDockerImages image descriptors
     * @param downloadedImgs   docker images loaded
     */
    public void loadDockerImages(String packageId, List<SwImageDescr> loadDockerImages, Set<String> downloadedImgs) {
        String intendedDir = getLocalIntendedDir(packageId, null);

        for (SwImageDescr imgDescr : loadDockerImages) {
            DockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    //.withRegistryUsername(syncAppPackage.getAppstoreRepoUserName())
                    //.withRegistryPassword(syncAppPackage.getAppstoreRepoPassword())
                    .build();

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
     * Decompress tar file.
     *
     * @param tarFile  tar file
     * @param destFile destination folder
     */
    private void deCompress(String tarFile, File destFile) {
        TarArchiveInputStream tis = null;
        try (FileInputStream fis = new FileInputStream(tarFile)) {

            if (tarFile.contains(".tar")) {
                tis = new TarArchiveInputStream(new BufferedInputStream(fis));
            } else {
                GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(fis));
                tis = new TarArchiveInputStream(gzipInputStream);
            }

            TarArchiveEntry tarEntry;
            while ((tarEntry = tis.getNextTarEntry()) != null) {
                if (tarEntry.isDirectory()) {
                    LOGGER.debug("skip directory");
                } else {
                    if (!tarEntry.isDirectory()) {

                        File outputFile = new File(destFile + File.separator + tarEntry.getName());
                        LOGGER.info("deCompressing... {}", outputFile.getName());
                        boolean result = outputFile.getParentFile().mkdirs();
                        LOGGER.debug("create directory result {}", result);
                        IOUtils.copy(tis, new FileOutputStream(outputFile));
                    }
                }
            }
        } catch (IOException ex) {
            throw new ApmException("failed to decompress, IO exception " + ex.getMessage());
        } finally {
            if (tis != null) {
                try {
                    tis.close();
                } catch (IOException ex) {
                    LOGGER.error("failed to close tar input stream {} ", ex.getMessage());
                }
            }
        }
    }

    private void compress(String sourceDir) {
        if (sourceDir == null || sourceDir.isEmpty()) {
            return;
        }

        File destination = new File(sourceDir);
        try (FileOutputStream destOutStream = new FileOutputStream(destination.getCanonicalPath().concat(".tgz"));
             GZIPOutputStream gipOutStream = new GZIPOutputStream(new BufferedOutputStream(destOutStream));
             TarArchiveOutputStream outStream = new TarArchiveOutputStream(gipOutStream)) {

            addFileToTar(sourceDir, "", outStream);

        } catch (IOException e) {
            throw new ApmException("failed to compress " + e.getMessage());
        }
    }

    private void addFileToTar(String filePath, String parent, TarArchiveOutputStream tarArchive) throws IOException {

        File file = new File(filePath);
        LOGGER.info("compressing... {}", file.getName());
        FileInputStream inputStream = null;
        String entry = parent + file.getName();
        try {
            tarArchive.putArchiveEntry(new TarArchiveEntry(file, entry));
            if (file.isFile()) {
                inputStream = new FileInputStream(file);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

                IOUtils.copy(bufferedInputStream, tarArchive);
                tarArchive.closeArchiveEntry();
                bufferedInputStream.close();
            } else if (file.isDirectory()) {
                tarArchive.closeArchiveEntry();
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        addFileToTar(f.getAbsolutePath(), entry + File.separator, tarArchive);
                    }
                }
            }
        } catch (IOException e) {
            throw new ApmException("failed to compress " + e.getMessage());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    /**
     * Returns file from the package.
     *
     * @param packageId package Id
     * @param file      file/path to search
     * @param extension file extension
     * @return file,
     */
    public File getFileFromPackage(String packageId, String file, String extension) {
        String dir = getLocalIntendedDir(packageId, null);
        String ext;

        List<File> files = (List<File>) FileUtils.listFiles(new File(dir), null, true);
        try {
            for (File f : files) {
                if (f.getCanonicalPath().contains(file)) {
                    ext = getFileExtension(f.getCanonicalPath());
                    if (ext.equals(extension)) {
                        return f;
                    }
                    if (extension.equals("tar")
                            && (ext.equals("tgz") || ext.equals("tar.gz") || ext.equals("tar"))) {
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
        List<String> extensions = Arrays.asList("tar", "tar.gz", "tgz", "gz", "zip", "json", "yaml", "yml");
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
     * @param tenantId  tenantId
     * @return returns local intended dir path
     */
    private String getLocalIntendedDir(String packageId, String tenantId) {
        if (tenantId != null) {
            return localDirPath + File.separator + packageId + tenantId;
        }
        return localDirPath + File.separator + packageId;
    }

    /**
     * Returns edge repository address.
     *
     * @param hostIp      host ip
     * @param accessToken access token
     * @return returns edge repository info
     * @throws ApmException exception if failed to get edge repository details
     */
    public String getRepoInfoOfHost(String hostIp, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL).append("/mechosts/").append(hostIp).toString();

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
     * Gets applcm endpoint from inventory.
     *
     * @param hostIp      host ip
     * @param accessToken access token
     * @return returns edge repository info
     * @throws ApmException exception if failed to get edge repository details
     */
    public String getApplcmCfgOfHost(String hostIp, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL).append("/mechosts/").append(hostIp).toString();

        String response = sendGetRequest(url, accessToken);

        LOGGER.info(EMPTY_RESPONSE, response);

        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        JsonElement applcmIp = jsonObject.get("applcmIp");
        if (applcmIp == null) {
            LOGGER.error(Constants.REPO_INFO_NULL, hostIp);
            throw new ApmException("applcm IP is null for host " + hostIp);
        }

        String ip = applcmIp.getAsString();
        if (!isRegexMatched(Constants.IP_REGEX, ip)) {
            LOGGER.error(Constants.REPO_IP_INVALID, hostIp);
            throw new ApmException("edge repo ip is invalid for host " + hostIp);
        }

        url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL).append("/applcms/").append(ip).toString();
        response = sendGetRequest(url, accessToken);

        LOGGER.info(EMPTY_RESPONSE, response);

        jsonObject = new JsonParser().parse(response).getAsJsonObject();
        JsonElement applcmPort = jsonObject.get("applcmPort");
        if (applcmPort == null) {
            LOGGER.error(Constants.REPO_INFO_NULL, hostIp);
            throw new ApmException("applcm port is null for host " + hostIp);
        }

        String port = applcmPort.getAsString();
        if (!isRegexMatched(Constants.PORT_REGEX, port)) {
            LOGGER.error(Constants.REPO_PORT_INVALID, hostIp);
            throw new ApmException("applcm port is invalid for host " + hostIp);
        }

        return applcmIp.getAsString() + ":" + applcmPort.getAsString();
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
            FileUtils.forceDelete(new File(localFilePath));
        } catch (IOException e) {
            LOGGER.error("failed to delete csar file");
        }
    }

    /**
     * Returns edge repository address.
     *
     * @param appstoreIp  appstore ip
     * @param accessToken access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public AppStore getAppStoreCfgFromInventory(String appstoreIp, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL)
                .append("/appstores/").append(appstoreIp).toString();

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
    public List<AppStore> getAppStoreCfgFromInventory(String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL).append("/appstores").toString();

        String response = sendGetRequest(url, accessToken);

        List<AppStore> appStoreRecords = new LinkedList<>();
        JsonArray appStoreRecs = new JsonParser().parse(response).getAsJsonArray();
        for (JsonElement appStoreRec : appStoreRecs) {
            AppStore appstore = new Gson().fromJson(appStoreRec, AppStore.class);
            appStoreRecords.add(appstore);
        }
        return appStoreRecords;
    }

    /**
     * Returns edge repository address.
     *
     * @param accessToken access token
     * @return returns all appstore configurations
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public List<AppRepo> getAllAppRepoCfgFromInventory(String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL).append("/apprepos").toString();

        List<AppRepo> appRepoRecords = new LinkedList<>();
        try {
            String response = sendGetRequest(url, accessToken);
            JsonArray appRepoRecs = new JsonParser().parse(response).getAsJsonArray();
            for (JsonElement appRepoRec : appRepoRecs) {
                AppRepo apprepo = new Gson().fromJson(appRepoRec, AppRepo.class);
                appRepoRecords.add(apprepo);
            }
        } catch (NoSuchElementException | ApmException ex) {
            LOGGER.info("failed to fetch app source repositories");
        }
        return appRepoRecords;
    }

    /**
     * Returns edge repository address.
     *
     * @param url         URL
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
     * @param url         URL
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
     * @param url         URL
     * @param reqBody     request body
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
     * Returns edge repository address.
     *
     * @param tenantId    tenant ID
     * @param host        repo host
     * @param accessToken access token
     * @return returns  apprepo configurations
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public AppRepo getAppRepoCfgFromInventory(String tenantId, String host, String accessToken) {
        String url = new StringBuilder(Constants.HTTPS_PROTO).append(inventoryIp).append(":")
                .append(inventoryPort).append(INVENTORY_URL).append("/apprepos/").append(host).toString();

        return new Gson().fromJson(sendGetRequest(url, accessToken), AppRepo.class);
    }

    /**
     * Returns application package info.
     *
     * @param appstoreEndpoint appstore endpoint
     * @param accessToken      access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public List<AppPackageInfoDto> getAppPackagesInfoFromAppStore(String appstoreEndpoint, String accessToken) {
        String appsUrl = new StringBuilder(HTTPS).append(appstoreEndpoint)
                .append("/mec/appstore/v1/apps").toString();

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
     * @param appId            app ID
     * @param packageId        package ID
     * @param accessToken      access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    public AppPackageInfoDto getAppPkgInfoFromAppStore(String appstoreEndpoint, String appId,
                                                       String packageId, String accessToken) {
        String appsUrl = new StringBuilder(HTTPS).append(appstoreEndpoint)
                .append("/mec/appstore/v1/apps/").append(appId).append("/packages/").append(packageId).toString();

        String response = sendGetRequest(appsUrl, accessToken);
        LOGGER.info("applications package info response: {}", response);

        return new Gson().fromJson(response, AppPackageInfoDto.class);
    }

    /**
     * Returns application package info.
     *
     * @param appstoreEndpoint appstore endpoint
     * @param accessToken      access token
     * @return returns appstore configuration info
     * @throws ApmException exception if failed to get appstore configuration details
     */
    private List<AppPackageInfoDto> getAppPackagesInfoBasedOnAppId(String appstoreEndpoint, String appId,
                                                                   String accessToken) {
        String appsUrl = new StringBuilder(HTTPS).append(appstoreEndpoint)
                .append("/mec/appstore/v1/apps/").append(appId).append("/packages").toString();

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
     * @param syncInfo      app package sync info
     * @param imageInfoList list of images
     * @param uploadedImgs  uploaded images
     */
    public void uploadAppImage(PkgSyncInfo syncInfo, List<SwImageDescr> imageInfoList,
                               Set<String> uploadedImgs) {

        for (SwImageDescr imageInfo : imageInfoList) {
            LOGGER.info("Docker image to  upload: {}", imageInfo.getSwImage());

            DockerClient dockerClient = getDockerClient(mecmRepoEndpoint, mecmRepoUsername, mecmRepoPassword);

            String[] dockerImageNames = imageInfo.getSwImage().split("/");
            String uploadImgName;
            if (dockerImageNames.length > 1) {
                uploadImgName = new StringBuilder(mecmRepoEndpoint)
                        .append("/mecm/").append(dockerImageNames[dockerImageNames.length - 1]).toString();
            } else {
                uploadImgName = new StringBuilder(mecmRepoEndpoint)
                        .append("/mecm/").append(dockerImageNames[0]).toString();
            }

            LOGGER.info("tagged image upload: {}", uploadImgName);
            String id = dockerClient.inspectImageCmd(imageInfo.getSwImage()).exec().getId();
            dockerClient.tagImageCmd(id, uploadImgName, imageInfo.getVersion()).withForce().exec();

            uploadedImgs.add(uploadImgName);
            try {
                dockerClient.pushImageCmd(uploadImgName)
                        .exec(new PushImageResultCallback()).awaitCompletion();
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
        DockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .build();

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
                LOGGER.debug("docker image {} not found {}", image, ex.getMessage());
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
        DockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withRegistryUsername(mecmRepoUsername)
                .withRegistryPassword(mecmRepoPassword)
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        String id;
        for (String image : imageInfoList) {
            try {
                id = dockerClient.inspectImageCmd(image).exec().getId();
                if (id != null) {
                    LOGGER.debug("delete docker image from repo {}", image);
                }
            } catch (NotFoundException | ConflictException ex) {
                LOGGER.debug("docker image {} not found {}", image, ex.getMessage());
            }
        }
    }
}
