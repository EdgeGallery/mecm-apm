/*
 *    Copyright 2020 Huawei Technologies Co., Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.edgegallery.mecm.apm.utils;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.edgegallery.mecm.apm.service.ApmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public final class FileChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApmService.class);

    private static final String REG
            = "[^\\s\\\\/:*?\"<>|](\\x20|[^\\s\\\\/:*?\"<>|])*[^\\s\\\\/:*?\"<>|.]$";

    private static final int MAX_LENGTH_FILE_NAME = 255;
    private static final long MAX_ZIP_FILE_SIZE = 50 * 1024 * 1024L;
    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s");

    private FileChecker() {
    }

    /**
     * Checks file if is invalid.
     *
     * @param file object.
     */
    public static void check(File file) {
        String fileName = file.getName();

        // file name should not contains blank.
        if (fileName != null && WHITE_SPACE_PATTERN.split(fileName).length > 1) {
            throw new IllegalArgumentException(fileName + Constants.FILENAME_BLANK);
        }

        if (!isAllowedFileName(fileName)) {
            throw new IllegalArgumentException(fileName + Constants.FILENAME_ILLEGAL);
        }

        if (file.length() > MAX_ZIP_FILE_SIZE) {
            throw new IllegalArgumentException(fileName + Constants.FILE_SIZE_TOO_BIG);
        }
    }

    /**
     * Checks file if is invalid.
     *
     * @param file object.
     */
    public static void check(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        LOGGER.info(fileName + Constants.FILENAME);

        // file name should not contains blank.
        if (fileName != null && WHITE_SPACE_PATTERN.split(fileName).length > 1) {
            LOGGER.error(fileName + Constants.FILENAME_BLANK);
            throw new IllegalArgumentException(fileName + Constants.FILENAME_BLANK);
        }

        if (!isAllowedFileName(fileName)) {
            LOGGER.error(fileName + Constants.FILENAME_ILLEGAL);
            throw new IllegalArgumentException(fileName + Constants.FILENAME_ILLEGAL);
        }

        if (file.getSize() > MAX_ZIP_FILE_SIZE) {
            LOGGER.error(fileName + Constants.FILE_SIZE_TOO_BIG);
            throw new IllegalArgumentException(fileName + Constants.FILE_SIZE_TOO_BIG);
        }
    }

    static boolean isAllowedFileName(String originalFilename) {
        return isValid(originalFilename)
                && ("csar".equals(Files.getFileExtension(originalFilename.toLowerCase()))
                || "zip".equals(Files.getFileExtension(originalFilename.toLowerCase())));
    }

    static boolean isValid(String fileName) {
        if (StringUtils.isEmpty(fileName) || fileName.length() > MAX_LENGTH_FILE_NAME) {
            return false;
        }
        fileName = Normalizer.normalize(fileName, Normalizer.Form.NFKC);
        return Pattern.compile(REG).matcher(fileName).matches();
    }

    /**
     * Sanitizes the file name.
     *
     * @param entryName entry name
     * @param intendedDir intended path
     * @return String
     * @throws IOException exception if failed to sanitize the file
     */
    public static String sanitizeFileName(String entryName, String intendedDir) throws IOException {
        File f = new File(intendedDir, entryName);
        String canonicalPath = f.getCanonicalPath();
        File intendDir = new File(intendedDir);
        if (intendDir.isDirectory() && !intendDir.exists()) {
            createFile(intendedDir);
        }
        String canonicalID = intendDir.getCanonicalPath();
        if (canonicalPath.startsWith(canonicalID)) {
            return canonicalPath;
        } else {
            throw new IllegalStateException("file is outside extraction target directory.");
        }
    }

    static void createFile(String filePath) throws IOException {
        File tempFile = new File(filePath);
        boolean result = false;

        if (!tempFile.getParentFile().exists() && !tempFile.isDirectory()) {
            result = tempFile.getParentFile().mkdirs();
        }
        if (!tempFile.exists() && !tempFile.isDirectory() && !tempFile.createNewFile() && !result) {
            throw new IllegalArgumentException("create temp file failed");
        }
    }
}