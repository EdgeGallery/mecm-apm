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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.ApmApplicationTest;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppPackageInfo;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.SwImageDescr;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.repository.AppPackageInfoRepository;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.edgegallery.mecm.apm.utils.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
public class ApmServiceFacadeTest {

    private static final String TENANT_ID = "18db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String APP_ID = "f50358433cf8eb4719a62a49ed118c9c";
    private static final String ACCESS_TOKEN = "access_token";

    private AppPackageDto packageDto = new AppPackageDto();

    private PkgSyncInfo syncAppPkg = new PkgSyncInfo();

    @Autowired
    private DbService dbServices;

    @Autowired
    private ApmServiceFacade facades;

    @InjectMocks
    private ApmServiceFacade facade;

    @Mock
    private ApmService apmService;

    @Mock
    private DbService dbService;

    @Mock
    AppPackageInfoRepository appPkgSyncRepository;

    @Autowired
    @Mock
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;
    SwImageDescr swImageDescr = new SwImageDescr();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
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
        syncAppPkg.setPackageId(PACKAGE_ID);
        syncAppPkg.setAppstoreIp("OK");
    }

    @BeforeEach
    void setUpEach() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void getAppPackageFile() throws IOException {
        assertDoesNotThrow(() -> dbServices.createAppPackage(TENANT_ID, packageDto));
        assertDoesNotThrow(() -> dbServices.createHost(TENANT_ID, packageDto));
        File file = ResourceUtils.getFile("classpath:packages");
        facades.setLocalDirPath(file.getPath());
        InputStream inputStream = IOUtils.toInputStream("mock data for test", "UTF-8");
        String response = ApmServiceHelper.saveInputStreamToFile(inputStream, PACKAGE_ID, TENANT_ID, file.getPath());
        assertNotNull(response);
        File responseFile = new File(response);
        assertTrue(responseFile.exists());
        InputStream stream = facades.getAppPackageFile(TENANT_ID, PACKAGE_ID);
        assertNotNull(stream);
        assertDoesNotThrow(() -> facades.deleteAppPackage(TENANT_ID, PACKAGE_ID));
    }

    @Test
    public void addAppSyncInfoDb() throws Exception {

        Object[] obj = {packageDto, syncAppPkg, Constants.SUCCESS};
        Method method = ApmServiceFacade.class.getDeclaredMethod("addAppSyncInfoDb", AppPackageDto.class, PkgSyncInfo.class, String.class);
        method.setAccessible(true);
        method.invoke(facade, obj);
    }


    @Test
    public void onboardApplication() {
        String localFilePath = "/";

        facades.onboardApplication("access_token", "tenant id", packageDto, syncAppPkg);
        facades.onboardApplication("access_token", "tenant id", packageDto, localFilePath, syncAppPkg);
        facades.deleteDistributedAppPackage("tenant id", "host ip", "app package id", "access_token");
        facades.deleteDistributedAppPackageOnHost("tenant id", "host ip", "app package id", "access_token");
    }

    @Test
    public void deleteAppPackageInHostTest() {
        facades.deleteAppPackageInHost(TENANT_ID, PACKAGE_ID, "1.1.1.1");
    }

    @Test(expected = ApmException.class)
    public void checkIfManifestPresentRepoTest() {

        facades.checkIfManifestPresentRepo("repo", "repository", "tag", "accessToken");
    }

    @Test(expected = ApmException.class)
    public void checkIfManifestPresentRepoTests() {

        facades.checkIfManifestPresentRepo("repo:test", "repository", "tag", "accessToken");
    }

    @Test
    public void onboardApplicationtest() {
        String localFilePath = "/test";

        AppPackageInfo appPackageInfo = new AppPackageInfo();
        appPackageInfo.setSyncStatus(Constants.APP_IN_SYNC);

        facades.onboardApplication("access_token", "tenant id", packageDto, localFilePath, syncAppPkg);
    }
    @Test
    public void distributeApplicationPackageTest() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] obj1 = {"mepmEndPoint", "tenantId", "pkgId", "1.1.1.1", "ACCESS_TOKEN"};
        Method method1 = ApmServiceFacade.class.getDeclaredMethod("distributeApplicationPackage", String.class, String.class,String.class,String.class, String.class);
        method1.setAccessible(true);
        method1.invoke(facade, obj1);
    }

    @Test(expected = InvocationTargetException.class)
    public void getImagesExcludingTest1() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        swImageDescr.setSwImage("swImage");
        swImageDescr.setVersion("1.0");
        swImageDescr.setName("image:name");

        List<SwImageDescr> imageInfoList= new ArrayList<>();
        imageInfoList.add(swImageDescr);

        Object[] obj1 = {imageInfoList, "ACCESS_TOKEN"};
        Method method1 = ApmServiceFacade.class.getDeclaredMethod("getImagesExcludingAlreadyUploaded", List.class, String.class);
        method1.setAccessible(true);
        method1.invoke(facade, obj1);
    }

    @Test(expected = InvocationTargetException.class)
    public void getImagesExcludingTest2() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        Object[] obj1 = {null, "ACCESS_TOKEN"};
        Method method1 = ApmServiceFacade.class.getDeclaredMethod("getImagesExcludingAlreadyUploaded", List.class, String.class);
        method1.setAccessible(true);
        method1.invoke(facade, obj1);
    }

    @Test(expected = InvocationTargetException.class)
    public void onboardVmBasedAppPkgFailureTest() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        AppPackageDto appPackageDto = new AppPackageDto();
        appPackageDto.setAppPkgId(PACKAGE_ID);
        Object[] obj1 = {"ACCESS_TOKEN", TENANT_ID, appPackageDto};
        Method method1 = ApmServiceFacade.class.getDeclaredMethod("onboardVmBasedAppPkg", String.class, String.class, AppPackageDto.class);
        method1.setAccessible(true);
        method1.invoke(facade, obj1);
    }

    private void inventoryFlowUrls(MockRestServiceServer server) throws Exception {

        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        String serviceResponseBody = "{'mepmIp': '3.3.3.3', 'mepmPort': '808',"
                + " 'mepmUsername': 'admin' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String url2 = "https://1.1.1.1:8080/inventory/v1/mepms/3.3.3.3";
        String serviceResponseBody2 = "{'mepmIp': '3.3.3.3', 'mepmPort': '808',"
                + " 'username': 'admin' }";
        mockServer.expect(requestTo(url2))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody2, MediaType.APPLICATION_JSON));
    }

    @Test
    public void getAppTemplateInfoTest() throws Exception {
        facade.getAppTemplateInfo(TENANT_ID, PACKAGE_ID);
        inventoryFlowUrls(mockServer);
        facade.deleteDistributedAppPackageOnHost(TENANT_ID, "1.1.1.1", PACKAGE_ID, ACCESS_TOKEN);
        facade.deleteDistributedAppPackage(TENANT_ID, "1.1.1.1", PACKAGE_ID, ACCESS_TOKEN);
    }

    @Test(expected = Exception.class)
    public void uploadAndDistributeApplicationPackageTest() throws Exception {
        inventoryFlowUrls(mockServer);
        facade.uploadAndDistributeApplicationPackage(ACCESS_TOKEN,"1.1.1.1", TENANT_ID, APP_ID, PACKAGE_ID);
    }

    @Test
    public void getImagesExcludingUploadedTest() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        swImageDescr.setSwImage("swImage");
        List<SwImageDescr> imageInfoList= new ArrayList<>();
        imageInfoList.add(swImageDescr);

        Object[] obj = {imageInfoList, "ACCESS_TOKEN"};
        Method swImageList = ApmServiceFacade.class.getDeclaredMethod("getImagesExcludingAlreadyUploaded", List.class, String.class);
        swImageList.setAccessible(true);
        swImageList.invoke(facade, obj);
        assertNotNull(swImageList);
    }


    @Test
    public void testGetAppPackageInfoDB() throws Exception {
        Optional<AppPackageInfo> appPkgInfo = Optional.of(new AppPackageInfo());
        when(appPkgSyncRepository.findById(Mockito.anyString())).thenReturn(appPkgInfo);
        assertDoesNotThrow(() -> facade.getAppPackageInfoDB("id1"));
    }

}