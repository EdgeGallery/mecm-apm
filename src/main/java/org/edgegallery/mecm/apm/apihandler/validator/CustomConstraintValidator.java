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

package org.edgegallery.mecm.apm.apihandler.validator;

import java.net.MalformedURLException;
import java.net.URL;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of custom constraint validator.
 */
public final class CustomConstraintValidator implements ConstraintValidator<CustomConstraint, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomConstraintValidator.class);

    private ConstraintType type;

    @Override
    public void initialize(CustomConstraint constraintAnnotation) {
        this.type = constraintAnnotation.value();
    }

    /**
     * Checks if input param is valid as per the constraint.
     *
     * @param param   input parameter
     * @param context context have constraint information
     * @return true if valid, false otherwise
     */
    public boolean isValid(String param, ConstraintValidatorContext context) {
        LOGGER.debug(context.getDefaultConstraintMessageTemplate());
        if (param == null) {
            return true;
        }
        if (type == ConstraintType.URL) {
            boolean isValid = isUrlValid(param);
            if (!isValid) {
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addConstraintViolation();
            }
            return isValid;
        }
        return true;
    }

    private boolean isUrlValid(String url) {
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }
}
