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

package com.vmware.xenon.distributedtracing.com.vmware.xenon.distributedtracing.jaeger;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

import com.uber.jaeger.Tracer;
import org.junit.Test;

import com.vmware.xenon.distributedtracing.DTracer;

public class JaegerConfigTest {

    @Test
    public void testSensibleDefaults() throws Throwable {
        System.setProperty("tracer.appName", "missingurl");
        System.setProperty("tracer.implementation", "jaeger");
        assertThat(DTracer.getTracer(), instanceOf(Tracer.class));
    }
}
