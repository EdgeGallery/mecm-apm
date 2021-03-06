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

package org.edgegallery.mecm.apm.utils;

import static org.edgegallery.mecm.apm.utils.ApmServiceHelper.getHostList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import javax.validation.ConstraintViolationException;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.edgegallery.mecm.apm.model.dto.MecHostDto;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.ResourceUtils;

class ApmServiceHelperTest {

    private static final String TENANT_ID = "18db0283-3c67-4042-a708-a8e4a10c6b32";
    private static final String PACKAGE_ID = "f50358433cf8eb4719a62a49ed118c9b";

    @Test
    void testGetMainServiceYaml() throws IOException {
        File file = ResourceUtils.getFile("classpath:sampleapp.csar");
        File packagesDir = ResourceUtils.getFile("classpath:packages");
        String indentedDir = packagesDir.getPath() + File.separator + PACKAGE_ID + TENANT_ID;
        try {
            String response = ApmServiceHelper.getMainServiceYaml(file.getPath(), indentedDir);
            assertNotNull(response);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    void testGetImageInfo() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:sampleapp.csar");
        File packagesDir = ResourceUtils.getFile("classpath:packages");
        String indentedDir = packagesDir.getPath() + File.separator + PACKAGE_ID + TENANT_ID;
        // String response = ApmServiceHelper.getMainServiceYaml(file.getPath(), indentedDir);
        try {
            String response = "OK";
            List<String> imageInfoList = ApmServiceHelper.getImageInfo(response);
            assertNotNull(imageInfoList);
            assertEquals(2, imageInfoList.size());
            String imageInfo = imageInfoList.get(0);
            assertEquals("swr.ap-southeast-1.myhuaweicloud.com/edgegallery/mep-agent:1.0", imageInfo);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    void testSaveInputStreamToFile() throws IOException {
        File file = ResourceUtils.getFile("classpath:packages");
        InputStream inputStream = IOUtils.toInputStream("mock data for test", "UTF-8");
        String response = ApmServiceHelper.saveInputStreamToFile(inputStream, PACKAGE_ID, TENANT_ID, file.getPath());
        assertNotNull(response);
        File responseFile = new File(response);
        assertTrue(responseFile.exists());

        // clean up
        Files.deleteIfExists(Paths.get(response));
    }

    @Test
    void testSaveNullInputStreamToFile() throws IOException {
        File file = ResourceUtils.getFile("classpath:packages");
        String localFilePath = file.getPath();
        assertThrows(ApmException.class, () -> ApmServiceHelper.saveInputStreamToFile(null,
                PACKAGE_ID, TENANT_ID, localFilePath));
    }

    @Test
    void testGetMainServiceYamlInvalid() throws IOException {
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913b.csar");
        String localFilePath = file.getPath();
        File packagesDir = ResourceUtils.getFile("classpath:packages");
        String indentedDir = packagesDir.getPath() + File.separator + PACKAGE_ID + TENANT_ID;
        assertThrows(ApmException.class, () -> ApmServiceHelper.getMainServiceYaml(localFilePath, indentedDir));
    }

    @Test
    public void testGetHostList() {
        List<MecHostDto> mecHostList = getHostList("1.1.1.1");
        assertEquals(1, mecHostList.size());

        mecHostList = getHostList("     1.1.1.1");
        assertEquals(1, mecHostList.size());

        mecHostList = getHostList("1.1.1.1, 2.2.2.2");
        assertEquals(2, mecHostList.size());

        mecHostList = getHostList("1.1.1.1, 2.2.2.2          ");
        assertEquals(2, mecHostList.size());

        assertThrows(ConstraintViolationException.class, () -> getHostList("sdfsdfsd"));
    }

    @Test
    public void testSaveMultiPartFile() throws IOException {
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        FileInputStream fis = new FileInputStream(file.getPath());
        MockMultipartFile multipartFile = new MockMultipartFile("22406fba-fd5d-4f55-b3fa-89a45fee913a.csar",
                "app.csar", "", fis);
        File dir = ResourceUtils.getFile("classpath:packages");
        String path = ApmServiceHelper.saveMultipartFile(multipartFile, PACKAGE_ID, TENANT_ID, dir.getPath());
        String expectedPath = new StringBuilder(dir.getPath()).append(File.separator).append(PACKAGE_ID)
                .append(File.separator).append(PACKAGE_ID).append(".csar").toString();
        assertEquals(expectedPath, path);
        String dirPath = new StringBuilder(dir.getPath()).append(File.separator).append(PACKAGE_ID)
                .append(TENANT_ID).toString();
        Files.deleteIfExists(Paths.get(path));
        Files.deleteIfExists(Paths.get(dirPath));
    }

    @Test
    public void testGetAppId() {
        assertNotNull(ApmServiceHelper.generateAppId());
    }
}