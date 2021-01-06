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

import java.io.File;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.ResourceUtils;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
@AutoConfigureMockMvc
public class ApmHandlerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    private DbService dbService;

    @Autowired
    private ApmServiceFacade apmServiceFacade;

    private static final String TENANT_ID = "19db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID1 = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String PACKAGE_ID2 = "f60358433cf8eb4719a62a49ed118c9b";
    private static final String TIME = "Thu Nov 21 16:02:24 CST 2019";

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
        packageDto.setAppPkgId(packageId);
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
    @WithMockUser(roles = "MECM_TENANT")
    public void onBoardAppPackage() throws Exception {
        String request = "{\n"
                + "  \"appIconUrl\": \"http://1.1.1.1:1234/mec\",\n"
                + "  \"appId\": \"f40358433cf8eb4719a62a49ed118c9c\",\n"
                + "  \"appPkgAffinity\": \"GPU\",\n"
                + "  \"appPkgDesc\": \"face recognition application\",\n"
                + "  \"appPkgId\": \"f40358433cf8eb4719a62a49ed118c9b\",\n"
                + "  \"appPkgName\": \"正在参-加开源.中国举_办的\",\n"
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
                        .accept(MediaType.APPLICATION_JSON)
                        .header("access_token", "aasdjk")
                        .content(request))
                        .andExpect(MockMvcResultMatchers.status().isAccepted());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertEquals("{\"packageId\":\"f40358433cf8eb4719a62a49ed118c9b\"}", obj.getContentAsString());
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
                        .accept(MediaType.APPLICATION_JSON)
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
                        + "/packages/" + PACKAGE_ID1)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void deleteAppPackage() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.delete("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + PACKAGE_ID1)
                        .accept(MediaType.TEXT_PLAIN_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertEquals("success", obj.getContentAsString());

        mockAppPackageDto(PACKAGE_ID1, packageDto1);
        mockOnboardPackage(packageDto1);
    }

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void downloadAppPackage() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + PACKAGE_ID1 + "/download")
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

    @Test
    @WithMockUser(roles = "MECM_TENANT")
    public void deleteAppPackageInHost() throws Exception {
        ResultActions resultActions =
                mvc.perform(MockMvcRequestBuilders.delete("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + PACKAGE_ID1 + "/hosts/1.1.1.1")
                        .accept(MediaType.TEXT_PLAIN_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse obj = result.getResponse();
        assertEquals("success", obj.getContentAsString());

        resultActions =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants/" + TENANT_ID
                        + "/packages/" + PACKAGE_ID1)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk());
        result = resultActions.andReturn();
        obj = result.getResponse();
        assertNotNull(obj.getContentAsString());
    }

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
                        .accept(MediaType.APPLICATION_JSON)
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
}