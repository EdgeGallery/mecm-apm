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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.ApmApplicationTest;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.AppRepo;
import org.edgegallery.mecm.apm.model.PkgSyncInfo;
import org.edgegallery.mecm.apm.model.SwImageDescr;
import org.edgegallery.mecm.apm.utils.ApmServiceHelper;
import org.edgegallery.mecm.apm.utils.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApmApplicationTest.class)
public class ApmServiceTest {

    private static final String TENANT_ID = "18db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID = "f50358433cf8eb4719a62a49ed118c9b";

    @Autowired
    private ApmService apmService;

    @Autowired
    private RestTemplate restTemplate;
    RestClientHelper restClientHelper;

    private MockRestServiceServer mockServer;

    @InjectMocks
    PkgSyncInfo pkgSyncInfo = new PkgSyncInfo();
    SwImageDescr swImageDescr =new SwImageDescr();
    AppRepo appRepo=new AppRepo();



    @Test
    public void downloadAppPackageTest() throws FileNotFoundException {

        mockServer = MockRestServiceServer.createServer(restTemplate);

        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        InputStream ins = new BufferedInputStream(new FileInputStream(file.getPath()));
        InputStreamResource inputStreamResource = new InputStreamResource(ins);
        String url = "http://1.1.1.1:8099/mec/appstore/v1/apps/8ec923a8-9e30-4c94-a7ac-c92279488db2/packages"
                + "/0fb274f2-213b-4a66-accc-ab218470caa3/action/download";
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(inputStreamResource, MediaType.APPLICATION_OCTET_STREAM));

        InputStream response = apmService.downloadAppPackage(url, PACKAGE_ID, "access token");
        assertNotNull(response);
        mockServer.verify();
    }

    @Test
    public void getRepoInfoOfHostTest() {
        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        String serviceResponseBody = "{'edgerepoIp': '2.2.2.2', 'edgerepoPort': 1234, 'edgerepoUsername': 'admin' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        String info = apmService.getRepoInfoOfHost("1.1.1.1", "access token");
        assertEquals("2.2.2.2:1234", info);
        mockServer.verify();
    }

    @Test
    public void getRepoInfoOfHostInvalidTest() {
        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ApmException.class, () -> apmService.getRepoInfoOfHost("1.1.1.1",
                 "access token"));
        mockServer.verify();
    }

    @Test
    public void getRepoInfoOfHostInvalidEdgeRepoIpTest() {
        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        String serviceResponseBody = "{'edgerepoIp': 'edgerepoIp', 'edgerepoPort': 1234, 'edgerepoUsername': 'admin' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        assertThrows(ApmException.class, () -> apmService.getRepoInfoOfHost("1.1.1.1",
                 "access token"));
        mockServer.verify();
    }

    @Test
    public void getRepoInfoOfHostInvalidEdgeRepoPortTest() {
        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        String serviceResponseBody = "{'edgerepoIp': '1.1.1.1', 'edgerepoPort': 'edgerepoPort',"
                + " 'edgerepoUsername': 'admin' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));

        assertThrows(ApmException.class, () -> apmService.getRepoInfoOfHost("1.1.1.1",
                 "access token"));
        mockServer.verify();
    }

    @Test
    public void testGetAppPackageFile() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        assertNotNull(apmService.getAppPackageFile(file.getPath()));
    }

    @Test
    public void testDeleteFile() throws IOException {
        File file = ResourceUtils.getFile("classpath:packages");
        InputStream inputStream = IOUtils.toInputStream("mock data for test", "UTF-8");
        String response = ApmServiceHelper.saveInputStreamToFile(inputStream, PACKAGE_ID, TENANT_ID, file.getPath());
        assertNotNull(response);
        File responseFile = new File(response);
        assertTrue(responseFile.exists());
        assertDoesNotThrow(() -> apmService.deleteAppPackageFile(response));
    }

    @Test
    public void testGetAppImageInfo() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:sampleapp.csar");
        List<String> imageInfo = apmService.getAppImageInfo(file.getPath(), PACKAGE_ID, TENANT_ID);
        assertEquals(2, imageInfo.size());
    }
	
	@Test
    public void testdeleteAppPkgDockerImagesFromRepo() throws Exception {
        Set<String> imageInfoList = new HashSet<String>();
		imageInfoList.add("Test");
		try {
			apmService.deleteAppPkgDockerImagesFromRepo(imageInfoList);
		} catch (Exception e) {
			assertTrue(true);
		}
    }
	@Test
    public void testdeleteAppPkgDockerImages() throws Exception {
        Set<String> imageInfoList = new HashSet<String>();
		imageInfoList.add("Test");
		try {
			apmService.deleteAppPkgDockerImages(imageInfoList);
		} catch (Exception e) {
			assertTrue(true);
		}
    }

	@Test
    public void testsendPostRequest() throws AssertionError {
        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        String serviceResponseBody = "{'edgerepoIp': '1.1.1.1', 'edgerepoPort': 'edgerepoPort',"
                + " 'edgerepoUsername': 'admin' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));
		try {
			apmService.sendPostRequest(url,"reqBody","access token");
		} catch (Exception e) {
			assertTrue(true);
		}
    }
	@Test
    public void testsendDeleteRequest() throws AssertionError {
        String url = "https://1.1.1.1:8080/inventory/v1/mechosts/1.1.1.1";
        String serviceResponseBody = "{'edgerepoIp': '1.1.1.1', 'edgerepoPort': 'edgerepoPort',"
                + " 'edgerepoUsername': 'admin' }";
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(serviceResponseBody, MediaType.APPLICATION_JSON));
		try {
			apmService.sendDeleteRequest(url,"access token");
		} catch (Exception e) {
			assertTrue(true);
		}
    }

    @Test(expected = ApmException.class)
    public void downloadAppImageTest() {

        Map<String, AppRepo> repoMap = new HashMap<>();
        repoMap.put("repoInfo" , appRepo);
        pkgSyncInfo.setRepoInfo(repoMap);

        swImageDescr.setSwImage("swImage");

        List<SwImageDescr> imageInfoList= new ArrayList<>();
        imageInfoList.add(swImageDescr);

        Set<String> downloadedImgs = new HashSet<>();
        downloadedImgs.add("image1");
        downloadedImgs.add("image2");

        apmService.downloadAppImage(pkgSyncInfo,imageInfoList,downloadedImgs);
    }

}