/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.xenon.jee.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception class to wrap any HTTP based errors
 */
public class ServiceException extends RuntimeException {

    static final long serialVersionUID = 1;

    public static final int DEFAULT_ERROR_CODE = 55056;

    public static final int DEFAULT_INTERNAL_ERROR_CODE = 55055;

    // external facing error code
    // ui should be able to fetch a user friend message using this error-code
    // error code should follow pattern [M][C][E]
    // where M-> Module Code. Should be 2 digit and between 10 > M < 100
    //       C -> Client Response hint code,  1 for retry
    //       E -> Server side error code. Should be 2 digit and between 10 > M < 100
    //Reserved error code[55055] for internal errors
    private int errorCode;

    private int statusCode;

    //Message useful for a developer to debug further. This is not for end-user consumption
    private String developerMessage;

    //Context information on when this error occurred. Can include input arguments, document self link etc.,
    private Map<String, Object> context = new HashMap<>();

    private String originalType;

    public ServiceException(int errorCode) {
        this.errorCode = errorCode;
        this.statusCode = 500;
    }

    public ServiceException(int statusCode, int errorCode, String developerMessage) {
        super(developerMessage);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.developerMessage = developerMessage;
    }

    public ServiceException(int errorCode, String developerMessage) {
        super(developerMessage);
        this.errorCode = errorCode;
        this.developerMessage = developerMessage;
        this.statusCode = 500;
    }

    public ServiceException(int errorCode, String developerMessage, Map<String, Object> context) {
        this.errorCode = errorCode;
        this.developerMessage = developerMessage;
        this.context = context;
    }

    public ServiceException(String developerMessage, Throwable e) {
        super(developerMessage, e);
        this.errorCode = DEFAULT_INTERNAL_ERROR_CODE; // internal error
        this.developerMessage = developerMessage;
        this.statusCode = 500;
    }

    public ServiceException(Throwable e, int errorCode) {
        super(e);
        this.errorCode = errorCode;
        this.statusCode = 500;
    }

    public ServiceException(Throwable e, int statusCode, int errorCode, String developerMessage) {
        super(developerMessage, e);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.developerMessage = developerMessage;
    }

    public ServiceException(Throwable e, int errorCode, String developerMessage) {
        super(developerMessage, e);
        this.errorCode = errorCode;
        this.developerMessage = developerMessage;
    }

    public ServiceException(Throwable e, int errorCode, String developerMessage,
            Map<String, Object> context) {
        super(e);
        this.errorCode = errorCode;
        this.developerMessage = developerMessage;
        this.context = context;
    }

    /**
     * Builder style construct to add context information on when this error occured
     */
    public ServiceException addToContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    public Map<String, Object> getContext() {
        return this.context;
    }

    public String getDeveloperMessage() {
        return this.developerMessage;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public String getOriginalType() {
        return this.originalType;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public void setDeveloperMessage(String developerMessage) {
        this.developerMessage = developerMessage;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        return "ServiceException{" +
                "errorCode=" + this.errorCode +
                ", developerMessage='" + this.developerMessage + '\'' +
                ", context=" + this.context +
                ", originalType='" + this.originalType + '\'' +
                '}';
    }
}
