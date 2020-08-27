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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.MecHost;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.repository.AppPackageRepository;
import org.edgegallery.mecm.apm.repository.MecHostRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("DbService")
public class DbService {

    @Autowired
    private AppPackageRepository appPackageRepository;

    @Autowired
    private MecHostRepository mecHostRepository;

    /**
     * Create app package record.
     *
     * @param tenantId tenant ID
     * @param appPackageDto appPackageDto
     */
    public void createAppPackage(String tenantId, AppPackageDto appPackageDto) {
        ModelMapper mapper = new ModelMapper();
        AppPackage appPackage = mapper.map(appPackageDto, AppPackage.class);
        appPackage.setId(appPackageDto.getAppPkgId() + tenantId);
        appPackage.setTenantId(tenantId);
        appPackageRepository.save(appPackage);
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
            return;
        }
        appPackageRepository.delete(info.get());
    }

    /**
     * Returns app package record.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @return app package record with mec host
     */
    public AppPackageDto getAppPackage(String tenantId, String packageId) {
        String id = packageId + tenantId;
        Optional<AppPackage> info = appPackageRepository.findById(id);
        if (!info.isPresent()) {
            throw new ApmException("Record does not exist with package id " + packageId);
        }
        ModelMapper mapper = new ModelMapper();
        AppPackageDto appPackageDto = mapper.map(info.get(), AppPackageDto.class);

        List<MecHostDto> mecHostDtoList = new LinkedList<>();
        List<String> mecHost = new LinkedList<>();
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)) {
                mecHostDtoList.add(new MecHostDto(host.getHostIp(), host.getDistributionStatus()));
                mecHost.add(host.getHostIp());
            }
        });
        appPackageDto.setMecHostInfo(mecHostDtoList);
        appPackageDto.setMecHost(mecHost);
        return appPackageDto;
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
            List<String> mecHost = new LinkedList<>();
            mecHostRepository.findAll().forEach((MecHost host) -> {
                if (host.getPkgHostKey().equals(appPackage.getAppPkgId() + tenantId)) {
                    mecHostDtoList.add(new MecHostDto(host.getHostIp(), host.getDistributionStatus()));
                    mecHost.add(host.getHostIp());
                }
            });
            appPackageDto.setMecHostInfo(mecHostDtoList);
            appPackageDto.setMecHost(mecHost);
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
        List<String> hostIpList = appPackageDto.getMecHost();
        for (String hostIp : hostIpList) {
            MecHost host = new MecHost();
            host.setPkgHostKey(appPackageDto.getAppPkgId() + tenantId);
            host.setDistributionStatus("PROCESSING");
            host.setHostIp(hostIp);
            host.setAppPkgId(appPackageDto.getAppPkgId());
            host.setTenantId(tenantId);
            mecHostRepository.save(host);
        }
    }

    /**
     * Delete host record.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     */
    public void deleteHost(String tenantId, String packageId) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)) {
                mecHostRepository.delete(host);
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
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)
                    && host.getHostIp().equals(hostIp)) {
                mecHostRepository.delete(host);
            }
        });
    }

    /**
     * Updates local file path in app package.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param localFilePath local file path
     */
    public void updateLocalFilePathOfAppPackage(String tenantId, String packageId,
                                                String localFilePath) {
        String id = packageId + tenantId;
        Optional<AppPackage> info = appPackageRepository.findById(id);
        if (!info.isPresent()) {
            throw new ApmException("error occurred while updating local file path in db" + packageId);
        }
        AppPackage appPackage = info.get();
        appPackage.setLocalFilePath(localFilePath);
        appPackageRepository.save(appPackage);
    }

    /**
     * Update distribution status of all hosts.
     *
     * @param tenantId tenant ID
     * @param packageId package ID
     * @param status distribution status
     */
    public void updateDistributionStatusOfAllHost(String tenantId, String packageId,
                                                String status) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)) {
                host.setDistributionStatus(status);
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
     */
    public void updateDistributionStatusOfHost(String tenantId, String packageId,
                                               String hostIp, String status) {
        mecHostRepository.findAll().forEach((MecHost host) -> {
            if (host.getPkgHostKey().equals(packageId + tenantId)
                    && host.getHostIp().equals(hostIp)) {
                host.setDistributionStatus(status);
                mecHostRepository.save(host);
            }
        });
    }
}
