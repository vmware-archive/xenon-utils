/*
 * Copyright (c) 2014-2017 VMware, Inc. All Rights Reserved.
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

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

public class TestStatefulService extends StatefulService {
    public static final String FACTORY_LINK = "/stateful";

    public static class State extends ServiceDocument {
        public String name;
    }

    public TestStatefulService() {
        super(State.class);
    }


}
