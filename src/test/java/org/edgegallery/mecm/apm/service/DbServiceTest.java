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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.impl.client.CloseableHttpClient;
import org.edgegallery.mecm.apm.ApmApplicationTest;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.exception.ApmExceptionHandler;
import org.edgegallery.mecm.apm.model.AppPackage;
import org.edgegallery.mecm.apm.model.AppPackageInfo;
import org.edgegallery.mecm.apm.model.AppTemplate;
import org.edgegallery.mecm.apm.model.MecHost;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageInfoDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.model.dto.SyncUpdatedAppPackageDto;
import org.edgegallery.mecm.apm.repository.ApmTenantRepository;
import org.edgegallery.mecm.apm.repository.AppPackageInfoRepository;
import org.edgegallery.mecm.apm.repository.AppPackageRepository;
import org.edgegallery.mecm.apm.repository.AppTemplateRepository;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.edgegallery.mecm.apm.utils.FileChecker;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
public class DbServiceTest {

    private static final String TENANT_ID = "18db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String ERROR = "error";
    private static final String FAILED_TO_DISTRIBUTE = "failed to distribute";
    private static final String FAILED_TO_CONNECT = "failed to connect to app store";

    private AppPackageDto packageDto = new AppPackageDto();

    @InjectMocks
    private DbService dbService;

    RestServiceImpl restServiceImpl = new RestServiceImpl();
    ApmExceptionHandler apm = new ApmExceptionHandler();
    RestClientHelper restClientHelper;

    @Autowired
    private DbService dbServices;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mock
    AppPackageInfoRepository appPkgSyncRepository;

    @Mock
    AppPackageRepository appPackageRepository;

    @Mock
    AppTemplateRepository appTemplateRepository;

    @Mock
    ApmTenantRepository tenantRepository;

    @InjectMocks
    ApmServiceHelper apmServiceHelper;

    @BeforeEach
    public void setUpEach() {
        MockitoAnnotations.initMocks(this);
    }

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
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        AppPackage response = dbServices.getAppPackage(TENANT_ID, PACKAGE_ID);
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
        assertDoesNotThrow(() -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbServices.getAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testCreateHost() {
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createHost(TENANT_ID, packageDto));
        AppPackageDto response = dbServices.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);
        List<MecHostDto> hostDtos = response.getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(2, hostDtos.size());
        assertEquals("1.1.1.1", hostDtos.get(0).getHostIp());

        // clean up
        assertDoesNotThrow(() -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbServices.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbServices.getAppPackageWithHost("18db0283-3c67-4042-a708"
                + "-a8e4a10c6b32", PACKAGE_ID));
    }

    @Test
    public void testCreateHostDuplicateRecord() {
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createHost(TENANT_ID, packageDto));
        AppPackageDto response = dbServices.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);

        assertDoesNotThrow(() -> dbServices.updateDistributionStatusOfAllHost(TENANT_ID, PACKAGE_ID,
                ERROR, FAILED_TO_DISTRIBUTE));

        List<AppPackageDto> packageDtos = dbServices.getAllAppPackage(TENANT_ID);
        assertNotNull(packageDtos);
        assertEquals(1, packageDtos.size());
        List<MecHostDto> hostDtos = packageDtos.get(0).getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(ERROR, hostDtos.get(0).getStatus());
        assertEquals(ERROR, hostDtos.get(1).getStatus());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(0).getError());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(1).getError());

        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createHost(TENANT_ID, packageDto));
        response = dbServices.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
        assertNotNull(response);

        hostDtos = response.getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(2, hostDtos.size());
        assertEquals("1.1.1.1", hostDtos.get(0).getHostIp());
        assertEquals("Processing", hostDtos.get(0).getStatus());

        // clean up
        assertDoesNotThrow(() -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbServices.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbServices.getAppPackageWithHost("18db0283-3c67-4042-a708"
                + "-a8e4a10c6b32", PACKAGE_ID));
    }

    @Test
    public void testUpdateDistributionStatusOfAllHost() {
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createHost(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.updateDistributionStatusOfAllHost(TENANT_ID, PACKAGE_ID,
                ERROR, FAILED_TO_DISTRIBUTE));
        List<AppPackageDto> packageDtos = dbServices.getAllAppPackage(TENANT_ID);
        assertNotNull(packageDtos);
        assertEquals(1, packageDtos.size());
        List<MecHostDto> hostDtos = packageDtos.get(0).getMecHostInfo();
        assertNotNull(hostDtos);
        assertEquals(ERROR, hostDtos.get(0).getStatus());
        assertEquals(ERROR, hostDtos.get(1).getStatus());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(0).getError());
        assertEquals(FAILED_TO_DISTRIBUTE, hostDtos.get(1).getError());

        // clean up
        assertDoesNotThrow(() -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbServices.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbServices.getAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testUpdateDistributionStatusOfHost() {
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createHost(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.updateDistributionStatusOfHost(TENANT_ID, PACKAGE_ID, "2.2.2.2",
                ERROR, FAILED_TO_CONNECT));
        AppPackageDto packageDtos = dbServices.getAppPackageWithHost(TENANT_ID, PACKAGE_ID);
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
        assertDoesNotThrow(() -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertDoesNotThrow(() -> dbServices.deleteHost(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbServices.getAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testCreateAppPackageRecordIfNotExist() {
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));

        // clean up
        assertDoesNotThrow(() -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
        assertThrows(IllegalArgumentException.class, () -> dbService.getAppPackage("18db0283-3c67-4042-a708"
                + "-a8e4a10c6b32", PACKAGE_ID));
    }

    @Test
    public void testDeleteAppPackageRecordIfNotExist() {
        assertThrows(IllegalArgumentException.class, () -> dbServices.deleteAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void testupdateAppPackages() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Optional<AppPackage> info = Optional.of(new AppPackage());
        when(appPackageRepository.findById(Mockito.anyString())).thenReturn(info);

        dbService.updateAppPackage(TENANT_ID, packageDto);
        dbService.getAppPackage(TENANT_ID, PACKAGE_ID);
        dbService.deleteAppPackageSyncInfoDb("OK");
        dbServices.deleteHostWithIp(TENANT_ID, PACKAGE_ID, "host");

        Object[] obj = {"app"};
        Method method = DbService.class.getDeclaredMethod("deleteAppPackageSyncInfo", String.class);
        method.setAccessible(true);
        method.invoke(dbService, obj);

        HttpMethod m = HttpMethod.POST;
        try {
            restServiceImpl.sendRequest("a", m, "b", "b");
        } catch (Exception e) {
            assertTrue(true);
        }
        try {
            restServiceImpl.syncRecords("a", SyncUpdatedAppPackageDto.class, "b");
        } catch (Exception e) {
            assertTrue(true);
        }
        AccessDeniedException ex = null;
        RuntimeException ex1 = null;
        NoSuchElementException ex2 = new NoSuchElementException("ok");
        apm.handleAccessDeniedException(ex);
        apm.handleRuntimeException(ex1);
        apm.handleNoSuchElementException(ex2);
    }

    @Test
    public void addAppSyncPackageInfoDBTest() {
        List<AppPackageInfo> appPkgInfosDb = new ArrayList<>();
        AppPackageInfo packageInfo = new AppPackageInfo();
        packageInfo.setPackageId(PACKAGE_ID);
        packageInfo.setAppstoreIp("1.1.1.1");
        appPkgInfosDb.add(packageInfo);
        when(appPkgSyncRepository.findByAppstoreId(Mockito.anyString())).thenReturn(appPkgInfosDb);
        dbService.addAppSyncPackageInfoDB(packageInfo);
    }

    @Test
    public void createAppTemplateTest() {
        List<AppPackageInfoDto> list = new ArrayList<>();

        dbService.deleteNonExistingPackages("119.8.63.144", list);

    }

    @Test
    public void buildHttpClient() {
        restClientHelper = new RestClientHelper(true, "path", "trust");
        assertThrows(ApmException.class, () -> restClientHelper.buildHttpClient());
        restClientHelper.setTrustStorePasswd("truststore");
        restClientHelper.setSslEnabled(false);
        restClientHelper.setTrustStorePath("text");
        RestClientHelper restClientHelper2 = new RestClientHelper(restClientHelper.isSslEnabled(),
                restClientHelper.getTrustStorePath(),
                restClientHelper.getTrustStorePasswd());
        CloseableHttpClient httpClient = restClientHelper2.buildHttpClient();
        assertNotNull(httpClient);

    }

    @Test
    public void testApmService() {
        assertThrows(ApmException.class, () -> ApmServiceHelper.getHostList(null));
        assertThrows(ApmException.class, () -> ApmServiceHelper.saveInputStreamToFile(null, "packageId", TENANT_ID, "path"));
    }

    @Test
    public void testPackageDir() throws IOException {
        File dir = folder.newFile("classpath:packages");
        String path = ApmServiceHelper.getPackageDirPath(dir.getPath(), PACKAGE_ID, null);
        assertNotNull(path);
        File file = folder.newFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        FileInputStream fis = new FileInputStream(file.getPath());

        MockMultipartFile multipartFile = new MockMultipartFile("22406fba-fd5d-4f55-b3fa-89a45fee913a.csar",
                "app.csar", "", fis);
        assertThrows(ApmException.class, () -> ApmServiceHelper.saveMultipartFile(multipartFile, PACKAGE_ID, null, dir.getPath()));
    }

    @Test(expected = InvocationTargetException.class)
    public void testChildJsonObject() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        JsonParser parser = new JsonParser();
        JsonNull jsonNull = null;
        JsonElement jsonElement = parser.parse("{\"elememt\":\"package\",\"key\":" + jsonNull + "}");
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        Object[] obj1 = {jsonObject, "place"};
        Method method1 = ApmServiceHelper.class.getDeclaredMethod("getChildJsonObject", JsonObject.class, String.class);
        method1.setAccessible(true);
        method1.invoke(apmServiceHelper, obj1);
    }

    @Test(expected = InvocationTargetException.class)
    public void testChildJsonObjectJsonNull() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        JsonParser parser = new JsonParser();
        JsonNull jsonNull = null;
        JsonElement jsonElement = parser.parse("{\"elememt\":\"package\",\"key\":" + jsonNull + "}");
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        Object[] obj1 = {jsonObject, "key"};
        Method method1 = ApmServiceHelper.class.getDeclaredMethod("getChildJsonObject", JsonObject.class, String.class);
        method1.setAccessible(true);
        method1.invoke(apmServiceHelper, obj1);
    }

    @Test(expected = InvocationTargetException.class)
    public void testChildJsonObjectJsonValueNull() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        JsonParser parser = new JsonParser();
        JsonNull jsonNull = null;
        JsonElement jsonElement = parser.parse("{\"elememt\":\"package\",\"key\":" + jsonNull + "}");
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        Object[] obj1 = {jsonObject, "key"};
        Method method1 = ApmServiceHelper.class.getDeclaredMethod("getChildJsonObjectValue", JsonObject.class, String.class);
        method1.setAccessible(true);
        method1.invoke(apmServiceHelper, obj1);
    }

    @Test
    public void testSanitizeFile() throws IOException {
        File file = folder.newFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        FileInputStream fis = new FileInputStream(file.getPath());

        MockMultipartFile multipartFile = new MockMultipartFile("22406fba-fd5d-4f55-b3fa-89a45fee913a.csar",
                "app file name is.csar", "", fis);
        assertThrows(IllegalArgumentException.class, () -> FileChecker.check(multipartFile));
    }

    @Test
    public void buildHttpClient2() throws IOException {
        File dir = folder.newFile("classpath:packages");
        restClientHelper = new RestClientHelper(true, dir.getPath(), "ABc@12!#xyz");
        assertThrows(ApmException.class, () -> restClientHelper.buildHttpClient());
    }

    @Test
    public void testIsExistingHost() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        MecHostDto mecHostDto = new MecHostDto();
        mecHostDto.setStatus("processing");
        MecHost existingHost = new MecHost();
        existingHost.setDistributionStatus("Distributed");
        Object[] obj1 = {mecHostDto, existingHost};
        Method method1 = DbService.class.getDeclaredMethod("isExistingHost", MecHostDto.class, MecHost.class);
        method1.setAccessible(true);
        method1.invoke(dbService, obj1);
    }

    @Test
    public void testDeleteAppPackageSyncInfo() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        List<AppPackageInfo> appPkgInfosDb = new ArrayList<>();
        AppPackageInfo packageInfo = new AppPackageInfo();
        packageInfo.setPackageId(PACKAGE_ID);
        packageInfo.setAppstoreIp("1.1.1.1");

        AppPackageInfo packageInfo2 = new AppPackageInfo();
        packageInfo.setPackageId("packageId");
        packageInfo.setAppstoreIp("3.3.3.3");
        packageInfo.setAppPkgInfoId("id2");

        appPkgInfosDb.add(packageInfo);
        appPkgInfosDb.add(packageInfo2);

        when(appPkgSyncRepository.findByAppstoreId(Mockito.anyString())).thenReturn(appPkgInfosDb);
        Object[] obj1 = {"1.1.1.1"};
        Method method1 = DbService.class.getDeclaredMethod("deleteAppPackageSyncInfo", String.class);
        method1.setAccessible(true);
        method1.invoke(dbService, obj1);

        Optional<AppPackage> appPackage = Optional.of(new AppPackage());
        AppTemplate template = new AppTemplate();
        template.setAppId("appId");
        template.setAppPackageId(PACKAGE_ID);
        Optional<AppTemplate> info = Optional.of(new AppTemplate());
        when(appPackageRepository.findById(Mockito.anyString())).thenReturn(appPackage);
        when(appTemplateRepository.findById(Mockito.anyString())).thenReturn(info);
        List<AppPackage> record = new ArrayList<>();
        AppPackage appPkg = new AppPackage();
        appPkg.setTenantId(TENANT_ID);
        record.add(appPkg);
        when(appPackageRepository.findByTenantId(Mockito.anyString())).thenReturn(record);
        assertDoesNotThrow(() -> dbService.deleteAppPackage(TENANT_ID, PACKAGE_ID));

        AppPackageInfoDto infoDto = new AppPackageInfoDto();
        infoDto.setAppId("appId");
        infoDto.setPackageId(PACKAGE_ID);
        List<AppPackageInfoDto> appPackageInfoDtos = new ArrayList<>();
        appPackageInfoDtos.add(infoDto);
        assertDoesNotThrow(() -> dbService.deleteNonExistingPackages("1.1.1.1", appPackageInfoDtos));

        Optional<AppPackageInfo> appPkgInfo = Optional.of(new AppPackageInfo());
        when(appPkgSyncRepository.findById(Mockito.anyString())).thenReturn(appPkgInfo);

        AppPackageInfo appPackageInfo = dbService.getAppPackageSyncInfo("id1");
        assertNotNull(appPackageInfo);

        assertDoesNotThrow(() -> dbService.deleteAppPackageSyncInfoDb(packageInfo.getPackageId()));
        assertDoesNotThrow(() -> dbService.createOrUpdateAppTemplate(TENANT_ID, template));
    }

    @Test
    public void testCreateOrUpdateAppTemplate() {
        AppTemplate template = new AppTemplate();
        template.setAppId("appId");
        template.setAppPackageId(PACKAGE_ID);
        assertDoesNotThrow(() -> dbService.createOrUpdateAppTemplate(TENANT_ID, template));
        assertThrows(ApmException.class, () -> dbService.updateAppPackage(TENANT_ID, packageDto));

        Optional<AppPackageInfo> appPkgInfo = Optional.of(new AppPackageInfo());
        when(appPkgSyncRepository.findById(Mockito.anyString())).thenReturn(appPkgInfo);

        assertThrows(NoSuchElementException.class, () -> dbService.getAppPackageSyncInfo());
    }

}