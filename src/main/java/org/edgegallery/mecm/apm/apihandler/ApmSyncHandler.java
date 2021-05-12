/*
 *  Copyright 2021 Huawei Technologies Co., Ltd.
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

import static org.edgegallery.mecm.apm.utils.Constants.TENENT_ID_REGEX;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.dto.AppPackageDeletedDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageHostDeletedDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageRecordDto;
import org.edgegallery.mecm.apm.model.dto.SyncDeletedAppPackageDto;
import org.edgegallery.mecm.apm.model.dto.SyncUpdatedAppPackageDto;
import org.edgegallery.mecm.apm.service.DbService;
import org.edgegallery.mecm.apm.service.RestServiceImpl;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apm sync API handler.
 */
@RestSchema(schemaId = "apm-sync")
@Api(value = "Application instance info api system")
@Validated
@RequestMapping("/apm/v1")
@RestController
public class ApmSyncHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApmSyncHandler.class);

    @Value("${apm.inventory-endpoint}")
    private String inventoryService;

    @Value("${apm.inventory-port}")
    private String inventoryServicePort;

    @Autowired
    private RestServiceImpl syncService;

    @Autowired
    private DbService dbService;

    /**
     * Synchronizes application package management information from all edges.
     *
     * @param accessToken   access token
     * @param tenantId       tenant ID
     */
    @ApiOperation(value = "Synchronizes application package management information from all edges.",
            response = String.class)
    @PostMapping(value = "/tenants/{tenant_id}/app_package_infos/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MECM_TENANT') || hasRole('MECM_ADMIN')")
    public ResponseEntity<String> syncAppPackageInfos(@ApiParam(value = "access token")
                                                      @RequestHeader("access_token") String accessToken,
                                                      @ApiParam(value = "tenant id") @PathVariable("tenant_id")
                                                      @Pattern(regexp = TENENT_ID_REGEX)
                                                      @Size(max = 64) String tenantId) {
        synchronizePackageMgmtDataFromEdges(tenantId, accessToken);
        return new ResponseEntity<>("accepted", HttpStatus.ACCEPTED);
    }

    @Async
    private void synchronizePackageMgmtDataFromEdges(String tenantId,  String accessToken) {
        LOGGER.info("Sync application package from edge");
        try {
            Set<String> applcms = getInventoryMecHostsCfg(accessToken);
            for (String applcm: applcms) {
                LOGGER.info("Sync application package infos from edge {}", applcm);
                String appLcmEndPoint = getInventoryApplcmCfg(applcm, accessToken);

                getSyncPackageStaleRecords(appLcmEndPoint, tenantId, accessToken);
                getSyncPackageUpdateRecords(appLcmEndPoint, tenantId, accessToken);
            }
        } catch (ApmException ex) {
            throw new ApmException("failed to synchronize app package management data from edge:" + ex.getMessage());
        }
    }

    private void getSyncPackageUpdateRecords(String appLcmEndPoint, String tenantId, String accessToken) {
        try {
            String uri = appLcmEndPoint + "/lcmcontroller/v1" + "/tenants/" + tenantId + "/packages/sync_updated";

            ResponseEntity<SyncUpdatedAppPackageDto> updateResponse = syncService.syncRecords(uri,
                    SyncUpdatedAppPackageDto.class, accessToken);
            SyncUpdatedAppPackageDto syncUpdatedAppPkgDto = updateResponse.getBody();
            // Update table
            if (syncUpdatedAppPkgDto != null && syncUpdatedAppPkgDto.getAppPackageRecord() != null) {
                for (AppPackageRecordDto updatedRecord : syncUpdatedAppPkgDto.getAppPackageRecord()) {
                    updateSyncAppPackageRecords(tenantId, updatedRecord);
                }
            }
        } catch (NoSuchElementException ex) {
            LOGGER.error("failed to sync records {}", ex.getMessage());
        }
    }

    private void getSyncPackageStaleRecords(String appLcmEndPoint, String tenantId, String accessToken) {
        try {
            String uri = appLcmEndPoint + "/lcmcontroller/v1" + "/tenants/" + tenantId + "/packages/sync_deleted";

            ResponseEntity<SyncDeletedAppPackageDto> updateResponse = syncService.syncRecords(uri,
                    SyncDeletedAppPackageDto.class, accessToken);
            SyncDeletedAppPackageDto syncDeletedAppPkgDto = updateResponse.getBody();
            // Update table
            if (syncDeletedAppPkgDto != null && syncDeletedAppPkgDto.getAppPackageStaleRec() != null) {
                for (AppPackageDeletedDto deletedRecord : syncDeletedAppPkgDto.getAppPackageStaleRec()) {
                    deleteSyncAppPackageRecords(tenantId, deletedRecord);
                }
            }

            if (syncDeletedAppPkgDto != null && syncDeletedAppPkgDto.getAppPackageHostStaleRec() != null) {
                for (AppPackageHostDeletedDto deletedHostRecord : syncDeletedAppPkgDto.getAppPackageHostStaleRec()) {
                    deleteSyncAppPackageHostRecords(tenantId, deletedHostRecord);
                }
            }
        } catch (ApmException ex) {
            LOGGER.error("failed to sync records {}", ex.getMessage());
        }
    }

    private void updateSyncAppPackageRecords(String tenantId, AppPackageRecordDto updatedRecord) {
        ModelMapper mapper = new ModelMapper();
        updatedRecord.setAppPkgId(updatedRecord.getPackageId());
        try {
            dbService.getAppPackageWithHost(tenantId, updatedRecord.getPackageId());

            // record already exist update it
            AppPackageDto appPackageDto = mapper.map(updatedRecord, AppPackageDto.class);
            dbService.updateAppPackage(tenantId, appPackageDto);

            dbService.createHost(tenantId, appPackageDto);

        } catch (IllegalArgumentException e) {

            //record does not exist add new
            AppPackageDto appPackageDto = mapper.map(updatedRecord, AppPackageDto.class);

            dbService.createAppPackage(tenantId, appPackageDto);
            dbService.createHost(tenantId, appPackageDto);
        }
    }

    private void deleteSyncAppPackageRecords(String tenantId, AppPackageDeletedDto deletedRecord) {
        try {
            dbService.deleteAppPackage(tenantId, deletedRecord.getAppPackageId());
            dbService.deleteHost(tenantId, deletedRecord.getAppPackageId());
        } catch (NoSuchElementException e) {
            LOGGER.error("app package does not exist to delete");
        }
    }

    private void deleteSyncAppPackageHostRecords(String tenantId, AppPackageHostDeletedDto deletedRecord) {
        try {
            dbService.deleteHostWithIp(tenantId, deletedRecord.getPackageId(), deletedRecord.getHostIp());
        } catch (NoSuchElementException e) {
            LOGGER.error("app package host does not exist to delete, package {}, host {}",
                    deletedRecord.getPackageId(), deletedRecord.getHostIp());
        }
    }

    /**
     * Gets applcm endpoint from inventory.
     *
     * @param hostIp      host ip
     * @param accessToken access token
     * @return returns edge repository info
     * @throws ApmException exception if failed to get edge repository details
     */
    private String getInventoryApplcmCfg(String hostIp, String accessToken) {

        String url = new StringBuilder(inventoryService).append(":")
                .append(inventoryServicePort).append("/inventory/v1").append("/applcms/").append(hostIp).toString();

        ResponseEntity<String> response = syncService.sendRequest(url, HttpMethod.GET, accessToken, null);

        LOGGER.info("response: {}", response);

        JsonObject jsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
        JsonElement applcmPort = jsonObject.get("applcmPort");
        if (applcmPort == null) {
            throw new ApmException("applcm port is null for host " + hostIp);
        }

        return hostIp + ":" + applcmPort.getAsString();
    }

    /**
     * Gets applcm configurations from inventory.
     *
     * @param accessToken access token
     * @return returns applcms
     * @throws ApmException exception if failed to get edge repository details
     */
    private Set<String> getInventoryMecHostsCfg(String accessToken) {

        String url = new StringBuilder(inventoryService).append(":")
                .append(inventoryServicePort).append("/inventory/v1").append("/mechosts/").toString();

        ResponseEntity<String> response = syncService.sendRequest(url, HttpMethod.GET, accessToken, null);

        LOGGER.info("response: {}", response);
        JsonArray jsonArray = new JsonParser().parse(response.getBody()).getAsJsonArray();

        Set<String> applcms = new HashSet<>();
        String applcm;
        for (JsonElement host: jsonArray) {
            applcm = host.getAsJsonObject().get("applcmIp").getAsString();
            applcms.add(applcm);
        }

        return applcms;
    }
}
