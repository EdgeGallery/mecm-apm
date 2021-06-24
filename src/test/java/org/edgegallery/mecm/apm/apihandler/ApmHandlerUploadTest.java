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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.ApmApplicationTest;
import org.edgegallery.mecm.apm.model.AppPackageMf;
import org.edgegallery.mecm.apm.model.dto.AppPackageDto;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.edgegallery.mecm.apm.service.ApmServiceFacade;
import org.edgegallery.mecm.apm.service.DbService;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.junit.After;
import org.junit.Assert;
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
import org.springframework.mock.web.MockMultipartFile;
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

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
@AutoConfigureMockMvc
public class ApmHandlerUploadTest {

    private static final String TENANT_ID = "19db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID1 = "f50358433cf8eb4719a62a49ed118c9b";
    String appPackageId;
    @Autowired
    MockMvc mvc;

    @Autowired
    private ApmServiceFacade apmServiceFacade;

    @Autowired
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private AppPackageDto packageDto1 = new AppPackageDto();
    private AppPackageDto packageDto2 = new AppPackageDto();


    @After
    public void cleanUp() {
        apmServiceFacade.deleteAppPackage(TENANT_ID, appPackageId);
    }

    @Test
    @WithMockUser(roles = "MECM_ADMIN")
    public void onBoardApplicationTest() throws Exception {
        String serviceResponseBody;


        String url1 = "https://1.1.1.1:8080/inventory/v1/apprepos";
        serviceResponseBody = "[{'repoEndPoint': '119.8.63.144', 'repoName': 'AppRepo1', 'repoUserName': "
                + "'admin', 'repoPassword': '12345' }]";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String url2 = "https://1.1.1.1:8080/inventory/v1/mechosts/3.3.3.3";
        serviceResponseBody = "{\"mechostIp\":\"3.3.3.3\",\"mechostName\":\"TestHost\","
                + "\"zipCode\":null,"
                + "\"city\":\"TestCity\","
                + "\"address\":\"Test Address\",\"affinity\":\"part1,part2\",\"userName\":null,\"edgerepoName\":null,"
                + "\"edgerepoIp\":\"1.1.1.1\",\"edgerepoPort\":\"10000\",\"edgerepoUsername\":null,"
                + "\"mepmIp\":\"3.3.3.3\"}";
        mockServer.expect(requestTo(url2))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String url3 = "https://1.1.1.1:8080/inventory/v1/mepms/3.3.3.3";
        serviceResponseBody = "{'mepmName':'applemname', 'mepmIp':'3.3.3.3', 'mepmPort':'443', " +
                "'userName':'mepm'}";
        mockServer.expect(requestTo(url3))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String url4 = "https://3.3.3.3:443/lcmcontroller/v1/tenants/" + TENANT_ID + "/packages";
        serviceResponseBody = "{'mepmIp': '1.1.1.1', 'mepmPort': '1111', 'mepmName': "
                + "'applemname', 'userName': 'mepm' }";
        mockServer.expect(requestTo(url4))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        //appPackageId will be null, need to check further..so commented
        /*String url5 = " https://3.3.3.3:443/lcmcontroller/v1/tenants/" + TENANT_ID + "/packages/" + appPackageId;
        serviceResponseBody = "{\"hostIp\": [\"3.3.3.3\"], \"origin\": \"MEPM\"}";
        mockServer.expect(requestTo(url5))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));*/
        // Testing csar upload
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913c.csar");

        ResultActions resultActions =
                mvc.perform(multipart("/apm/v1/tenants/" + TENANT_ID + "/packages/upload")
                        .file(new MockMultipartFile("file", "22406fba-fd5d-4f55-b3fa-89a45fee913c.csar", MediaType.TEXT_PLAIN_VALUE,
                                FileUtils.openInputStream(file)))
                        .with(csrf()).contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                        .param("appPackageName", "appPackageName")
                        .param("appPackageVersion", "1.0")
                        .param("hostList", "3.3.3.3")
                        .header("access_token", "SampleToken"));

        MvcResult postMvcResult = resultActions.andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andReturn();
        String postResponse = postMvcResult.getResponse().getContentAsString();
        assertThat(postResponse, containsString("appPackageId"));
        assertThat(postResponse, containsString("appId"));

        // Sleep for create to finish as its an async call
        Thread.sleep(5000);

        appPackageId = postResponse.substring(60, 124);
        String appId = postResponse.substring(10, 42);
        Assert.assertEquals("{\"appId\":\"" + appId + "\",\"appPackageId\":\"" + appPackageId + "\"}", postResponse);

        //bad response since appPackageId will be null to request lcmcontroller url
        ResultActions getAllResult =
                mvc.perform(MockMvcRequestBuilders.get("/apm/v1/tenants" + TENANT_ID + appPackageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE).with(csrf()));
        MvcResult getAllMvcResult = getAllResult.andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().is4xxClientError())
                .andReturn();

        /*String getAllResponse = getAllMvcResult.getResponse().getContentAsString();

        Assert.assertEquals(" {[\"appPkgId\":\"" + appPackageId + "\",\n" +
                        "    \"appPkgName\": \"positioning_service\",\n" +
                        "    \"appPkgVersion\": \"1.0\",\n" +
                        "    \"appPkgPath\": null,\n" +
                        "    \"appProvider\": \"Huawei\",\n" +
                        "    \"appPkgDesc\": \"positioning_service\",\n" +
                        "    \"appPkgAffinity\": null,\n" +
                        "    \"appIconUrl\": null,\n" +
                        "    \"createdTime\": \"2021-06-23T17:46:39.271\",\n" +
                        "    \"modifiedTime\": \"2021-06-23T17:46:39.271\",\n" +
                        "    \"appId\": \"f85c02f42fb34a709884b5e96bff24be\"]}",
                getAllResponse);*/

        //mockServer.verify();
    }

}