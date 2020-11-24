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

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.generateAppId;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getHostList;
import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.saveMultipartFile;
import static org.edgegallery.mecm.apm.utils.Constants.APP_PKG_ID_REGX;
import static org.edgegallery.mecm.apm.utils.Constants.HOST_IP_REGX;
import static org.edgegallery.mecm.apm.utils.Constants.TENENT_ID_REGEX;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.service.ApmServiceFacade;
import org.edgegallery.mecm.apm.utils.Constants;
import org.hibernate.validator.constraints.Length;
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
     * @param accessToken access token
     * @param tenantId tenant ID
     * @param appPackageName application package name
     * @param appPkgVersion application package version
     * @param hostList list of host
     * @param file CSAR package
     * @return application package identifier on success, error code on failure
     */
    @ApiOperation(value = "Onboard application package", response = Map.class)
    @PostMapping(path = "/tenants/{tenant_id}/packages/upload",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT')")
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
        String appPkgId = generateAppId();
        dto.setAppPkgId(appPkgId);
        String appId = generateAppId();
        dto.setAppId(appId);
        dto.setMecHostInfo(getHostList(hostList));

        String localFilePath = saveMultipartFile(file, appPkgId, tenantId, localDirPath);
        service.onboardApplication(accessToken, tenantId, dto, localFilePath);

        Map<String, String> response = new HashMap<>();
        response.put("appPackageId", appPkgId);
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
    @PreAuthorize("hasRole('MECM_TENANT')")
    public ResponseEntity<Map<String, String>> onBoardAppPackage(
            @RequestHeader("access_token") String accessToken,
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @Valid @ApiParam(value = "app package info") @RequestBody AppPackageDto appPackageDto) {
        if (appPackageDto.getAppPkgPath() == null) {
            throw new IllegalArgumentException("App Package Path is null");
        }

        service.onboardApplication(accessToken, tenantId, appPackageDto);

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
    @PreAuthorize("hasRole('MECM_TENANT')")
    public ResponseEntity<AppPackageDto> getAppPackageInfo(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APP_PKG_ID_REGX) String appPackageId) {
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
    @PreAuthorize("hasRole('MECM_TENANT')")
    public ResponseEntity<String> deleteAppPackage(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APP_PKG_ID_REGX) String appPackageId) {
        service.deleteAppPackage(tenantId, appPackageId);
        return new ResponseEntity<>("success", HttpStatus.OK);
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
    @PreAuthorize("hasRole('MECM_TENANT')")
    public ResponseEntity<InputStreamResource> downloadAppPackage(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APP_PKG_ID_REGX) String appPackageId) {
        InputStream resource = service.getAppPackageFile(tenantId, appPackageId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/octet-stream");
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
    @PreAuthorize("hasRole('MECM_TENANT')")
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
    @PreAuthorize("hasRole('MECM_TENANT')")
    public ResponseEntity<String> deleteAppPackageInHost(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = TENENT_ID_REGEX) String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id")
            @Size(max = Constants.MAX_COMMON_ID_LENGTH) @Pattern(regexp = APP_PKG_ID_REGX) String appPackageId,
            @ApiParam(value = "host ip") @PathVariable("host_ip")
            @Size(max = Constants.MAX_COMMON_IP_LENGTH) @Pattern(regexp = HOST_IP_REGX) String hostIp) {
        service.deleteAppPackageInHost(tenantId, appPackageId, hostIp);
        return new ResponseEntity<>("success", HttpStatus.OK);
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
}
