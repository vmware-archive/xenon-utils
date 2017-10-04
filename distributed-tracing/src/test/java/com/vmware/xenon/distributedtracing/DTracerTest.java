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

import static org.junit.Assert.assertEquals;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.opentracing.TracerFactory;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class DTracerTest {

    @Test
    public void testTracingHostStartupWorks() throws Throwable {
        System.setProperty("tracer.appName", "testservice");
        MockTracer tracer = Helpers.injectTracer();
        Helpers.runHost();
        assertEquals(20, tracer.finishedSpans().size());
    }

    @Rule
    public ExternalResource isolateConfig = new IsolateConfig();

}
