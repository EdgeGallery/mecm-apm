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

package org.edgegallery.mecm.apm.model.dto;

import java.util.LinkedList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.edgegallery.mecm.apm.utils.Constants;

/**
 * Application package schema.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AppPackageDto {

    @NotEmpty(message = "appPkgId must not be empty")
    @Pattern(regexp = Constants.APP_PKG_ID_REGX, message = "appPkgId must match pattern [0-9a-f]{32}")
    private String appPkgId;

    @NotEmpty(message = "appPkgName must not be empty")
    @Size(max = Constants.MAX_COMMON_STRING_LENGTH, message = "appPkgName must not exceed more than 255 characters")
    @Pattern(regexp = Constants.APP_NAME_REGEX, message = "appPkgName must match pattern ^[a-zA-Z0-9]*$|^[a-zA-Z0-9]"
            + "[a-zA-Z0-9_\\\\-]*[a-zA-Z0-9]$\"")
    private String appPkgName;

    @NotEmpty(message = "appPkgVersion must not be empty")
    @Size(max = Constants.MAX_COMMON_STRING_LENGTH, message = "appPkgName must not exceed more than 255 characters")
    private String appPkgVersion;

    @NotEmpty(message = "appPkgPath must not be empty")
    @Pattern(regexp = Constants.URI_REGEX, message = "appPkgPath must match pattern ^(([^:/?#]+):)?(//([^/?#]*))?"
            + "([^?#]*)(\\?([^#]*))?(#(.*))?")
    private String appPkgPath;

    @NotEmpty(message = "appProvider must not be empty")
    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "appProvider must not exceed more than 1024 characters")
    private String appProvider;

    @NotEmpty(message = "appPkgDesc must not be empty")
    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "appPkgDesc must not exceed more than 1024 characters")
    private String appPkgDesc;

    @NotEmpty(message = "appPkgAffinity must not be empty")
    @Size(max = Constants.MAX_COMMON_STRING_LENGTH, message = "appPkgAffinity must not exceed more than 255 characters")
    private String appPkgAffinity;

    @NotEmpty(message = "appIconUrl must not be empty")
    @Pattern(regexp = Constants.URI_REGEX, message = "Url must match pattern ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)"
            + "(\\?([^#]*))?(#(.*))?")
    private String appIconUrl;

    @NotEmpty(message = "createdTime must not be empty")
    private String createdTime;

    @NotEmpty(message = "modifiedTime must not be empty")
    private String modifiedTime;

    @NotEmpty(message = "appId must not be empty")
    @Pattern(regexp = Constants.APPD_ID_REGEX, message = "appId must match pattern [0-9a-f]{32}")
    private String appId;

    @NotEmpty(message = "mecHost info must not be empty")
    private List<@Valid MecHostDto> mecHostInfo = new LinkedList<>();
}
