/*
 * Copyright (c) 2014-2015 VMware, Inc. All Rights Reserved.
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

import java.util.HashMap;
import java.util.Map;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;

import com.vmware.xenon.distributedtracing.jaeger.JaegerConfig;
import com.vmware.xenon.distributedtracing.zipkin.ZipkinConfig;

/**
 * DTracer class which will help tracing an application or a stack (within an application).
 */
public class DTracer {

    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String msg) {
            super(msg);
        }
    }

    private static final String PARAM_TRACER_IMPLEMENTATION = "tracer.implementation";
    private static final String PARAM_TRACER_APP_NAME = "tracer.appName";
    private static final String DEFAULT_APP_NAME = "service";
    private static final String PARAM_TRACER_SAMPLE_RATE = "tracer.sampleRate";
    public static final float DEFAULT_SAMPLE_RATE = 1.0f;

    /* Allows having multiple independent applications in a single process. */
    private static Map<String, Tracer> tracers = new HashMap<>();

    public static Tracer getTracer() throws Throwable {
        return getTracer(null);
    }

    /**
     * Get a tracer for a given stack. For instance, if your service is called foo,
     * obtain a tracer by calling getTracer("foo").
     *
     * XXX: Perhaps this should be linked into Xenon's context, as getting a span builder is an operation on Tracer,
     *      and we want HTTP requests and the like to be able to create spans. OTOH perhaps we should simplify to the
     *      OpenTracing model and just have one tracer at a time, which would deliver the least friction but mean that
     *      all services in a single process would have the same opentracing service id.
     *      https://github.com/opentracing/opentracing-java#initialization
     *
     * The tracer.appName JVM property, or TRACER_APPNAME environment variable can be used to customise the service
     * name reported in spans. The default is "service".
     *
     * The tracer.implementation JVM property, or TRACER_IMPLEMENTATION environment variable can be used to select
     * implementation. Valid values are:
     * - empty or unset: No tracing. A no-op tracer will be installed so that instrumentation code does not need to be
     *   conditional.
     * - zipkin: Use the Zipkin brave libraries.
     * - jaeger: Use the Jaeger reporter library.
     *
     * The tracer.sampleRate JVM property, or TRACER_SAMPLERATE environment variable can be used to control sampling.
     * Valid values are:
     * - empty or unset: use the default for whatever reporter implementation.
     * - a floating point number: 0 <= fraction of requests to sample <= 1
     *
     * @param stackName (optional) The name of the stack to report in spans. Defaults to the service name the JVM was configured with.
     * @return A Tracer instance.
     */
    public static Tracer getTracer(String stackName) throws Throwable {
        String tracerName = getTracerName(stackName);
        if (tracers.containsKey(tracerName)) {
            return tracers.get(tracerName);
        }
        /* Could refactor into a persistent config cache or some such; but as we only look up config rarely there is little benefit today. */
        String implementation = readParameter(PARAM_TRACER_IMPLEMENTATION).toLowerCase();
        Tracer tracer = null;
        if (implementation.isEmpty()) {
            tracer = NoopTracerFactory.create();
        } else if (implementation.equals("zipkin")) {
            tracer = ZipkinConfig.getOpenTracingInstance(stackName);
        } else if (implementation.equals("jaeger")) {
            tracer = JaegerConfig.getOpenTracingInstance(stackName);
        } else {
            throw new Exception(String.format("Unknown tracer implementation [%s]", implementation));
        }
        tracers.put(tracerName, tracer);
        return tracer;
    }

    /**
     * Test support: empty all the cached tracers.
     */
    public static void clearTracers() {
        tracers.clear();
    }

    /**
     * Inject a specific tracer bypassing automatic configuration.
     *
     * This is useful for both testing and if for production use the automatic configuration mechanism is insufficient.
     *
     * @param tracer An io.opentracing.Tracer to store against stack stackName.
     * @param stackName A string describing the stack this should trace against.
     */
    public static void setTracer(Tracer tracer, String stackName) {
        String tracerName = getTracerName(stackName);
        tracers.put(tracerName, tracer);
    }

    /**
     * Inject a specific tracer bypassing automatic configuration.
     *
     * This is useful for both testing and if for production use the automatic configuration mechanism is insufficient.
     *
     * @param tracer An io.opentracing.Tracer to store.
     */
    public static void setTracer(Tracer tracer) {
        setTracer(tracer, null);
    }

    /**
     * Read a system property or an environment variable. System properties take precedence.
     * @param varName The property to read.
     * @return The resulting property. If the property is not set "" is returned.
     */
    public static String readParameter(String varName) {
        String varValue = System.getProperty(varName);
        if (varValue == null || varValue.isEmpty() || varValue
                .equalsIgnoreCase("null")) {
            String envVarName = varName.toUpperCase();
            envVarName = envVarName.replaceAll("\\.", "_");
            varValue = System.getenv(envVarName);
        }
        if (varValue == null) {
            varValue = "";
        }
        return varValue;
    }

    public static String getTracerName(String stackName) {
        String appName = readParameter(PARAM_TRACER_APP_NAME);

        if (appName == null) {
            appName = DEFAULT_APP_NAME;
        }

        if (stackName == null || stackName.isEmpty() || stackName
                .equalsIgnoreCase("null")) {
            return appName;
        }
        return appName + "-" + stackName;
    }

    public static Number getSampleRate() {
        Number sampleRate;
        String sampleRateStr = readParameter(PARAM_TRACER_SAMPLE_RATE);
        if (sampleRateStr.isEmpty()) {
            sampleRate = null;
        } else {
            try {
                sampleRate = Float.parseFloat(sampleRateStr);
            } catch (NumberFormatException nfe) {
                sampleRate = null;
            }
        }
        return sampleRate;
    }
}
