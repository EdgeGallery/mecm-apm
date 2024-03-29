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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import org.edgegallery.mecm.apm.service.ApmServiceFacade;
import org.edgegallery.mecm.apm.service.DbService;
import org.edgegallery.mecm.apm.service.RestServiceImpl;
import org.edgegallery.mecm.apm.utils.Constants;
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
            Set<String> mepms = getInventoryMecHostsCfg(tenantId, accessToken);
            for (String mepm: mepms) {
                LOGGER.info("Sync application package infos from edge {}", mepm);
                String appLcmEndPoint = getInventoryMepmCfg(mepm, accessToken);

                getSyncPackageStaleRecords(appLcmEndPoint, tenantId, accessToken);
                getSyncPackageUpdateRecords(appLcmEndPoint, tenantId, accessToken);
            }
        } catch (ApmException ex) {
            throw new ApmException("failed to synchronize app package management data from edge:" + ex.getMessage());
        }
    }

    private void getSyncPackageUpdateRecords(String appLcmEndPoint, String tenantId, String accessToken) {
        try {
            String uri = new StringBuilder(appLcmEndPoint).append("/lcmcontroller/v2")
                    .append("/tenants/").append(tenantId).append("/packages/sync_updated").toString();

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
            String uri = new StringBuilder(appLcmEndPoint).append("/lcmcontroller/v2").append("/tenants/")
                    .append(tenantId).append("/packages/sync_deleted").toString();

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
     * Gets MEPM endpoint from inventory.
     *
     * @param hostIp      host ip
     * @param accessToken access token
     * @return returns MEPM configuration data
     * @throws ApmException exception if failed to get MEPM config details
     */
    private String getInventoryMepmCfg(String hostIp, String accessToken) {

        String url = new StringBuilder(inventoryService).append(":")
                .append(inventoryServicePort).append("/inventory/v1").append("/mepms/").append(hostIp).toString();

        ResponseEntity<String> response = syncService.sendRequest(url, HttpMethod.GET, accessToken, null);

        LOGGER.info("response: {}", response);

        JsonObject jsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
        JsonElement mepmPort = jsonObject.get("mepmPort");
        if (mepmPort == null) {
            throw new ApmException("MEPM port is null for host " + hostIp);
        }

        return hostIp + ":" + mepmPort.getAsString();
    }

    /**
     * Gets MEPM configurations from inventory.
     *
     * @param tenantId tenant ID
     * @param accessToken access token
     * @return returns MEPM configurations
     * @throws ApmException exception if failed to get edge repository details
     */
    private Set<String> getInventoryMecHostsCfg(String tenantId, String accessToken) {

        String url = new StringBuilder(inventoryService).append(":")
                .append(inventoryServicePort).append("/inventory/v1").append("/tenants/").append(tenantId)
                .append("/mechosts/").toString();

        ResponseEntity<String> response = syncService.sendRequest(url, HttpMethod.GET, accessToken, null);

        LOGGER.info("response: {}", response);
        JsonArray jsonArray = new JsonParser().parse(response.getBody()).getAsJsonArray();

        Set<String> mepms = new HashSet<>();
        String mepm;
        for (JsonElement host: jsonArray) {
            mepm = host.getAsJsonObject().get("mepmIp").getAsString();
            mepms.add(mepm);
        }

        return mepms;
    }

    private Map<String, String> getInventoryMecHostsCfgforHost(String tenantId, String accessToken, String vim) {

        String url = new StringBuilder(inventoryService).append(":")
                .append(inventoryServicePort).append("/inventory/v1").append("/tenants/").append(tenantId)
                .append("/mechosts/").toString();

        ResponseEntity<String> response = syncService.sendRequest(url, HttpMethod.GET, accessToken, null);

        LOGGER.info("response: {}", response);
        JsonArray jsonArray = new JsonParser().parse(response.getBody()).getAsJsonArray();

        Map<String, String> hostConfig = new HashMap<>();
        String mecHost;
        String mepm;
        for (JsonElement host: jsonArray) {
            mepm = host.getAsJsonObject().get("mepmIp").getAsString();
            mecHost = host.getAsJsonObject().get("mechostIp").getAsString();
            if (host.getAsJsonObject().get("vim").getAsString().equalsIgnoreCase(vim)) {
                hostConfig.put(mecHost, mepm);
            }
        }
        return hostConfig;
    }

    /**
     * queryKpi.
     */
    public Map<String, String> queryKpi(String tenantId, String accessToken, String vim) {

        Map<String, String> listData = new HashMap<>();
        try {

            Map<String, String> hostConfigs = getInventoryMecHostsCfgforHost(tenantId, accessToken, vim);
            Set<Map.Entry<String, String>> entrySet = hostConfigs.entrySet();

            for (Map.Entry<String, String> hostConfig : entrySet) {
                LOGGER.info("Query kpi details from edge {}", hostConfig.getKey());
                String appLcmEndPoint = getInventoryMepmCfg(hostConfig.getValue(), accessToken);
                String url = null;
                JsonObject jsonObject;

                try {
                    url = new StringBuilder(appLcmEndPoint).append("/lcmcontroller/v2").append("/tenants/")
                            .append(tenantId).append(Constants.HOSTS).append(hostConfig.getKey() + "/kpi").toString();
                    ResponseEntity<String> response = syncService.sendRequest(url, HttpMethod.GET, accessToken, null);
                    jsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
                    JsonObject data = jsonObject.get("data").getAsJsonObject();
                    LOGGER.info("data: {}", data);
                    String integerMap = getRemainResourceInfo(data);
                    listData.put(hostConfig.getKey(), integerMap);
                } catch (ApmException ex) {
                    LOGGER.info("couldn't get response from this {}", url);
                }
            }
        } catch (ApmException ex) {
            throw new ApmException("failed to synchronize app package management data from edge:" + ex.getMessage());
        } catch (Exception ex) {
            throw new ApmException("failed to synchronize app package management data from edge:" + ex.getMessage());
        }
        LOGGER.info("Final Query Kpi details: {}", listData);
        return listData;

    }

    /**
     * getRemainResourceInfo.
     */
    public String getRemainResourceInfo(JsonObject data) {

        Map<String, Integer> remain = new HashMap<>();

        remain.put("virtual_cpu_total", data.get("virtual_cpu_total").getAsInt());
        remain.put("virtual_cpu_used", data.get("virtual_cpu_used").getAsInt());
        remain.put("virtual_mem_total", data.get("virtual_mem_total").getAsInt());
        remain.put("virtual_mem_used", data.get("virtual_mem_used").getAsInt());
        remain.put("virtual_local_storage_total", data.get("virtual_local_storage_total").getAsInt());
        remain.put("virtual_local_storage_used", data.get("virtual_local_storage_used").getAsInt());

        return remain.toString();
    }
}
