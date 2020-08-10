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
import org.edgegallery.mecm.apm.model.AppPackage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Application package management API handler.
 */
@Api(value = "APM api system")
@Validated
@RequestMapping("/apm/v1")
@RestController
public class ApmHandler {

    // TODO pre authorization & parameter validations

    /**
     * On-boards application package
     *
     * @param tenantId tenant ID
     * @param appPackage application package
     * @return application package identifier on success, error code on failure
     */
    @ApiOperation(value = "Onboard application package", response = Map.class)
    @RequestMapping(value = "/apm/v1/tenants/{tenant_id}/packages", method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> onBoardAppPackage(@PathVariable("tenant_id") String tenantId,
                                                                 @RequestBody AppPackage appPackage) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Retrieves application package information.
     *
     * @param tenantId tenant identifier
     * @param appPackageId application package identifier
     * @return application package on success, error code on failure
     */
    @ApiOperation(value = "Retrieves application package information", response = AppPackage.class)
    @RequestMapping(value = "/tenants/{tenant_id}/packages/{app_package_id}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppPackage> getAppPackageInfo(@PathVariable("tenant_id") String tenantId,
                                                        @PathVariable("app_package_id") String appPackageId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Deletes application package.
     *
     * @param tenantId tenant identifier
     * @param appPackageId application package identifier
     * @return status code 200 on success, error code on failure
     */
    @ApiOperation(value = "Deletes application package", response = String.class)
    @RequestMapping(value = "/tenants/{tenant_id}/packages/{app_package_id}", method = RequestMethod.DELETE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteAppPackage(@PathVariable("tenant_id") String tenantId,
                                                   @PathVariable("app_package_id") String appPackageId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Downloads application package CSAR for a given package identifier.
     *
     * @param tenantId tenant identifier
     * @param appPackageId application package identifier
     * @return application package
     */
    @ApiOperation(value = "Download application package CSAR", response = InputStreamResource.class)
    @RequestMapping(value = "/tenants/{tenant_id}/packages/{app_package_id}/download", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> downloadAppPackage(@PathVariable("tenant_id") String tenantId,
                                                                  @PathVariable("app_package_id") String appPackageId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Retrieves all application packages
     *
     * @param tenantId tenant ID
     * @return application packages
     */
    @ApiOperation(value = "Retrieves all application packages", response = List.class)
    @RequestMapping(value = "/tenants/{tenant_id}/packages", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AppPackage>> getAllAppPackageInfo(@PathVariable("tenant_id") String tenantId) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Deletes application package for a given host
     *
     * @param tenantId tenant ID
     * @param appPackageId application package identifier which needs to be deleted
     * @param hostIp host IP for which package needs to be deleted
     * @return status code 200 on success, error code on failure
     */
    @ApiOperation(value = "Deletes an application packages", response = String.class)
    @RequestMapping(value = "/tenants/{tenant_id}/packages/{app_package_id}/hosts/{host_ip}",
            method = RequestMethod.DELETE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteAppPackageInHost(@PathVariable("tenant_id") String tenantId,
                                                         @PathVariable("app_package_id") String appPackageId,
                                                         @PathVariable("host_ip") String hostIp) {
        // TODO: implementation
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
