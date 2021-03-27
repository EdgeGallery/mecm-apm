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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedList;
import java.util.List;
import org.edgegallery.mecm.apm.ApmApplicationTest;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
public class DbServiceTest {

    private static final String TENANT_ID = "18db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String ERROR = "error";
    private static final String FAILED_TO_DISTRIBUTE = "failed to distribute";
    private static final String FAILED_TO_CONNECT = "failed to connect to app store";
    private static final String TIME = "Thu Nov 21 16:02:24 CST 2019";

    private AppPackageDto packageDto = new AppPackageDto();
    @Autowired
    private DbService dbService;

    @Before
    public void setUp() {
        packageDto.setAppPkgId(PACKAGE_ID);
        packageDto.setAppId("f50358433cf8eb4719a62a49ed118c9c");
        packageDto.setAppIconUrl("http://1.1.1.1:1234/mec");
        packageDto.setAppPkgAffinity("GPU");
        packageDto.setAppPkgDesc("face recognition application");
        packageDto.setAppPkgName("codelab-demo1");
        packageDto.setAppPkgPath("http://1.1.1.1:1234/mec/appstore/v1/apps/8ec923a8-9e30-4c94-a7ac-c92279488db2"
                + "/packages/0fb274f2-213b-4a66-accc-ab218470caa3/action/download");
        packageDto.setAppPkgVersion("1.0");
        packageDto.setAppProvider("Huawei");
        MecHostDto hostDto = new MecHostDto();
        hostDto.setHostIp("1.1.1.1");
        MecHostDto hostDto2 = new MecHostDto();
        hostDto2.setHostIp("2.2.2.2");
        List<MecHostDto> hostDtos = new LinkedList<>();
        hostDtos.add(hostDto);
        hostDtos.add(hostDto2);
        packageDto.setMecHostInfo(hostDtos);
    }

    @Test
    public void testCreateAppPackage() {
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        AppPackage response = dbService.getAppPackage(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);
        assertEquals(PACKAGE_ID, response.getAppPkgId());
        assertEquals("f50358433cf8eb4719a62a49ed118c9c", response.getAppId());
        assertEquals("http://1.1.1.1:1234/mec", response.getAppIconUrl());
        assertEquals("GPU", response.getAppPkgAffinity());
        assertEquals("face recognition application", response.getAppPkgDesc());
        assertEquals("codelab-demo1", response.getAppPkgName());
        assertEquals("http://1.1.1.1:1234/mec/appstore/v1/apps/8ec923a8-9e30-4c94-a7ac-c92279488db2/packages"
                + "/0fb274f2-213b-4a66-accc-ab218470caa3/action/download", response.getAppPkgPath());
        assertEquals("1.0", response.getAppPkgVersion());
        assertEquals("Huawei", response.getAppProvider());

        // clean up
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testCreateHost() {
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.createHost(TENANT_ID, packageDto));
        AppPackageDto response = dbService.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);
        List<MecHostDto> hostDtos = response.getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(2, hostDtos.size());
        assertEquals("1.1.1.1", hostDtos.get(0).getHostIp());

        // clean up
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbService.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackageWithHost("18db0283-3c67-4042-a708"
                + "-a8e4a10c6b32", PACKAGE_ID));
    }

    @Test
    public void testCreateHostDuplicateRecord() {
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.createHost(TENANT_ID, packageDto));
        AppPackageDto response = dbService.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);

        assertDoesNotThrow(() -> dbService.updateDistributionStatusOfAllHost(TENANT_ID, PACKAGE_ID,
                ERROR, FAILED_TO_DISTRIBUTE));

        List<AppPackageDto> packageDtos = dbService.getAllAppPackage(TENANT_ID);
        assertNotNull(packageDtos);
        assertEquals(1, packageDtos.size());
        List<MecHostDto> hostDtos = packageDtos.get(0).getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(ERROR, hostDtos.get(0).getStatus());
        assertEquals(ERROR, hostDtos.get(1).getStatus());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(0).getError());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(1).getError());

        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.createHost(TENANT_ID, packageDto));
        response = dbService.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);

        hostDtos = response.getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(2, hostDtos.size());
        assertEquals("1.1.1.1", hostDtos.get(0).getHostIp());
        assertEquals("Processing", hostDtos.get(0).getStatus());

        // clean up
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbService.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackageWithHost("18db0283-3c67-4042-a708"
                + "-a8e4a10c6b32", PACKAGE_ID));
    }

    @Test
    public void testUpdateDistributionStatusOfAllHost() {
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.createHost(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.updateDistributionStatusOfAllHost(TENANT_ID, PACKAGE_ID,
                ERROR, FAILED_TO_DISTRIBUTE));
        List<AppPackageDto> packageDtos = dbService.getAllAppPackage(TENANT_ID);
        assertNotNull(packageDtos);
        assertEquals(1, packageDtos.size());
        List<MecHostDto> hostDtos = packageDtos.get(0).getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(ERROR, hostDtos.get(0).getStatus());
        assertEquals(ERROR, hostDtos.get(1).getStatus());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(0).getError());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(1).getError());

        // clean up
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbService.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testUpdateDistributionStatusOfHost() {
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.createHost(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.updateDistributionStatusOfHost(TENANT_ID, PACKAGE_ID, "2.2.2.2",
                ERROR, FAILED_TO_CONNECT));
        AppPackageDto packageDtos = dbService.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(packageDtos);
        List<MecHostDto> hostDtos = packageDtos.getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(2, hostDtos.size());
        for (MecHostDto hostDto : hostDtos) {
            if ("2.2.2.2".equals(hostDto.getHostIp())) {
                assertEquals(ERROR, hostDto.getStatus());
                assertEquals(FAILED_TO_CONNECT, hostDto.getError());
            }
        }

        // clean up
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbService.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testCreateAppPackageRecordIfNotExist() {
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbService.createAppPackage(TENANT_ID, packageDto));

        // clean up
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackage("18db0283-3c67-4042-a708"
                + "-a8e4a10c6b32", PACKAGE_ID));
    }

    @Test
    public void testDeleteAppPackageRecordIfNotExist() {
        assertThrows(IllegalArgumentException.class, () -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));
    }
	
	@Test
	public void testupdateAppPackage() {
		assertThrows(ApmException.class,() -> dbService.updateAppPackage(TENANT_ID,packageDto));
		assertThrows(IllegalArgumentException.class,() -> dbService.getAppPackage(TENANT_ID,PACKAGE_ID));
		dbService.deleteAppPackageSyncInfoDb("OK");

	}
}