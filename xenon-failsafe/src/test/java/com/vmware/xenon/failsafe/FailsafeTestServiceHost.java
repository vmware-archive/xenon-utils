/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.failsafe;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.RootNamespaceService;

public class FailsafeTestServiceHost extends ServiceHost {

    static FailsafeTestServiceHost startHost(String[] stringArgs, Arguments args) throws Throwable {
        FailsafeTestServiceHost h = new FailsafeTestServiceHost();
        h.initialize(stringArgs, args);
        h.start();
        Runtime.getRuntime().addShutdownHook(new Thread(h::stop));
        return h;
    }

    @Override
    public ServiceHost start() throws Throwable {
        super.start();

        startDefaultCoreServicesSynchronously();
        super.startFactory(new FailsafeTestPingService());
        super.startService(new RootNamespaceService());

        return this;
    }
}
