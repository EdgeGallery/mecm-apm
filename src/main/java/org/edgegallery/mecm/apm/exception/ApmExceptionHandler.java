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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ApmExceptionHandler {

    /**
     * Returns error code and message for APM exception.
     *
     * @param ex exception while processing request
     * @return response entity with error code and message
     */
    @ExceptionHandler(ApmException.class)
    public ResponseEntity<String> handleApmException(ApmException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Returns response entity with error details when input validation is failed.
     *
     * @param ex exception while validating input
     * @return response entity with error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApmExceptionResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> errorMsg = new ArrayList<>();
        if (ex.getBindingResult().hasErrors()) {
            ex.getBindingResult().getAllErrors().forEach(error -> errorMsg.add(error.getDefaultMessage()));
        }
        ApmExceptionResponse response = new ApmExceptionResponse(LocalDateTime.now(), "input validation failed",
                errorMsg);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Returns response entity with error details when input validation is failed.
     *
     * @param ex exception while validating input
     * @return response entity with error details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApmExceptionResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        ApmExceptionResponse response = new ApmExceptionResponse(LocalDateTime.now(), "input validation failed",
                Collections.singletonList("URL validation failed"));
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Returns error code and message for Inventory exception.
     *
     * @param ex exception while processing request
     * @return response entity with error code and message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgException(IllegalArgumentException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
}
