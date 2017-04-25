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

package com.vmware.xenon.jee.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vmware.xenon.common.Operation;

/**
 * Enables chaining interceptors.
 * Interceptors are invoked sequentially and synchronously
 */
public class InterceptorChain implements OperationInterceptor {

    public static class Builder {

        private List<OperationInterceptor> interceptorList = new ArrayList<>();

        public InterceptorChain build() {
            return new InterceptorChain(this.interceptorList.toArray(new OperationInterceptor[0]));
        }

        public Builder with(OperationInterceptor interceptor) {
            this.interceptorList.add(interceptor);
            return this;
        }

    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private OperationInterceptor[] interceptors;

    public InterceptorChain(OperationInterceptor... interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public Map.Entry<Operation, Throwable> interceptAfterComplete(Operation op,
            Map.Entry<Operation, Throwable> result) {
        if (this.interceptors != null) {
            for (OperationInterceptor interceptor : this.interceptors) {
                result = interceptor.interceptAfterComplete(op, result);
            }
        }
        return result;
    }

    @Override
    public Operation interceptBeforeComplete(Operation operation) {
        if (this.interceptors != null) {
            for (OperationInterceptor interceptor : this.interceptors) {
                operation = interceptor.interceptBeforeComplete(operation);
            }
        }
        return operation;
    }
}
