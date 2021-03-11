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

package org.edgegallery.mecm.apm.apihandler;

import static org.edgegallery.mecm.apm.utils.Constants.APPD_ID_PKG_ID_REGEX;
import static org.edgegallery.mecm.apm.utils.Constants.APP_PKG_ID_REGX;
import static org.edgegallery.mecm.apm.utils.Constants.HOST_IP_REGX;
import static org.edgegallery.mecm.apm.utils.Constants.TENENT_ID_REGEX;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackageInfo;
import org.edgegallery.mecm.apm.model.AppPackageSyncInfo;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.AppStore;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageInfoDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageSyncStatusDto;
import org.edgegallery.mecm.apm.model.dto.SyncAppPackageDto;
import org.edgegallery.mecm.apm.service.ApmServiceFacade;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.edgegallery.mecm.apm.utils.Constants;
import org.hibernate.validator.constraints.Length;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application package management API handler.
 */
@Api(tags = {"APM api system"})
@Validated
@RequestMapping("/apm/v1")
@RestController
public class ApmHandler {

    @Autowired
    private ApmServiceFacade service;

    @Value("${apm.package-dir:/usr/app/packages}")
    private String localDirPath;

    /**
     * On-boards application with package provided.
     *
     * @param accessToken    access token
     * @param tenantId       tenant ID
     * @param appPackageName application package name
     * @param appPkgVersion  application package version
     * @param hostList       list of host
     * @param file           CSAR package
     * @return application package identifier on success, error code on failure
     */
    @ApiOperation(value = "Onboard application package", response = Map.class)
    @PostMapping(path = "/tenants/{tenant_id}/packages/upload",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<Map<String, String>> onBoardApplication(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @RequestParam("appPackageName") @Pattern(regexp = Constants.APP_NAME_REGEX) String appPackageName,
            @RequestParam("appPackageVersion") @Length(max = Constants.MAX_COMMON_STRING_LENGTH) String appPkgVersion,
            @RequestParam("hostList") @NotNull @Length(max = Constants.MAX_COMMON_STRING_LENGTH) String hostList,
            @ApiParam(value = "app package") @RequestPart MultipartFile file) {

        AppPackageDto dto = new AppPackageDto();
        dto.setAppPkgName(appPackageName);
        dto.setAppPkgVersion(appPkgVersion);
        String appPkgId = ApmServiceHelper.generateAppId();
        dto.setAppPkgId(appPkgId);
        String appId = ApmServiceHelper.generateAppId();
        dto.setAppId(appId);
        dto.setMecHostInfo(ApmServiceHelper.getHostList(hostList));
        dto.setAppPkgId(appId + appPkgId);

        PkgSyncInfo syncAppPkg = new PkgSyncInfo();
        syncAppPkg.setAppstoreIp("-");
        syncAppPkg.setAppId(appId);
        syncAppPkg.setPackageId(appPkgId);

        service.createAppPackageEntryInDb(tenantId, dto);

        List<AppRepo> appRepos = service.getAllAppRepoConfig(accessToken);
        Map<String, AppRepo> repoInfo = new HashMap<>();
        for (AppRepo appRepo : appRepos) {
            repoInfo.put(appRepo.getRepoEndPoint(), appRepo);
        }
        syncAppPkg.setRepoInfo(repoInfo);

        String localFilePath = ApmServiceHelper.saveMultipartFile(file, dto.getAppPkgId(), null, localDirPath);
        service.onboardApplication(accessToken, tenantId, dto, localFilePath, syncAppPkg);

        Map<String, String> response = new HashMap<>();
        response.put("appPackageId", appId + appPkgId);
        response.put("appId", appId);
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    /**
     * On-boards application package by downloading package from appstore.
     *
     * @param tenantId      tenant ID
     * @param appPackageDto application package
     * @return application package identifier on success, error code on failure
     */
    @ApiOperation(value = "Onboard application package", response = Map.class)
    @PostMapping(path = "/tenants/{tenant_id}/packages",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<Map<String, String>> onBoardAppPackage(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @Valid @ApiParam(value = "app package info") @RequestBody AppPackageDto appPackageDto) {
        if (appPackageDto.getAppPkgPath() == null) {
            throw new IllegalArgumentException("App Package Path is null");
        }
        AppStore appStore;
        try {
            URL appRepoUrl = new URL(appPackageDto.getAppPkgPath());
            appStore = service.getAppstoreConfig(appRepoUrl.getHost(), accessToken);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }

        PkgSyncInfo syncAppPkg = new PkgSyncInfo();
        syncAppPkg.setAppstoreIp(appStore.getAppstoreIp());
        syncAppPkg.setAppstorePort(appStore.getAppstorePort());
        syncAppPkg.setAppId(appPackageDto.getAppId());
        syncAppPkg.setPackageId(appPackageDto.getAppPkgId());

        List<AppRepo> appRepos = service.getAllAppRepoConfig(accessToken);
        Map<String, AppRepo> repoInfo = new HashMap<>();
        for (AppRepo appRepo : appRepos) {
            repoInfo.put(appRepo.getRepoEndPoint(), appRepo);
        }
        AppRepo appRepo = new AppRepo();
        appRepo.setRepoEndPoint(appStore.getAppstoreRepo());
        appRepo.setRepoUserName(appStore.getAppstoreRepoUserName());
        appRepo.setRepoPassword(appStore.getAppstoreRepoPassword());
        repoInfo.put(appStore.getAppstoreRepo(), appRepo);

        syncAppPkg.setRepoInfo(repoInfo);

        appPackageDto.setAppPkgId(appPackageDto.getAppId() + appPackageDto.getAppPkgId());
        service.createAppPackageEntryInDb(tenantId, appPackageDto);
        service.onboardApplication(accessToken, tenantId, appPackageDto, syncAppPkg);

        Map<String, String> response = new HashMap<>();
        response.put("packageId", appPackageDto.getAppPkgId());
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    /**
     * Retrieves application package information.
     *
     * @param tenantId     tenant identifier
     * @param appPackageId application package identifier
     * @return application package on success, error code on failure
     */
    @ApiOperation(value = "Retrieves application package information", response = AppPackageDto.class)
    @GetMapping(path = "/tenants/{tenant_id}/packages/{app_package_id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN') || hasRole('MECM_GUEST')")
    public ResponseEntity<AppPackageDto> getAppPackageInfo(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APPD_ID_PKG_ID_REGEX) String appPackageId) {
        AppPackageDto response = service.getAppPackageInfo(tenantId, appPackageId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Deletes application package.
     *
     * @param tenantId     tenant identifier
     * @param appPackageId application package identifier
     * @return status code 200 on success, error code on failure
     */
    @ApiOperation(value = "Deletes application package", response = String.class)
    @DeleteMapping(path = "/tenants/{tenant_id}/packages/{app_package_id}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<String> deleteAppPackage(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APPD_ID_PKG_ID_REGEX) String appPackageId) {

        List<String> hosts = service.deleteAppPackage(tenantId, appPackageId);
        for (String host : hosts) {
            service.deleteAppPackageOnHost(tenantId, host, appPackageId, accessToken);
        }
        return new ResponseEntity<>(Constants.SUCCESS, HttpStatus.OK);
    }

    /**
     * Downloads application package CSAR for a given package identifier.
     *
     * @param tenantId     tenant identifier
     * @param appPackageId application package identifier
     * @return application package
     */
    @ApiOperation(value = "Download application package CSAR", response = InputStreamResource.class)
    @GetMapping(path = "/tenants/{tenant_id}/packages/{app_package_id}/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<InputStreamResource> downloadAppPackage(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APPD_ID_PKG_ID_REGEX) String appPackageId) {
        InputStream resource = service.getAppPackageFile(tenantId, appPackageId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return new ResponseEntity<>(new InputStreamResource(resource), headers, HttpStatus.OK);
    }

    /**
     * Retrieves all application packages.
     *
     * @param tenantId tenant ID
     * @return application packages
     */
    @ApiOperation(value = "Retrieves all application packages", response = List.class)
    @GetMapping(path = "/tenants/{tenant_id}/packages",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN') || hasRole('MECM_GUEST')")
    public ResponseEntity<List<AppPackageDto>> getAllAppPackageInfo(
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Pattern(regexp = TENENT_ID_REGEX) String tenantId) {
        List<AppPackageDto> response = service.getAllAppPackageInfo(tenantId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Deletes application package for a given host.
     *
     * @param tenantId     tenant ID
     * @param appPackageId application package identifier which needs to be deleted
     * @param hostIp       host IP for which package needs to be deleted
     * @return status code 200 on success, error code on failure
     */
    @ApiOperation(value = "Deletes an application packages", response = String.class)
    @DeleteMapping(path = "/tenants/{tenant_id}/packages/{app_package_id}/hosts/{host_ip}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<String> deleteAppPackageInHost(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APPD_ID_PKG_ID_REGEX) String appPackageId,
            @ApiParam(value = "host ip") @PathVariable("host_ip")
            @Size(max = Constants.MAX_COMMON_IP_LENGTH) @Pattern(regexp = HOST_IP_REGX) String hostIp) {

        service.deleteAppPackageOnHost(tenantId, hostIp, appPackageId, accessToken);

        service.deleteAppPackageInHost(tenantId, appPackageId, hostIp);
        return new ResponseEntity<>(Constants.SUCCESS, HttpStatus.OK);
    }

    /**
     * Queries liveness & readiness.
     *
     * @return status code 200 when ready
     */
    @ApiOperation(value = "Queries liveness and readiness", response = String.class)
    @GetMapping(path = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> healthCheck() {
        return new ResponseEntity<>("ok", HttpStatus.OK);
    }

    /**
     * Retrieves all application packages info from app store.
     *
     * @param appstoreIp application store IP
     * @return application packages
     */
    @ApiOperation(value = "Retrieves all application packages info from app store", response = List.class)
    @GetMapping(path = "/apps/info/appstores/{appstore_ip}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN') || hasRole('MECM_GUEST')")
    public ResponseEntity<List<AppPackageInfoDto>> getAllAppPackageInfoFromAppStore(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "appstore ip") @PathVariable("appstore_ip")
            @Size(max = Constants.MAX_COMMON_IP_LENGTH) @Pattern(regexp = HOST_IP_REGX) String appstoreIp) {

        AppStore appstore = service.getAppstoreConfig(appstoreIp, accessToken);
        String appstoreEndPoint = appstore.getAppstoreIp() + ":" + appstore.getAppstorePort();
        List<AppPackageInfoDto> apps = null;
        try {
            apps = service.getAppPackagesInfo(appstoreEndPoint, accessToken);
        } catch (NoSuchElementException ex) {
            return new ResponseEntity<>(apps, HttpStatus.NOT_FOUND);
        }

        service.deleteNonExistingPackages(appstoreIp, apps);

        return new ResponseEntity<>(apps, HttpStatus.OK);
    }

    /**
     * Sync application package by downloading package from appstore.
     *
     * @param syncAppPackageDtos sync application packages
     * @return http status code
     */
    @ApiOperation(value = "Sync application packages", response = List.class)
    @PostMapping(path = "/apps/sync",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<List<Map<String, String>>> syncApplicationPackages(
            @RequestHeader("access_token") String accessToken,
            @Valid @ApiParam(value = "sync app package info") @Size(min = 1, max = 400)
            @RequestBody Set<SyncAppPackageDto> syncAppPackageDtos) {

        List<Map<String, String>> responseList = new LinkedList<>();
        List<PkgSyncInfo> syncAppPkgs;
        Map<String, AppRepo> repoInfo = new HashMap<>();

        syncAppPkgs = syncAppPackageProcessInput(syncAppPackageDtos, repoInfo, responseList, accessToken);
        if (syncAppPkgs.isEmpty()) {
            return new ResponseEntity<>(responseList, HttpStatus.BAD_REQUEST);
        }

        AppPackageSyncInfo appPkgSyncInfo = new AppPackageSyncInfo();
        appPkgSyncInfo.setSyncInfo(syncAppPkgs);
        List<AppRepo> appRepos = service.getAllAppRepoConfig(accessToken);

        for (AppRepo appRepo : appRepos) {
            repoInfo.put(appRepo.getRepoEndPoint(), appRepo);
        }
        appPkgSyncInfo.setRepoInfo(repoInfo);
        service.syncApplicationPackages(accessToken, appPkgSyncInfo);

        return new ResponseEntity<>(responseList, HttpStatus.ACCEPTED);
    }

    private List<PkgSyncInfo> syncAppPackageProcessInput(Set<SyncAppPackageDto> syncAppPackageDtos,
                                                         Map<String, AppRepo> repoInfo,
                                                         List<Map<String, String>> responseList,
                                                         String accessToken) {
        List<PkgSyncInfo> syncAppPkgs = new LinkedList<>();
        Map<String, AppStore> appstoreCfgs = new HashMap<>();
        ModelMapper mapper = new ModelMapper();
        AppStore appstore;
        boolean isValidInput;

        for (SyncAppPackageDto syncApp : syncAppPackageDtos) {
            isValidInput = true;
            Map<String, String> response = new HashMap<>();
            response.put("appId", syncApp.getAppId());
            response.put("packageId", syncApp.getPackageId());

            if (!appstoreCfgs.containsKey(syncApp.getAppstoreIp())) {
                appstore = service.getAppstoreConfig(syncApp.getAppstoreIp(), accessToken);
                appstoreCfgs.put(syncApp.getAppstoreIp(), appstore);

                AppRepo appRepo = new AppRepo();
                appRepo.setRepoEndPoint(appstore.getAppstoreRepo());
                appRepo.setRepoUserName(appstore.getAppstoreRepoUserName());
                appRepo.setRepoPassword(appstore.getAppstoreRepoPassword());
                repoInfo.put(appstore.getAppstoreRepo(), appRepo);
            } else {
                appstore = appstoreCfgs.get(syncApp.getAppstoreIp());
            }

            try {
                String key = syncApp.getAppId() + syncApp.getPackageId();
                AppPackageInfoDto appPkgInfoDto =
                        service.getAppPackageInfoFromAppStore(
                                appstore.getAppstoreIp() + ":" + appstore.getAppstorePort(),
                                syncApp.getAppId(), syncApp.getPackageId(), accessToken);

                if (service.isAppPackageInfoExistInDB(key)) {
                    AppPackageInfo appPkgInfo = service.getAppPackageInfoDB(key);
                    if (Constants.APP_SYNC_INPROGRESS.equals(appPkgInfo.getSyncStatus())) {
                        response.put(Constants.STATUS, "failed");
                        response.put("reason", Constants.APP_SYNC_INPROGRESS);
                        responseList.add(response);
                        isValidInput = false;
                    }
                } else {
                    service.addAppPackageInfoDB(appstore.getAppstoreIp(), appPkgInfoDto);
                }
            } catch (NoSuchElementException | ApmException ex) {
                response.put(Constants.STATUS, "failed");
                response.put("reason", ex.getMessage());
                responseList.add(response);
                isValidInput = false;
            }

            if (!isValidInput) {
                continue;
            }
            response.put(Constants.STATUS, "accepted");
            responseList.add(response);

            PkgSyncInfo pkgSyncInfo = mapper.map(syncApp, PkgSyncInfo.class);
            pkgSyncInfo.setAppstoreIp(syncApp.getAppstoreIp());
            pkgSyncInfo.setAppstorePort(appstore.getAppstorePort());
            syncAppPkgs.add(pkgSyncInfo);
        }
        return syncAppPkgs;
    }

    /**
     * Retrieves all application packages sync status.
     *
     * @return application packages info
     */
    @ApiOperation(value = "Retrieves all application packages sync status", response = List.class)
    @GetMapping(path = "/apps/syncstatus",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN') || hasRole('MECM_GUEST')")
    public ResponseEntity<List<AppPackageSyncStatusDto>> getAllAppPackageSyncStatus(
            @RequestHeader("access_token") String accessToken) {

        List<AppPackageSyncStatusDto> response = new LinkedList<>();
        List<AppPackageInfo> appPkgInfos = service.getAppPackageInfoDB();
        for (AppPackageInfo appPkgInfo : appPkgInfos) {
            ModelMapper mapper = new ModelMapper();
            AppPackageSyncStatusDto statusInfoDto = mapper.map(appPkgInfo, AppPackageSyncStatusDto.class);
            response.add(statusInfoDto);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Retrieve application packages sync status.
     *
     * @return application packages info
     */
    @ApiOperation(value = "Retrieve  application packages sync status", response = AppPackageSyncStatusDto.class)
    @GetMapping(path = "/apps/{app_id}/packages/{package_id}/syncstatus",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN') || hasRole('MECM_GUEST')")
    public ResponseEntity<AppPackageSyncStatusDto> getAllAppPackageSyncStatus(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "app id") @PathVariable("app_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APP_PKG_ID_REGX) String appId,
            @ApiParam(value = "app package id") @PathVariable("package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APP_PKG_ID_REGX) String packageId) {

        AppPackageInfo statusInfo = service.getAppPackageInfoDB(appId + packageId);
        ModelMapper mapper = new ModelMapper();
        AppPackageSyncStatusDto statusInfoDto = mapper.map(statusInfo, AppPackageSyncStatusDto.class);

        return new ResponseEntity<>(statusInfoDto, HttpStatus.OK);
    }
}
