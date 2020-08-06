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

package org.edgegallery.mecm.apm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Application package management application.
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
public class ApmApplication {
    private static final Logger logger = LoggerFactory.getLogger(ApmApplication.class);

    /**
     * Application package management entry function.
     *
     * @param args arguments
     */
    public static void main(String[] args) {
        // TODO: Token & https based support.
        logger.info("APM application starting----");
        SpringApplication.run(ApmApplication.class, args);
    }

}
