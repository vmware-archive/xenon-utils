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

package com.vmware.xenon.distributedtracing.jaeger;

import java.util.logging.Logger;

import com.uber.jaeger.Configuration;
import io.opentracing.Tracer;

import com.vmware.xenon.distributedtracing.DTracer;

public class JaegerConfig {
    private static final Logger LOG = Logger.getLogger(JaegerConfig.class.getName());

    /**
     * Get a Jaeger based tracer.
     *
     * @param stackName Custom sub-component of the service name for reporting with.
     * @return An OpenTracking Tracer.
     * @throws Exception
     */
    public static Tracer getOpenTracingInstance(String stackName) throws Exception {
        String tracerName = DTracer.getTracerName(stackName);
        Number rate = DTracer.getSampleRate();
        Configuration.SamplerConfiguration samplerConfig = null;
        if (rate != null) {
            samplerConfig = new Configuration.SamplerConfiguration("probabilistic", rate);
        } else {
            samplerConfig = Configuration.SamplerConfiguration.fromEnv();
        }
        Configuration.ReporterConfiguration reporterConfig = Configuration.ReporterConfiguration.fromEnv();
        Configuration config = new Configuration(tracerName, samplerConfig,reporterConfig);
        LOG.info(String.format("Initialized DTracer[Jaeger]: [%s]", tracerName));
        return config.getTracer();
    }
}
