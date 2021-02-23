/*
 *  Copyright 2021 Huawei Technologies Co., Ltd.
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
 * Sync application package schema.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SyncAppPackageDto {

    @NotEmpty(message = "appstoreIp must not be empty")
    @Size(max = Constants.MAX_COMMON_IP_LENGTH, message = "appstore ip address must not exceed more than 15 characters")
    @Pattern(regexp = Constants.HOST_IP_REGX, message = "appstore ip address is invalid")
    private String appstoreIp;

    @NotEmpty(message = "packageId must not be empty")
    @Size(max = Constants.MAX_COMMON_ID_LENGTH, message = "appPkgId must not exceed more than 64 characters")
    @Pattern(regexp = Constants.APP_PKG_ID_REGX, message = "appPkgId is invalid. It must be smaller case letters or "
            + "numbers with length of 32 characters.")
    private String packageId;

    @NotEmpty(message = "appId must not be empty")
    @Size(max = Constants.MAX_COMMON_ID_LENGTH, message = "appId must not exceed more than 64 characters")
    @Pattern(regexp = Constants.APPD_ID_REGEX, message = "appId is invalid. It must be smaller case letters "
            + "or numbers with maximum length of 32 characters.")
    private String appId;
}
