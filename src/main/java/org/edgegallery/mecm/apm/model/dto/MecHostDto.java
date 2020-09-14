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
 * Mec host schema.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MecHostDto {

    @NotEmpty(message = "hostIp must not be empty")
    @Size(max = Constants.MAX_COMMON_IP_LENGTH, message = "host ip address must not exceed more than 15 characters")
    @Pattern(regexp = Constants.HOST_IP_REGX, message = "host ip address is invalid")
    private String hostIp;

    @Size(max = Constants.MAX_COMMON_STRING_LENGTH, message = "status must not exceed more than 255 characters")
    private String status;

    @Size(max = Constants.MAX_DETAILS_STRING_LENGTH, message = "error must not exceed more than 1024 characters")
    private String error;
}
