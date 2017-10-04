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

import org.junit.rules.ExternalResource;


public class IsolateConfig extends ExternalResource {

    @Override
    protected void before() throws Throwable {
        super.before();
        System.clearProperty("tracer.appName");
        System.clearProperty("tracer.implementation");
        System.clearProperty("tracer.sampleRate");
        System.clearProperty("tracer.zipkinUrl");
        DTracer.clearTracers();
    }

    @Override
    protected void after() {
        super.after();
        System.clearProperty("tracer.appName");
        System.clearProperty("tracer.implementation");
        System.clearProperty("tracer.sampleRate");
        System.clearProperty("tracer.zipkinUrl");
        DTracer.clearTracers();
    }
}
