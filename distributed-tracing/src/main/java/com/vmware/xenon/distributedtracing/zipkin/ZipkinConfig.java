/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.distributedtracing.zipkin;

import java.util.logging.Logger;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import brave.sampler.BoundarySampler;
import brave.sampler.Sampler;
import com.vmware.xenon.distributedtracing.DTracer;
import io.opentracing.Tracer;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.okhttp3.OkHttpSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * This will read and configure the Zipkin specific settings.
 * - zipkin url (e.g. http://HOST/api/v1/spans).
 * There is no default, and if zipkin has been chosen it must be supplied either in the tracer.zipkinUrl JVM property,
 * or the TRACER_ZIPKINURL environment variable.
 */
public class ZipkinConfig {

    private static final Logger LOG = Logger.getLogger(ZipkinConfig.class.getName());
    private static final String PARAM_TRACER_ZIPKIN_URL = "tracer.zipkinUrl";

    private static String getZipkinUrl() {
        return DTracer.readParameter(PARAM_TRACER_ZIPKIN_URL);
    }

    public static Tracer getOpenTracingInstance(String stackName) throws Exception {
        String zipkinUrl = getZipkinUrl();
        if (zipkinUrl == null || zipkinUrl.isEmpty()) {
            throw new DTracer.InvalidConfigException("Zipkin tracing requires a Zipkin URL.");
        }
        String tracerName = DTracer.getTracerName(stackName);
        Number rate = DTracer.getSampleRate();
        if (rate == null) {
            rate = DTracer.DEFAULT_SAMPLE_RATE;
        }
        Sender sender = null;
        Reporter spanReporter = null;
        if (zipkinUrl.contains("/v1/")) {
            sender = URLConnectionSender.create(zipkinUrl);
            spanReporter = AsyncReporter.builder(sender)
                .build(SpanBytesEncoder.JSON_V1);
        } else {
            sender = OkHttpSender.create(zipkinUrl);
            spanReporter = AsyncReporter.create(sender);
        }
        Tracing braveTracing = Tracing.newBuilder()
            .localServiceName(tracerName)
            .spanReporter(spanReporter)
            .sampler(BoundarySampler.create((float)rate))
            .build();
        LOG.info(String.format(
            "Initialized DTracer: [%s] which will submit traces to [%s] and will sample at rate: [%s]",
            tracerName, zipkinUrl, rate));
        return BraveTracer.create(braveTracing);
    }

}
