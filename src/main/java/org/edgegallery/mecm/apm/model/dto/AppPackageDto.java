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

import static org.edgegallery.mecm.apm.utils.Constants.MAX_COMMON_STRING_LENGTH;

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
import org.edgegallery.mecm.apm.apihandler.validator.ConstraintType;
import org.edgegallery.mecm.apm.apihandler.validator.CustomConstraint;
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
    @Size(max = Constants.MAX_COMMON_ID_LENGTH, message = "appPkgId must not exceed more than 64 characters")
    @Pattern(regexp = Constants.APP_PKG_ID_REGX, message = "appPkgId is invalid. It must be smaller case letters or "
            + "numbers with length of 32 characters.")
    private String appPkgId;

    @NotEmpty(message = "appPkgName must not be empty")
    @Size(max = MAX_COMMON_STRING_LENGTH, message = "appPkgName must not exceed more than 255 characters")
    @Pattern(regexp = Constants.APP_NAME_REGEX, message = "appPkgName is invalid. It must start with alpha numeric "
            + "characters and special characters allowed are hyphen and underscore.")
    private String appPkgName;

    @NotEmpty(message = "appPkgVersion must not be empty")
    @Size(max = MAX_COMMON_STRING_LENGTH, message = "appPkgName must not exceed more than 255 characters")
    private String appPkgVersion;

    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "appPkgPath must not exceed more than 1024 characters")
    @CustomConstraint(value = ConstraintType.URL, message = "invalid app package path")
    private String appPkgPath;

    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "appProvider must not exceed more than 1024 characters")
    private String appProvider;

    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "appPkgDesc must not exceed more than 1024 characters")
    private String appPkgDesc;

    @Size(max = MAX_COMMON_STRING_LENGTH, message = "appPkgAffinity must not exceed more than 255 characters")
    private String appPkgAffinity;

    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "appIconUrl must not exceed more than 1024 characters")
    private String appIconUrl;

    @Size(max = MAX_COMMON_STRING_LENGTH, message = "createdTime must not exceed more than 255 characters")
    private String createdTime;

    @Size(max = MAX_COMMON_STRING_LENGTH, message = "modifiedTime must not exceed more than 255 characters")
    private String modifiedTime;

    @NotEmpty(message = "appId must not be empty")
    @Size(max = Constants.MAX_COMMON_ID_LENGTH, message = "appId must not exceed more than 64 characters")
    @Pattern(regexp = Constants.APPD_ID_REGEX, message = "appId is invalid. It must be smaller case letters "
            + "or numbers with maximum length of 32 characters.")
    private String appId;

    @NotEmpty(message = "mecHost info must not be empty")
    private List<@Valid MecHostDto> mecHostInfo = new LinkedList<>();
}
