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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.ApmApplicationTest;
import org.edgegallery.mecm.apm.model.AppPackageSyncInfo;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.service.ApmServiceFacade;
import org.edgegallery.mecm.apm.service.DbService;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
@AutoConfigureMockMvc
public class SyncAppPackageTest {

    private static final String TENANT_ID = "19db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID1 = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String APP_ID1 = "f50358433cf8eb4719a62a49ed118c9c";
    private static final String PACKAGE_ID2 = "f60358433cf8eb4719a62a49ed118c9b";
    private static final String TIME = "Thu Nov 21 16:02:24 CST 2019";
    @Autowired
    MockMvc mvc;
    @Autowired
    private DbService dbService;
    @Autowired
    private ApmServiceFacade apmServiceFacade;
    @Autowired
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private AppPackageDto packageDto1 = new AppPackageDto();
    private AppPackageDto packageDto2 = new AppPackageDto();

    @Before
    public void setUp() throws IOException {
        mockAppPackageDto(PACKAGE_ID1, packageDto1);
        mockOnboardPackage(packageDto1);
    }

    @After
    public void cleanUp() {
        apmServiceFacade.deleteAppPackage(TENANT_ID, packageDto1.getAppPkgId());
    }

    public void mockAppPackageDto(String packageId, AppPackageDto packageDto) {
        packageDto.setAppPkgId("f50358433cf8eb4719a62a49ed118c9c" + packageId);
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

    public void mockOnboardPackage(AppPackageDto packageDto) throws IOException {
        dbService.createAppPackage(TENANT_ID, packageDto);
        dbService.createHost(TENANT_ID, packageDto);
        File file = ResourceUtils.getFile("classpath:packages");
        apmServiceFacade.setLocalDirPath(file.getPath());
        InputStream inputStream = IOUtils.toInputStream("mock data for test", "UTF-8");
        String response = ApmServiceHelper.saveInputStreamToFile(inputStream,
                packageDto.getAppPkgId(), null, file.getPath());
        assertNotNull(response);
        File responseFile = new File(response);
        assertTrue(responseFile.exists());
        InputStream stream = apmServiceFacade.getAppPackageFile(null, packageDto.getAppPkgId());
        assertNotNull(stream);
    }


    @Test
    @WithMockUser(roles = "MECM_ADMIN")
    public void syncAppPackageTest() throws Exception {
        String url1 = "https://1.1.1.1:8080/inventory/v1/appstore/1.1.1.1";
        String serviceResponseBody = "{'appstoreIp': '1.1.1.1', 'appstorePort': 1234, 'appstoreRepoUserName': "
                + "'admin', 'appstoreRepoPassword': '12345', 'appstoreName': 'testAppStore' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        url1 = "https://1.1.1.1:1234/mec/appstore/v1/apps/e261211d80d04cb6aed00e5cd1f2cd11/packages/"
                + "b5a6ca9b8f85477bba2cd66fd79d5f98";
        serviceResponseBody = "{\n"
                + "        \"packageId\": \"b5a6ca9b8f85477bba2cd66fd79d5f98\",\n"
                + "        \"size\": \"0\",\n"
                + "        \"format\": \"{\\\"name\\\":\\\"76026777dd5e47d8ad869585cf6e0652\\\"}\",\n"
                + "        \"createTime\": \"2021-02-10T07:56:35.157+0000\",\n"
                + "        \"name\": \"zoneminder\",\n"
                + "        \"version\": \"1.0\",\n"
                + "        \"type\": \"Video Application\",\n"
                + "        \"details\": \"ZoneMinder\\n\",\n"
                + "        \"affinity\": \"x86\",\n"
                + "        \"industry\": \"Smart Park\",\n"
                + "        \"contact\": null,\n"
                + "        \"appId\": \"e261211d80d04cb6aed00e5cd1f2cd11\",\n"
                + "        \"userId\": \"50ba5ba7-5165-4754-9192-9c739039109d\",\n"
                + "        \"userName\": \"wenson\",\n"
                + "        \"status\": \"Published\",\n"
                + "        \"shortDesc\": \"ZoneMinder is an integrated set of applications which provide a \",\n"
                + "        \"testTaskId\": null,\n"
                + "        \"provider\": \"Huawei\"\n"
                + "    }";
        mockServer.expect(requestTo(url1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        url1 = "https://1.1.1.1:8080/inventory/v1/apprepos";
        serviceResponseBody = "[{'repoEndPoint': '1.1.1.1:4443', 'repoName': 'AppRepo1', 'repoUserName': "
                + "'admin', 'repoPassword': '12345' }]";
        mockServer.expect(requestTo(url1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913c.csar");
        InputStream ins = new BufferedInputStream(new FileInputStream(file.getPath()));
        InputStreamResource inputStreamResource = new InputStreamResource(ins);
        String url = "https://1.1.1.1:1234/mec/appstore/v1/apps/e261211d80d04cb6aed00e5cd1f2cd11/packages"
                + "/b5a6ca9b8f85477bba2cd66fd79d5f98/action/download";
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(inputStreamResource, MediaType.APPLICATION_OCTET_STREAM));

        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.post("/apm/v1/apps/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).with(csrf())
                        .header("access_token", "aasdjk")
                        .content(" [{\n"
                                + "\n"
                                + "  \"appstoreIp\": \"1.1.1.1\",\n"
                                + "  \"packageId\": \"b5a6ca9b8f85477bba2cd66fd79d5f98\",\n"
                                + "  \"appId\": \"e261211d80d04cb6aed00e5cd1f2cd11\"\n"
                                + "}]"))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        MvcResult result = resultActions.andReturn();

        resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/apps/syncstatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("access_token", "aasdjk")
                        .content(""))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();

       /* AppRepo appRepo = new AppRepo();
        appRepo.setTenantId(TENANT_ID);

        Map<String, AppRepo> repoMap = new HashMap<>();
        repoMap.put("1", appRepo);

        PkgSyncInfo pkgSyncInfo = new PkgSyncInfo();
        pkgSyncInfo.setAppId(APP_ID1);
        pkgSyncInfo.setPackageId(PACKAGE_ID1);
        pkgSyncInfo.setRepoInfo(repoMap);
        pkgSyncInfo.setAppstoreIp("1.1.1.1");
        pkgSyncInfo.setAppstorePort("1001");

        AppPackageSyncInfo syncInfos = new AppPackageSyncInfo();
        List<PkgSyncInfo> pkgSyncInfos= new ArrayList<>();
        pkgSyncInfos.add(pkgSyncInfo);
        syncInfos.setSyncInfo(pkgSyncInfos);
        syncInfos.setRepoInfo(repoMap);
        apmServiceFacade.syncApplicationPackages("access_token", syncInfos);*/
    }
}
