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

import static org.edgegallery.mecm.apm.utils.Constants.ERROR_IN_UPDATING_LOCAL_FILE_PATH;
import static org.edgegallery.mecm.apm.utils.Constants.RECORD_NOT_FOUND;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.ApmTenant;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.MecHost;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.repository.ApmTenantRepository;
import org.edgegallery.mecm.apm.repository.AppPackageRepository;
import org.edgegallery.mecm.apm.repository.MecHostRepository;
import org.edgegallery.mecm.apm.utils.Constants;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("DbService")
public class DbService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbService.class);

    @Autowired
    private AppPackageRepository appPackageRepository;

    @Autowired
    private MecHostRepository mecHostRepository;

    @Autowired
    private ApmTenantRepository tenantRepository;

    /**
     * Create app package record.
     *
     * @param tenantId      tenant ID
     * @param appPackageDto appPackageDto
     */
    public void createAppPackage(String tenantId, AppPackageDto appPackageDto) {

        boolean addTenant = false;

        if (appPackageRepository.existsById(appPackageDto.getAppPkgId() + tenantId)) {
            throw new IllegalArgumentException("Record already exist");
        }
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
     * @param tenantId  tenant ID
     * @param packageId package ID
     */
    public void deleteAppPackage(String tenantId, String packageId) {
        Optional<AppPackage> info = appPackageRepository.findById(packageId + tenantId);
        if (!info.isPresent()) {
            return;
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
     * @param tenantId  tenant ID
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
            if (host.getPkgHostKey().equals(packageId + tenantId)) {
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
     * @param tenantId  tenant ID
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
     * @param tenantId      tenant ID
     * @param appPackageDto app package Dto
     */
    public void createHost(String tenantId, AppPackageDto appPackageDto) {
        List<MecHostDto> hostList = appPackageDto.getMecHostInfo();
        for (MecHostDto mecHostDto : hostList) {
            MecHost host = new MecHost();
            host.setPkgHostKey(appPackageDto.getAppPkgId() + tenantId);
            host.setDistributionStatus("Processing");
            host.setHostIp(mecHostDto.getHostIp());
            host.setAppPkgId(appPackageDto.getAppPkgId());
            host.setTenantId(tenantId);
            mecHostRepository.save(host);
            LOGGER.info("host record for tenant {}, package {} and host {} created successfully",
                    tenantId, appPackageDto.getAppPkgId(), mecHostDto.getHostIp());
        }
    }

    /**
     * Delete host record.
     *
     * @param tenantId  tenant ID
     * @param packageId package ID
     */
    public void deleteHost(String tenantId, String packageId) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)) {
                mecHostRepository.delete(host);
                LOGGER.info("host record for tenant {} and package {} deleted successfully",
                        tenantId, packageId);
            }
        });
    }

    /**
     * Deletes host record which matches host ip.
     *
     * @param tenantId  tenant ID
     * @param packageId package ID
     * @param hostIp    host ip
     */
    public void deleteHostWithIp(String tenantId, String packageId, String hostIp) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)
                    && host.getHostIp().equals(hostIp)) {
                mecHostRepository.delete(host);
                LOGGER.info("host record for tenant {}, package {} and host ip {} deleted successfully",
                        tenantId, packageId, hostIp);
            }
        });
    }

    /**
     * Updates local file path in app package.
     *
     * @param tenantId      tenant ID
     * @param packageId     package ID
     * @param localFilePath local file path
     */
    public void updateLocalFilePathOfAppPackage(String tenantId, String packageId,
                                                String localFilePath) {
        String id = packageId + tenantId;
        Optional<AppPackage> info = appPackageRepository.findById(id);
        if (!info.isPresent()) {
            LOGGER.error(ERROR_IN_UPDATING_LOCAL_FILE_PATH, packageId);
            throw new ApmException("error occurred while updating local file path in db for package " + packageId);
        }
        AppPackage appPackage = info.get();
        appPackage.setLocalFilePath(localFilePath);
        appPackageRepository.save(appPackage);
        LOGGER.info("local file path updated for tenant {} and package {}", tenantId, packageId);
    }

    /**
     * Update distribution status of all hosts.
     *
     * @param tenantId  tenant ID
     * @param packageId package ID
     * @param status    distribution status
     * @param error     reason for failure
     */
    public void updateDistributionStatusOfAllHost(String tenantId, String packageId,
                                                  String status, String error) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)) {
                host.setDistributionStatus(status);
                host.setError(error);
                mecHostRepository.save(host);
            }
        });
    }

    /**
     * Updates distribution status of host which matches host ip.
     *
     * @param tenantId  tenant ID
     * @param packageId package ID
     * @param hostIp    host ip
     * @param status    distribution status
     * @param error     reason for failure
     */
    public void updateDistributionStatusOfHost(String tenantId, String packageId,
                                               String hostIp, String status, String error) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)
                    && host.getHostIp().equals(hostIp)) {
                host.setDistributionStatus(status);
                host.setError(error);
                mecHostRepository.save(host);
            }
        });
    }
}
