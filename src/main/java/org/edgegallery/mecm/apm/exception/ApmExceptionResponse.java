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

package org.edgegallery.mecm.apm.exception;

import java.util.Date;
import java.util.List;

/**
 * Application package management exception response.
 */
public class ApmExceptionResponse {

    private Date timestamp;
    private String message;
    private List<String> details;

    /**
     * Constructor to create exception response.
     *
     * @param timestamp timestamp
     * @param message message
     * @param details details
     */
    public ApmExceptionResponse(Date timestamp, String message, List<String> details) {
        super();
        this.timestamp = (Date) timestamp.clone();
        this.message = message;
        this.details = details;
    }

    /**
     * Returns message.
     *
     * @return message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns error details.
     *
     * @return error details
     */
    public List<String> getDetails() {
        return details;
    }

    /**
     * Returns timestamp.
     *
     * @return timestamp
     */
    public Date getTimestamp() {
        return timestamp == null ? null : (Date) this.timestamp.clone();
    }
}
