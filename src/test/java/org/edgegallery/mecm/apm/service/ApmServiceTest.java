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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ApmServiceTest {

    @Autowired
    private ApmService apmService;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Test
    public void getRepoInfoOfHostTest() {
        String url = "https://1.1.1.1:8080/inventory/v1/tenants/18db0283-3c67-4042-a708-a8e4a10c6b32/mechosts/1.1.1.1";
        String serviceResponseBody = "{'edgeRepoIp': '2.2.2.2', 'edgeRepoPort': 1234 }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String response = apmService.getRepoInfoOfHost("1.1.1.1",  "18db0283-3c67-4042-a708-a8e4a10c6b32");
        assertEquals("2.2.2.2:1234", response);
    }
}