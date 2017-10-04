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

package com.vmware.xenon.distributedtracing.com.vmware.xenon.distributedtracing.zipkin;

import com.vmware.xenon.distributedtracing.DTracer;
import com.vmware.xenon.distributedtracing.Helpers;
import com.vmware.xenon.distributedtracing.IsolateConfig;
import com.vmware.xenon.distributedtracing.TestTracingHost;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import static org.junit.Assert.assertEquals;

public class ZipkinConfigTest {

    @Rule
    public ExternalResource isolateConfig = new IsolateConfig();

    // TODO: These would be better if we introspected the resulting tracers
    //       to be sure the config was set right. The API isn't convenient for that though.
    @Test
    public void testZipkinDriverAllProperties() throws Throwable {
        System.setProperty("tracer.appName", "tracerhost1");
        System.setProperty("tracer.implementation", "zipkin");
        System.setProperty("tracer.sampleRate", "1");
        System.setProperty("tracer.zipkinUrl", "http://zipkinUrl/api/v1/spans");
        Helpers.runHost();
    }

    @Test
    public void testSystemPropertiesNoSamplerRate() throws Throwable {
        System.setProperty("tracer.appName", "tracerhost2");
        System.setProperty("tracer.implementation", "zipkin");
        System.setProperty("tracer.zipkinUrl", "http://zipkinUrl/api/v1/spans");
        Helpers.runHost();
    }

    @Test(expected=DTracer.InvalidConfigException.class)
    public void testMissingURLFatal() throws Throwable {
        System.setProperty("tracer.appName", "missingurl");
        System.setProperty("tracer.implementation", "zipkin");
        DTracer.getTracer();
    }
}
