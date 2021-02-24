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

import static org.edgegallery.mecm.apm.utils.Constants.RECORD_NOT_FOUND;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.ApmTenant;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.AppPackageInfo;
import org.edgegallery.mecm.apm.model.MecHost;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageInfoDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.repository.ApmTenantRepository;
import org.edgegallery.mecm.apm.repository.AppPackageInfoRepository;
import org.edgegallery.mecm.apm.repository.AppPackageRepository;
import org.edgegallery.mecm.apm.repository.MecHostRepository;
import org.edgegallery.mecm.apm.utils.Constants;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

@Service("DbService")
public class DbService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbService.class);

    @Autowired
    private AppPackageRepository appPackageRepository;

    @Autowired
    private MecHostRepository mecHostRepository;

    @Autowired
    private ApmTenantRepository tenantRepository;

    @Autowired
    private AppPackageInfoRepository appPkgInfoRepository;

    /**
     * Create app package record.
     *
     * @param tenantId tenant ID
     * @param appPackageDto appPackageDto
     */
    public void createAppPackage(String tenantId, AppPackageDto appPackageDto) {
        boolean addTenant = false;

        ModelMapper mapper = new ModelMapper();
        AppPackage appPackage = mapper.map(appPackageDto, AppPackage.class);
        appPackage.setId(appPackageDto.getAppPkgId() + tenantId);
        appPackage.setTenantId(tenantId);

        Optional<ApmTenant> info = tenantRepository.findById(tenantId);
        if (info.isPresent()) {
            List<AppPackage> record = appPackageRepository.findByTenantId(tenantId);
            if (record.size() == Constants.MAX_ENTRY_PER_TENANT_PER_MODEL) {
                LOGGER.error("Max app instance's limit {} reached", Constants.MAX_ENTRY_PER_TENANT_PER_MODEL);
                throw new ApmException(Constants.MAX_LIMIT_REACHED_ERROR);
            }
        } else {
            if (tenantRepository.count() == Constants.MAX_TENANTS) {
                LOGGER.error("Max tenant limit {} reached", Constants.MAX_TENANTS);
                throw new ApmException(Constants.MAX_LIMIT_REACHED_ERROR);
            }
            addTenant = true;
        }

        appPackageRepository.save(appPackage);

        if (addTenant) {
            LOGGER.info("Add tenant {}", tenantId);
            ApmTenant tenant = new ApmTenant();
            tenant.setTenant(tenantId);
            tenantRepository.save(tenant);
        }

        LOGGER.info("app package record for tenant {} and package {} created successfully",
                tenantId, appPackageDto.getAppPkgId());
    }

    /**
     * Deletes app package record.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     */
    public void deleteAppPackage(String tenantId, String packageId) {
        Optional<AppPackage> info = appPackageRepository.findById(packageId + tenantId);
        if (!info.isPresent()) {
            LOGGER.error(RECORD_NOT_FOUND);
            throw new IllegalArgumentException(RECORD_NOT_FOUND);
        }
        appPackageRepository.delete(info.get());

        LOGGER.info("app package record for tenant {} and package {} deleted successfully",
                tenantId, packageId);

        List<AppPackage> record = appPackageRepository.findByTenantId(tenantId);
        if (record.isEmpty()) {
            LOGGER.info("Delete tenant {}", tenantId);
            tenantRepository.deleteById(tenantId);
        }
    }

    /**
     * Returns app package record.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @return app package record with mec host
     */
    public AppPackageDto getAppPackageWithHost(String tenantId, String packageId) {
        String id = packageId + tenantId;
        Optional<AppPackage> info = appPackageRepository.findById(id);
        if (!info.isPresent()) {
            LOGGER.error(RECORD_NOT_FOUND);
            throw new IllegalArgumentException(RECORD_NOT_FOUND);
        }
        ModelMapper mapper = new ModelMapper();
        AppPackageDto appPackageDto = mapper.map(info.get(), AppPackageDto.class);

        List<MecHostDto> mecHostDtoList = new LinkedList<>();
        List<String> mecHost = new LinkedList<>();
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(id)) {
                mecHostDtoList.add(new MecHostDto(host.getHostIp(), host.getDistributionStatus(),
                        host.getError()));
                mecHost.add(host.getHostIp());
            }
        });
        appPackageDto.setMecHostInfo(mecHostDtoList);
        return appPackageDto;
    }

    /**
     * Returns app package record.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @return app package record
     */
    public AppPackage getAppPackage(String tenantId, String packageId) {
        String id = packageId + tenantId;
        Optional<AppPackage> info = appPackageRepository.findById(id);
        if (!info.isPresent()) {
            LOGGER.error(RECORD_NOT_FOUND);
            throw new IllegalArgumentException(RECORD_NOT_FOUND);
        }
        return info.get();
    }

    /**
     * Returns list of app package.
     *
     * @param tenantId tenant ID
     * @return list of app package
     */
    public List<AppPackageDto> getAllAppPackage(String tenantId) {
        List<AppPackage> packageList = new LinkedList<>();
        appPackageRepository.findAll().forEach((AppPackage appPackage) -> {
            if (appPackage.getTenantId().equals(tenantId)) {
                packageList.add(appPackage);
            }
        });

        List<AppPackageDto> appPackageDtoList = new LinkedList<>();
        for (AppPackage appPackage : packageList) {
            ModelMapper mapper = new ModelMapper();
            AppPackageDto appPackageDto = mapper.map(appPackage, AppPackageDto.class);

            List<MecHostDto> mecHostDtoList = new LinkedList<>();
            mecHostRepository.findAll().forEach((MecHost host) -> {
                if (host.getPkgHostKey().equals(appPackage.getAppPkgId() + tenantId)) {
                    mecHostDtoList.add(new MecHostDto(host.getHostIp(), host.getDistributionStatus(),
                            host.getError()));
                }
            });
            appPackageDto.setMecHostInfo(mecHostDtoList);
            appPackageDtoList.add(appPackageDto);
        }
        return appPackageDtoList;
    }

    /**
     * Creates host record.
     *
     * @param tenantId tenant ID
     * @param appPackageDto app package Dto
     */
    public void createHost(String tenantId, AppPackageDto appPackageDto) {
        List<MecHostDto> hostList = appPackageDto.getMecHostInfo();
        MecHost host;
        for (MecHostDto mecHostDto : hostList) {
            MecHost existingHost = findHostWithIp(tenantId, appPackageDto.getAppPkgId(),
                            mecHostDto.getHostIp());
            if (existingHost != null) {
                LOGGER.info("host {} already exists, updating the record", mecHostDto.getHostIp());
                existingHost.setDistributionStatus("Processing");
                host = existingHost;
            } else {
                host = new MecHost();
                host.setPkgHostKey(appPackageDto.getAppPkgId() + tenantId);
                host.setDistributionStatus("Processing");
                host.setHostIp(mecHostDto.getHostIp());
                host.setAppPkgId(appPackageDto.getAppPkgId());
                host.setTenantId(tenantId);
            }

            mecHostRepository.save(host);
            LOGGER.info("host record for tenant {}, appId {} package {} and host {} created successfully",
                    tenantId, appPackageDto.getAppId(), appPackageDto.getAppPkgId(), mecHostDto.getHostIp());
        }
    }

    /**
     * Delete host record.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     */
    public void deleteHost(String tenantId, String packageId) {
        String id = packageId + tenantId;
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(id)) {
                mecHostRepository.delete(host);
                LOGGER.info("host record for tenant {} and package {} deleted successfully",
                        tenantId, packageId);
            }
        });
    }

    /**
     * Deletes host record which matches host ip.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param hostIp host ip
     */
    public void deleteHostWithIp(String tenantId, String packageId, String hostIp) {
        String id = packageId + tenantId;
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(id)
                    && host.getHostIp().equals(hostIp)) {
                mecHostRepository.delete(host);
                LOGGER.info("host record for tenant {}, package {} and host ip {} deleted successfully",
                        tenantId, packageId, hostIp);
            }
        });
    }

    /**
     * Returns host record which matches host ip, tenant ID and package ID.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param hostIp host ip
     * @return host record which matches host ip, tenant ID and package ID
     */
    public MecHost findHostWithIp(String tenantId, String packageId, String hostIp) {
        Iterable<MecHost> mecHostIterable = mecHostRepository.findAll();
        Iterator<MecHost> it = mecHostIterable.iterator();
        String id = packageId + tenantId;
        while (it.hasNext()) {
            MecHost host = it.next();
            if (host.getPkgHostKey().equals(id)
                    && host.getHostIp().equals(hostIp)) {
                return host;
            }
        }
        return null;
    }

    /**
     * Update distribution status of all hosts.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param status distribution status
     * @param error reason for failure
     */
    public void updateDistributionStatusOfAllHost(String tenantId, String packageId,
                                                String status, String error) {
        String id = packageId + tenantId;
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(id)) {
                host.setDistributionStatus(status);
                host.setError(error);
                mecHostRepository.save(host);
            }
        });
    }

    /**
     * Updates distribution status of host which matches host ip.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param hostIp host ip
     * @param status distribution status
     * @param error reason for failure
     */
    public void updateDistributionStatusOfHost(String tenantId, String packageId,
                                               String hostIp, String status, String error) {
        String id = packageId + tenantId;
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(id)
                    && host.getHostIp().equals(hostIp)) {
                host.setDistributionStatus(status);
                host.setError(error);
                mecHostRepository.save(host);
            }
        });
    }

    private void deleteAppPackageSyncInfo(String appstoreIp) {
        List<AppPackageInfo> appPkgInfosDb = appPkgInfoRepository.findByAppstoreId(appstoreIp);
        for (AppPackageInfo info : appPkgInfosDb) {

            boolean result = FileSystemUtils.deleteRecursively(new File(info.getAppId() + info.getPackageId()));
            LOGGER.debug("failed delete package appId {}, package ID {}, result {}", info.getAppId(),
                    info.getPackageId(), result);

            appPkgInfoRepository.delete(info);
            LOGGER.info("Deleted app package sync info DB {}", info);
        }
    }

    private Map<String, AppPackageInfo> updateAppPackageSyncInfo(String appstoreIp,
                                                                 List<AppPackageInfoDto> inAppPkgInfos) {
        List<String> inPkgIds = new LinkedList<>();

        for (AppPackageInfoDto inAppPkgInfo : inAppPkgInfos) {
            inPkgIds.add(inAppPkgInfo.getAppId() + inAppPkgInfo.getPackageId());
        }

        List<AppPackageInfo> appPkgInfosDb = appPkgInfoRepository.findByAppstoreId(appstoreIp);
        Map<String, AppPackageInfo> existingPkgCreateTime = new HashMap<>();
        for (AppPackageInfo info : appPkgInfosDb) {
            String appPackageId = info.getAppId() + info.getPackageId();
            if (!inPkgIds.contains(appPackageId)) {

                boolean result = FileSystemUtils.deleteRecursively(new File(appPackageId));
                LOGGER.debug("failed delete package appId {}, package ID {}, result {}", info.getAppId(),
                        info.getPackageId(), result);

                appPkgInfoRepository.delete(info);
                LOGGER.info("Deleted app package sync info DB {}", info);
            } else {
                existingPkgCreateTime.put(appPackageId, info);
            }
        }
        return existingPkgCreateTime;
    }

    /**
     * Updates app package info records.
     *
     * @param appstoreIp    app store IP
     * @param inAppPkgInfos app package infos
     */
    public void updateAppPackageInfoDB(String appstoreIp, List<AppPackageInfoDto> inAppPkgInfos) {

        if (inAppPkgInfos == null || inAppPkgInfos.isEmpty()) {
            deleteAppPackageSyncInfo(appstoreIp);
            return;
        }

        if (inAppPkgInfos.size() > Constants.MAX_APPS_PER_APPSTORE) {
            throw new ApmException("failed to update sync info DB, max limit is "
                    + Constants.MAX_APPS_PER_APPSTORE);
        }

        Map<String, AppPackageInfo> existingPkgCreateTime = updateAppPackageSyncInfo(appstoreIp, inAppPkgInfos);

        ModelMapper mapper = new ModelMapper();
        String createTime;
        for (AppPackageInfoDto inAppPkgInfo : inAppPkgInfos) {

            AppPackageInfo pkgInfo = mapper.map(inAppPkgInfo, AppPackageInfo.class);
            pkgInfo.setAppPkgInfoId(pkgInfo.getAppId() + pkgInfo.getPackageId());

            if (existingPkgCreateTime.isEmpty()) {
                pkgInfo.setSyncStatus("NOT_IN_SYNC");
            } else {
                createTime = null;
                AppPackageInfo existingPkg = existingPkgCreateTime.get(pkgInfo.getAppPkgInfoId());
                if (existingPkg != null) {
                    createTime = existingPkg.getCreateTime();
                }

                if (createTime == null || !createTime.equals(inAppPkgInfo.getCreateTime())) {
                    pkgInfo.setSyncStatus("NOT_IN_SYNC");
                } else {
                    pkgInfo.setSyncStatus(existingPkg.getSyncStatus());
                }
            }
            pkgInfo.setAppstoreIp(appstoreIp);
            appPkgInfoRepository.save(pkgInfo);
            LOGGER.info("Updated app package sync info DB {}", pkgInfo.toString());
        }
    }

    /**
     * Adds app package info records.
     *
     * @param pkgInfo package sync info
     */
    public void addAppPackageInfoDB(AppPackageInfo pkgInfo) {
        List<AppPackageInfo> appPkgInfosDb = appPkgInfoRepository.findByAppstoreId(pkgInfo.getAppstoreIp());
        if (!appPkgInfosDb.isEmpty() && (appPkgInfosDb.size() >= Constants.MAX_APPS_PER_APPSTORE)) {
            throw new ApmException("failed to add application sync info to DB, max limit reached");
        }
        appPkgInfoRepository.save(pkgInfo);
    }

    /**
     * Updates app package info record.
     *
     * @param appId           app ID
     * @param packageId       app package ID
     * @param syncStatus      app package sync status
     * @param operationalinfo operational info
     */
    public void updateAppPackageSyncStatus(String appId, String packageId,
                                           String syncStatus, String operationalinfo) {
        String pkgId = appId + packageId;
        Optional<AppPackageInfo> appPkgInfo = appPkgInfoRepository.findById(pkgId);
        if (!appPkgInfo.isPresent()) {
            throw new IllegalArgumentException("package not found");
        }
        AppPackageInfo pkgInfo = appPkgInfo.get();
        pkgInfo.setSyncStatus(syncStatus);
        pkgInfo.setOperationalInfo(operationalinfo);
        appPkgInfoRepository.save(pkgInfo);
        LOGGER.info("updated app {}, sync status {}, operInfo {}", pkgInfo.getName(), syncStatus, operationalinfo);
    }

    /**
     * Retrieves all app package info records.
     */
    public List<AppPackageInfo> getAppPackageSyncInfo() {
        Iterable<AppPackageInfo> dbPkgInfo = appPkgInfoRepository.findAll();

        Iterator<AppPackageInfo> itr = dbPkgInfo.iterator();
        List<AppPackageInfo> pkgInfos = new LinkedList<>();
        AppPackageInfo appPkgInfo;

        while (itr.hasNext()) {
            appPkgInfo = itr.next();
            pkgInfos.add(appPkgInfo);
        }

        if (pkgInfos.isEmpty()) {
            LOGGER.error(RECORD_NOT_FOUND);
            throw new IllegalArgumentException(RECORD_NOT_FOUND);
        }
        return pkgInfos;
    }

    /**
     * Retrieves app package info records.
     *
     * @return application package sync info
     */
    public AppPackageInfo getAppPackageSyncInfo(String id) {
        Optional<AppPackageInfo> appPkgInfo = appPkgInfoRepository.findById(id);
        if (!appPkgInfo.isPresent()) {
            LOGGER.error(RECORD_NOT_FOUND);
            throw new IllegalArgumentException(RECORD_NOT_FOUND);
        }

        return appPkgInfo.get();
    }

    /**
     * Deletes app package info records.
     *
     * @param appPkgInfoId app package info id
     */
    public void deleteAppPackageSyncInfoDb(String appPkgInfoId) {
        Optional<AppPackageInfo> appPkgInfo = appPkgInfoRepository.findById(appPkgInfoId);
        if (!appPkgInfo.isPresent()) {
            LOGGER.info(RECORD_NOT_FOUND);
            return;
        }
        //Delete only if added by atp flow where appstore is none
        AppPackageInfo deletePkg = appPkgInfo.get();
        if ("-".equals(deletePkg.getAppstoreIp())) {
            appPkgInfoRepository.deleteById(appPkgInfoId);
        }
    }
}
