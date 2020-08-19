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

package org.edgegallery.mecm.apm.model;

import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Mec host schema.
 */
@Getter
@Setter
@ToString
public class MecHostDto {

    @NotEmpty(message = "hostIp is empty")
    private String hostIp;

    @NotEmpty(message = "status is empty")
    private String status;

    /**
     * Constructor to create MecHostDto.
     *
     * @param hostIp host ip
     * @param status distribution status
     */
    public MecHostDto(@NotEmpty(message = "hostIp is empty") String hostIp,
                      @NotEmpty(message = "status is empty") String status) {
        this.hostIp = hostIp;
        this.status = status;
    }
}
