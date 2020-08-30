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

import static org.edgegallery.mecm.apm.utils.Constants.HOST_IP_REGX;
import static org.edgegallery.mecm.apm.utils.Constants.MAX_COMMON_STRING_LENGTH;
import static org.edgegallery.mecm.apm.utils.Constants.MAX_DETAILS_STRING_LENGTH;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Mec host schema.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MecHostDto {

    @NotEmpty(message = "hostIp must not be empty")
    @Pattern(regexp = HOST_IP_REGX, message = "host ip must match pattern ^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]"
            + "|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")
    private String hostIp;

    @Size(max = MAX_COMMON_STRING_LENGTH, message = "status must not exceed more than 255 characters")
    private String status;

    @Size(max = MAX_DETAILS_STRING_LENGTH, message = "error must not exceed more than 1024 characters")
    private String error;
}
