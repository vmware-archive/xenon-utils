/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.jee.consumer;

import com.vmware.xenon.common.Operation;

/**
 * A POJO class providing context when an exception is thrown.
 * Useful while writing custom exception handlers
 */
public class ErrorContext {

    public static ErrorContext of(Operation orig, Operation completed, Throwable error) {
        ErrorContext ctxt = new ErrorContext();
        ctxt.srcOperation = orig;
        ctxt.completedOperation = completed;
        ctxt.error = error;
        return ctxt;
    }

    private Operation srcOperation;
    private Operation completedOperation;

    private Throwable error;

    public Operation getCompletedOperation() {
        return this.completedOperation;
    }

    public Throwable getError() {
        return this.error;
    }

    public Operation getSrcOperation() {
        return this.srcOperation;
    }

    public void setCompletedOperation(Operation completedOperation) {
        this.completedOperation = completedOperation;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public void setSrcOperation(Operation srcOperation) {
        this.srcOperation = srcOperation;
    }
}
