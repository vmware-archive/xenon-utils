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

import java.util.Map;

import com.vmware.xenon.common.Operation;

/**
 * Please do not maintain state (instance level variables) with in interceptor
 */
public interface OperationInterceptor {

    default Map.Entry<Operation, Throwable> interceptAfterComplete(Operation op,
            Map.Entry<Operation, Throwable> result) {
        return result;
    }

    default Operation interceptBeforeComplete(Operation op) {
        return op;
    }
}
