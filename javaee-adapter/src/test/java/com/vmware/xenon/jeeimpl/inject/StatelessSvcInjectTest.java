/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.jeeimpl.inject;

import static java.util.Arrays.asList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.xenon.jee.inject.InjectUtils.extractPath;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.google.inject.ProvisionException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.exception.ServiceException;
import com.vmware.xenon.jee.host.InjectableHost;
import com.vmware.xenon.jee.inject.BeanNames;
import com.vmware.xenon.jee.inject.InjectRestProxy;
import com.vmware.xenon.jeeimpl.inject.DemoStatelessService.DemoStatelessServiceImpl;

public class StatelessSvcInjectTest extends BasicReusableHostTestCase {

    private InjectableHost injectableHost;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule();

    @BeforeClass
    public static void init() {
        // populate env variable with remote svc location
        System.setProperty(DemoStatelessService.REMOTE_SVC_URI, "http://127.0.0.1:" + wireMockRule.port());
    }

    @Before
    public void startServices() throws Throwable {
        this.injectableHost = InjectableHost.newBuilder()
                .wrapHost(this.host) // this is required only for test case
                .withStatelessService(new DemoStatelessServiceImpl()).buildAndStart();
        this.host.waitForServiceAvailable(extractPath(DemoStatelessService.class));
        WireMock.reset();
        //stub a simple get request
        stubFor(get(urlEqualTo("/demo/sayHello")).willReturn(aResponse().withBody(Utils.toJson(asList("Hello from remote", "CDI")))));
    }


    /**
     * Tests following
     * 1. Injection in a stateless service
     * 2. @InjectLogger annotation
     * 3. @InjectRestProxy annotation
     */
    @Test
    public void testRestProxyInjection() throws Throwable {
        ServiceClient instance = this.injectableHost.getInjector().getInstance(ServiceClient.class);
        assertNotNull(instance);

        assertEquals(2, instance.localHostContract.sayHello().join().size());
        assertEquals(2, instance.localHostContract.nestedSayHello().join().size());
        assertEquals(2, instance.localHostContract.remoteSayHello().join().size());

        assertEquals(2, instance.contractWithInterceptor.sayHello().join().size());
        assertEquals(2, instance.contractWithInterceptor.nestedSayHello().join().size());
        assertEquals(2, instance.contractWithInterceptor.remoteSayHello().join().size());

        assertEquals(asList("hello", "CDI"), instance.localHostContract.sayHello().join());
        assertEquals(asList("hello", "CDI"), instance.localHostContract.nestedSayHello().join());
        assertEquals(asList("Hello from remote", "CDI"), instance.localHostContract.remoteSayHello().join());

        assertEquals(asList("hello", "CDI"), instance.contractWithInterceptor.sayHello().join());

        try {
            instance.baseUriContract.sayHello().join();
            fail("Shouldn't reach here. No service running at baseUri");
        } catch (Exception e) {
            ServiceException cause = (ServiceException) e.getCause();
            Object uri = cause.getContext().get("URI");
            assertEquals("http://localhost:9999/demo/sayHello?", uri.toString());
        }
    }

    @Test(expected = ProvisionException.class)
    public void testRestProxyInjectionWithIncorrectInterceptor() {
        this.injectableHost.getInjector().getInstance(ServiceClientIncorrect.class);
    }


    /**
     * Following section captures simple classes with only injections
     */

    static class ServiceClientIncorrect {

        @InjectRestProxy(interceptorName = "NoSuchName")
        private DemoStatelessService contractWithInterceptor;
    }

    static class ServiceClient {

        @InjectRestProxy
        private DemoStatelessService localHostContract;


        @InjectRestProxy(baseUri = "http://localhost:9999")
        private DemoStatelessService baseUriContract;


        @InjectRestProxy(interceptorName = BeanNames.OPERATION_EXPIRY_IN_2_MINS)
        private DemoStatelessService contractWithInterceptor;
    }
}
