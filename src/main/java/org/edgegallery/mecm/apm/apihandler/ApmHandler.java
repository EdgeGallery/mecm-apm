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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application package management API handler.
 */
@Api(tags = {"APM api system"})
@Validated
@RequestMapping("/apm/v1")
@RestController
public class ApmHandler {

    // TODO pre authorization & parameter validations

    /**
     * On-boards application package.
     *
     * @param tenantId   tenant ID
     * @param appPackageDto application package
     * @return application package identifier on success, error code on failure
     */
    @ApiOperation(value = "Onboard application package", response = Map.class)
    @PostMapping(path = "/apm/v1/tenants/{tenant_id}/packages",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> onBoardAppPackage(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id") String tenantId,
            @Valid @ApiParam(value = "app package info") @RequestBody AppPackageDto appPackageDto) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
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
    public ResponseEntity<AppPackageDto> getAppPackageInfo(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id") String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id") String appPackageId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
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
    public ResponseEntity<String> deleteAppPackage(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id") String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id") String appPackageId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
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
    public ResponseEntity<InputStreamResource> downloadAppPackage(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id") String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id") String appPackageId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
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
    public ResponseEntity<List<AppPackageDto>> getAllAppPackageInfo(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id") String tenantId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
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
    public ResponseEntity<String> deleteAppPackageInHost(
            @ApiParam(value = "tenant id") @PathVariable("tenant_id") String tenantId,
            @ApiParam(value = "app package id") @PathVariable("app_package_id") String appPackageId,
            @ApiParam(value = "host ip") @PathVariable("host_ip") String hostIp) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
