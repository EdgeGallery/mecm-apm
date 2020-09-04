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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.edgegallery.mecm.apm.model.ImageInfo;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

class ApmServiceHelperTest {

    @Test
    void testGetMainServiceYaml() throws IOException {
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        String response = ApmServiceHelper.getMainServiceYaml(file.getPath());
        assertNotNull(response);
    }

    @Test
    void testGetImageInfo() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        String response = ApmServiceHelper.getMainServiceYaml(file.getPath());
        List<ImageInfo> imageInfoList = ApmServiceHelper.getImageInfo(response);
        assertNotNull(imageInfoList);
        assertEquals(3, imageInfoList.size());
        ImageInfo imageInfo = imageInfoList.get(0);
        assertEquals("template_app", imageInfo.getName());
        assertEquals("v1.4", imageInfo.getVersion());
    }
}