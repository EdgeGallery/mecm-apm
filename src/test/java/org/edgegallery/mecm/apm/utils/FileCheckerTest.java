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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

class FileCheckerTest {

    @Test
    void testValidFile() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:22406fba-fd5d-4f55-b3fa-89a45fee913a.csar");
        assertDoesNotThrow(() -> FileChecker.check(file));
    }

    @Test
    void testCreateFile() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:packages");
        assertDoesNotThrow(() -> FileChecker.createFile(file.getPath()));
    }

    @Test
    void testWhiteSpaceInFileName()  {
        File file = new File("ab c.txt");
        assertThrows(IllegalArgumentException.class, () -> FileChecker.check(file));

        File newfile = new File("abcccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                + "ccccccccccccccc.txt");
        assertThrows(IllegalArgumentException.class, () -> FileChecker.check(newfile));
    }
}