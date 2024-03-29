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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.ApmApplicationTest;
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
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
@AutoConfigureMockMvc
public class ApmHandlerTest {

    private static final String TENANT_ID = "19db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID1 = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String APP_ID1 = "f50358433cf8eb4719a62a49ed118c9c";
    private static final String PACKAGE_ID2 = "f60358433cf8eb4719a62a49ed118c9b";
    private static final String TIME = "Thu Nov 21 16:02:24 CST 2019";
    private static final String APM_TENANT = "/apm/v1/tenants/";
    private static final String SAMPLE_TOKEN = "SampleToken";
    private static final String ACCESS_TOKEN = "access_token";

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
                packageDto.getAppPkgId(), TENANT_ID, file.getPath());
        assertNotNull(response);
        File responseFile = new File(response);
        assertTrue(responseFile.exists());
        InputStream stream = apmServiceFacade.getAppPackageFile(TENANT_ID, packageDto.getAppPkgId());
        assertNotNull(stream);
    }

    @Test
    @WithMockUser(roles = "MECM_ADMIN")
    public void onBoardAppPackage() throws Exception {
        String request = "{\n"
                + "  \"appIconUrl\": \"http://1.1.1.1:1234/mec\",\n"
                + "  \"appId\": \"f40358433cf8eb4719a62a49ed118c9c\",\n"
                + "  \"appPkgAffinity\": \"GPU\",\n"
                + "  \"appPkgDesc\": \"face recognition application\",\n"
                + "  \"appPkgId\": \"f40358433cf8eb4719a62a49ed118c9b\",\n"
                + "  \"appPkgName\": \"正在参-加开源.中国举_办的\",\n"
                + "  \"appPkgPath\": \"http://1.1.1.1:4443/mec/appstore/v1/apps/8ec923a8-9e30-4c94-a7ac-c92279488db2"
                + "/packages/0fb274f2-213b-4a66-accc-ab218470caa3/action/download\",\n"
                + "  \"appPkgVersion\": \"1.0\",\n"
                + "  \"appProvider\": \"huawei\",\n"
                + "  \"createdTime\": \"Thu Nov 21 16:02:24 CST 2019\",\n"
                + "  \"mecHostInfo\": [\n"
                + "    { \"hostIp\" : \"1.1.1.1\"}\n"
                + "  ],\n"
                + "  \"modifiedTime\": \"Thu Nov 21 16:02:24 CST 2019\"\n"
                + "}";

        String serviceResponseBody;

        String url3 = "https://1.1.1.1:8080/inventory/v1/appstore/1.1.1.1";
        serviceResponseBody = "{'appstoreIp': '1.1.1.1', 'appstorePort': 1234, 'appstoreRepoUserName': "
                + "'admin', 'appstoreRepoPassword': '12345' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url3))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String url1 = "https://1.1.1.1:8080/inventory/v1/apprepos";
        serviceResponseBody = "[{'repoEndPoint': '1.1.1.1:4443', 'repoName': 'AppRepo1', 'repoUserName': "
                + "'admin', 'repoPassword': '12345' }]";
        mockServer.expect(requestTo(url1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913c.csar");
        InputStream ins = new BufferedInputStream(new FileInputStream(file.getPath()));
        InputStreamResource inputStreamResource = new InputStreamResource(ins);
        String url = "http://1.1.1.1:4443/mec/appstore/v1/apps/8ec923a8-9e30-4c94-a7ac-c92279488db2/packages"
                + "/0fb274f2-213b-4a66-accc-ab218470caa3/action/download";

        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(inputStreamResource, MediaType.APPLICATION_OCTET_STREAM));

        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.post("/apm/v1/tenants/" + TENANT_ID
                        + "/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).with(csrf())
                        .header("access_token", "aasdjk")
                        .content(request))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertEquals("{\"packageId\":\"f40358433cf8eb4719a62a49ed118c9cf40358433cf8eb4719a62a49ed118c9b\"}",
                obj.getContentAsString());
        mockServer.verify();
    }

    @Test
    @WithMockUser(roles = "MECM_ADMIN")
    public void queryAppPackageInfoTest() throws Exception {

        String url1 = "https://1.1.1.1:8080/inventory/v1/appstore/1.1.1.1";
        String serviceResponseBody = "{'appstoreIp': '1.1.1.1', 'appstorePort': 1234, 'appstoreRepoUserName': "
                + "'admin', 'appstoreRepoPassword': '12345' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String url2 = "https://1.1.1.1:1234/mec/appstore/v1/apps";
        String serviceResponseBody1 = "[\n"
                + "    {\n"
                + "        \"appId\": \"5e489a241af84e35b02079ace6954fc7\",\n"
                + "        \"iconUrl\": null,\n"
                + "        \"name\": \"zoneminder\",\n"
                + "        \"provider\": \"Huawei\",\n"
                + "        \"type\": \"Video Application\",\n"
                + "        \"shortDesc\": \"ZoneMinder is an integrated set of applications which provide a complete surveillance solution attached to a Linux based machine.\",\n"
                + "        \"createTime\": \"2021-02-10 15:56:35.157777\",\n"
                + "        \"details\": \"ZoneMinder\\n\",\n"
                + "        \"downloadCount\": 2,\n"
                + "        \"affinity\": \"x86\",\n"
                + "        \"industry\": \"Smart Park\",\n"
                + "        \"contact\": null,\n"
                + "        \"score\": 5.0,\n"
                + "        \"userId\": \"50ba5ba7-5165-4754-9192-9c739039109d\",\n"
                + "        \"userName\": \"wenson\",\n"
                + "        \"status\": \"Published\"\n"
                + "    }    \n"
                + "]";
        mockServer.expect(requestTo(url2))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody1, MediaType.APPLICATION_JSON));

        String url3 = "https://1.1.1.1:1234/mec/appstore/v1/apps/5e489a241af84e35b02079ace6954fc7/packages";
        String serviceResponseBody3 = "[\n"
                + "    {\n"
                + "        \"packageId\": \"7530d819ca12460a9cdf31c790417752\",\n"
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
                + "        \"appId\": \"5e489a241af84e35b02079ace6954fc7\",\n"
                + "        \"userId\": \"50ba5ba7-5165-4754-9192-9c739039109d\",\n"
                + "        \"userName\": \"wenson\",\n"
                + "        \"status\": \"Published\",\n"
                + "        \"shortDesc\": \"ZoneMinder is an integrated set of applications which provide a \",\n"
                + "        \"testTaskId\": null,\n"
                + "        \"provider\": \"Huawei\"\n"
                + "    }\n"
                + "]";
        mockServer.expect(requestTo(url3))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody3, MediaType.APPLICATION_JSON));

        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/apps/info/appstores/1.1.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("access_token", "aasdjk")
                        .content(""))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
       assertEquals(
                "[{\"packageId\":\"7530d819ca12460a9cdf31c790417752\",\"size\":\"0\",\"format\":\"{\\\"name\\\":\\\"76026777dd5e47d8ad869585cf6e0652\\\"}\",\"createTime\":\"2021-02-10T07:56:35.157+0000\",\"appstoreName\":null,\"name\":\"zoneminder\",\"version\":\"1.0\",\"type\":\"Video Application\",\"details\":\"ZoneMinder\\n\",\"affinity\":\"x86\",\"industry\":\"Smart Park\",\"contact\":null,\"appId\":\"5e489a241af84e35b02079ace6954fc7\",\"userId\":\"50ba5ba7-5165-4754-9192-9c739039109d\",\"userName\":\"wenson\",\"status\":\"Published\",\"shortDesc\":\"ZoneMinder is an integrated set of applications which provide a \",\"testTaskId\":null,\"provider\":\"Huawei\",\"syncStatus\":\"NOT_IN_SYNC\"}]",
                obj.getContentAsString());
        mockServer.verify();
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void onBoardAppPackageWithInvalidAppPackagePath() throws Exception {
        String request = "{\n"
                + "  \"appIconUrl\": \"app icon url\",\n"
                + "  \"appId\": \"f40358433cf8eb4719a62a49ed118c9c\",\n"
                + "  \"appPkgAffinity\": \"GPU\",\n"
                + "  \"appPkgDesc\": \"face recognition application\",\n"
                + "  \"appPkgId\": \"f40358433cf8eb4719a62a49ed118c9b\",\n"
                + "  \"appPkgName\": \"codelab-demo1\",\n"
                + "  \"appPkgPath\": \"app pkg path\",\n"
                + "  \"appPkgVersion\": \"1.0\",\n"
                + "  \"appProvider\": \"huawei\",\n"
                + "  \"createdTime\": \"Thu Nov 21 16:02:24 CST 2019\",\n"
                + "  \"mecHostInfo\": [\n"
                + "    { \"hostIp\" : \"1.1.1.1\"}\n"
                + "  ],\n"
                + "  \"modifiedTime\": \"Thu Nov 21 16:02:24 CST 2019\"\n"
                + "}";

        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.post("/apm/v1/tenants/" + TENANT_ID
                        + "/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).with(csrf())
                        .header("access_token", "aasdjk")
                        .content(request))
                        .andExpect(MockMvcResultMatchers.status().isBadRequest());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertTrue(obj.getContentAsString().contains("invalid app package path"));
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void getAppPackageInfo() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + APP_ID1 + PACKAGE_ID1)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }

    /*@Test
    @WithMockUser(roles = "MECM_TENANT")
    public void deleteAppPackage() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.delete("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + APP_ID1 + PACKAGE_ID1)
                        .accept(MediaType.TEXT_PLAIN_VALUE))
                        .andExpect(MockMvcResultMatchers.status().is4xxClientError());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertEquals("success", obj.getContentAsString());

        mockAppPackageDto(PACKAGE_ID1, packageDto1);
        mockOnboardPackage(packageDto1);
    }*/

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void downloadAppPackage() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + APP_ID1 + PACKAGE_ID1 + "/download")
                        .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void getAllAppPackageInfo() throws Exception {
        mockAppPackageDto(PACKAGE_ID2, packageDto2);
        mockOnboardPackage(packageDto2);
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
        apmServiceFacade.deleteAppPackage(TENANT_ID, packageDto2.getAppPkgId());
    }

    /*@Test
    @WithMockUser(roles = "MECM_TENANT")
    public void deleteAppPackageInHost() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.delete("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + APP_ID1 + PACKAGE_ID1 + "/hosts/1.1.1.1")
                        .accept(MediaType.TEXT_PLAIN_VALUE))
                        .andExpect(MockMvcResultMatchers.status().is4xxClientError());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertEquals("success", obj.getContentAsString());

        resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + APP_ID1 + PACKAGE_ID1)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        result = resultActions.andReturn();
        obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }*/

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void onBoardAppPackageWithInvalidAppPackageId() throws Exception {
        String request = "{\n"
                + "  \"appIconUrl\": \"http://1.1.1.1:1234/mec\",\n"
                + "  \"appId\": \"f40358433cf8eb4719a62a49ed118c9c\",\n"
                + "  \"appPkgAffinity\": \"GPU\",\n"
                + "  \"appPkgDesc\": \"face recognition application\",\n"
                + "  \"appPkgId\": \"f40358-433cf8eb4719a62a49ed118c9b\",\n"
                + "  \"appPkgName\": \"codelab-demo1\",\n"
                + "  \"appPkgPath\": \"http://1.1.1.1:1234/mec/appstore/v1/apps/8ec923a8-9e30-4c94-a7ac-c92279488db2"
                + "/packages/0fb274f2-213b-4a66-accc-ab218470caa3/action/download\",\n"
                + "  \"appPkgVersion\": \"1.0\",\n"
                + "  \"appProvider\": \"huawei\",\n"
                + "  \"createdTime\": \"Thu Nov 21 16:02:24 CST 2019\",\n"
                + "  \"mecHostInfo\": [\n"
                + "    { \"hostIp\" : \"1.1.1.1\"}\n"
                + "  ],\n"
                + "  \"modifiedTime\": \"Thu Nov 21 16:02:24 CST 2019\"\n"
                + "}";

        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.post("/apm/v1/tenants/" + TENANT_ID
                        + "/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).with(csrf())
                        .header("access_token", "aasdjk")
                        .content(request))
                        .andExpect(MockMvcResultMatchers.status().isBadRequest());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void getAppPackageInfoWithInvalidTenantId() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + "12"
                        + "/packages/" + PACKAGE_ID1)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isBadRequest());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void getAppPackageInfoWithInvalidPackageId() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + "f70358433cf8eb4719a62a49ed118c9b")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isBadRequest());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }


    private void syncAppInstanceInfos(MockRestServiceServer server) throws Exception {
        // Mocking get MEC host from inventory
        String url = "https://1.1.1.1:8080/inventory/v1/tenants/" + TENANT_ID + "/mechosts/";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"mechostIp\":\"1.1.1.1\",\"mechostName\":\"TestHost\","
                                + "\"zipCode\":null,"
                                + "\"city\":\"TestCity\","
                                + "\"address\":\"Test Address\",\"affinity\":\"part1,part2\",\"userName\":null,\"edgerepoName\":null,"
                                + "\"edgerepoIp\":\"1.1.1.1\",\"edgerepoPort\":\"10000\",\"edgerepoUsername\":null,"
                                + "\"mepmIp\":\"1.1.1.1\"}]",
                        MediaType.APPLICATION_JSON)); // host response , json response, mepm ip ... use mepm url

        // Mocking get mepm from inventory
        url = "https://1.1.1.1:8080/inventory/v1/mepms/1.1.1"
                + ".1";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"mepmIp\":\"1.1.1.1\",\"mepmPort\":\"10000\",\"userName\":\"Test\"}",
                        MediaType.APPLICATION_JSON)); /// validate response , use this query , // mepm port ,

        // Mocking get deleted app instance infos API
        url = "https://1.1.1.1:10000/lcmcontroller/v2/tenants/" + TENANT_ID + "/packages/" +
                "sync_deleted";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        // Mocking get updated app instance infos API
        url = "https://1.1.1.1:10000/lcmcontroller/v2/tenants/" + TENANT_ID + "/packages/" +
                "sync_updated";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void syncAppInstanceInfosTest() throws Exception {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        syncAppInstanceInfos(mockServer);

        // Sync app instance info
        ResultActions postResult =
                mvc.perform(MockMvcRequestBuilders.post(APM_TENANT + TENANT_ID + "/app_package_infos/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).with(csrf())
                        .header(ACCESS_TOKEN, SAMPLE_TOKEN));
        MvcResult postMvcResult = postResult.andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andReturn();
    }
}