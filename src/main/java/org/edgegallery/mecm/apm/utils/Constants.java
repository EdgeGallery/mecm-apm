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

public final class Constants {

    public static final String HOST_IP_REGX = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.)"
            + "{3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
    public static final String APPD_ID_REGEX = "[0-9a-f]{32}";
    public static final String APP_PKG_ID_REGX = APPD_ID_REGEX;
    public static final String TENENT_ID_REGEX = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    public static final String APP_NAME_REGEX = "^[a-zA-Z0-9]*$|^[a-zA-Z0-9][a-zA-Z0-9_\\-]*[a-zA-Z0-9]$";
    public static final String RECORD_NOT_FOUND = "Record not found";
    public static final String EMPTY_STRING = "";
    public static final int MAX_COMMON_STRING_LENGTH = 255;
    public static final int MAX_DETAILS_STRING_LENGTH = 1024;
    public static final String URI_REGEX = "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";

    public static final String FAILED_TO_CONNECT_APPSTORE = "failed to connect to app store";
    public static final String FAILED_TO_CONNECT_INVENTORY = "failed to connect to inventory";
    public static final String CSAR_NOT_EXIST = "csar package file does not exists";
    public static final String CSAR_READ_FAILED = "failed to read the csar file";
    public static final String CSAR_DOWNLOAD_FAILED = "failed to download app package for package {}";
    public static final String FAILED_TO_GET_REPO_INFO = "failed to get repository information of host {}";
    public static final String REPO_INFO_NULL = "edge nexus repository information is null for host {}";
    public static final String LOCAL_FILE_PATH_NULL = "local file path is null";
    public static final String DISTRIBUTION_FAILED = "failed to distribute the package {}";
    public static final String DISTRIBUTION_IN_HOST_FAILED = "failed to distribute the package {} in host {}";
    public static final String ERROR_IN_UPDATING_LOCAL_FILE_PATH = "error occurred while updating local file path in "
            + "db for package {}";
    public static final String FAILED_TO_READ_INPUTSTREAM = "failed to read input stream from app store for package {}";
    public static final String FAILED_TO_CREATE_CSAR = "failed to create csar file for package {}";
    public static final String FAILED_TO_UNZIP_CSAR = "failed to unzip the csar file";
    public static final String FAILED_TO_CONVERT_YAML_TO_JSON = "failed to convert main service yaml to json";
    public static final String SERVICE_YAML_NOT_FOUND = "main service yaml not found in CSAR";
    public static final String FAILED_TO_CREATE_DIR = "failed to create local directory";
    public static final String FAILED_TO_GET_FAIL_PATH = "failed to get local directory path";

    private Constants() {
    }
}