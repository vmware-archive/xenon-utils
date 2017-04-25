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

import static com.vmware.xenon.common.Utils.buildKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Xenon equivalent Response body class for ServiceException
 */
public class WebErrorResponse extends com.vmware.xenon.common.ServiceErrorResponse {

    public static void fillStacktrace(com.vmware.xenon.common.ServiceErrorResponse rsp,
            Throwable e) {
        rsp.stackTrace = new ArrayList<>();
        for (StackTraceElement se : e.getStackTrace()) {
            rsp.stackTrace.add(se.toString());
        }
    }

    public static WebErrorResponse from(Throwable e) {
        WebErrorResponse rsp = new WebErrorResponse();
        if (e instanceof ServiceException) {
            ServiceException ServiceException = (ServiceException) e;
            rsp.errorCode = ServiceException.getErrorCode();
            rsp.statusCode = ServiceException.getStatusCode();
            rsp.context = ServiceException.getContext();
            rsp.message = ServiceException.getDeveloperMessage();
            rsp.stackTraceElements = ServiceException.getStackTrace();
        } else {
            rsp.errorCode = 55055; //Reserved error code
            rsp.statusCode = 500;
            rsp.message = e.getMessage();
            rsp.stackTraceElements = e.getStackTrace();
        }
        if (Objects.nonNull(e.getCause()) && e != e.getCause()) {
            rsp.cause = from(e.getCause());
        }
        rsp.type = e.getClass().getTypeName();
        rsp.documentKind = buildKind(WebErrorResponse.class);
        fillStacktrace(rsp, e);
        return rsp;
    }

    public Map<String, Object> context;
    public StackTraceElement[] stackTraceElements;

    public WebErrorResponse cause;

    public String type;

    @Deprecated
    public ServiceException toError() {
        return toError(null);
    }

    public ServiceException toError(Throwable defaultErrorCause) {
        ServiceException error;
        if (this.cause == null) {
            if (defaultErrorCause != null) {
                error = new ServiceException(defaultErrorCause, this.statusCode, this.errorCode,
                        this.message);
            } else {
                error = new ServiceException(this.errorCode, this.message);
            }
        } else {
            error = new ServiceException(this.cause.toError(defaultErrorCause), this.statusCode,
                    this.errorCode, this.message);
        }
        if (this.context != null) {
            error.setContext(this.context);
        }
        error.setDeveloperMessage(this.message);
        if (this.stackTraceElements != null) {
            error.setStackTrace(this.stackTraceElements);
        }
        error.setOriginalType(this.type);
        return error;
    }

    @Override
    public String toString() {
        return "WebErrorResponse{" +
                "context=" + this.context +
                ", stackTraceElements=" + Arrays.toString(this.stackTraceElements) +
                ", message=" + super.message +
                ", messageId=" + super.messageId +
                ", errorCode=" + super.errorCode +
                ", statusCode=" + super.statusCode +
                ", cause=" + this.cause +
                ", type='" + this.type + '\'' +
                '}';
    }

}
