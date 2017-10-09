/*
 * Copyright (c) 2017-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.distributedtracing;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class TestStatelessService extends StatelessService {

    public static final String SELF_LINK = "/stateless";

    @Override
    public void handleGet(Operation get) {
        get.addRequestHeader(
                Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
        Operation getState = Operation.createGet(getHost(), "/stateful/foo")
                .setCompletion((operation, error) -> {
                    get.setBody(operation.getBody(TestStatefulService.State.class));
                    get.complete();
                });
        this.sendRequest(getState);
    }

}