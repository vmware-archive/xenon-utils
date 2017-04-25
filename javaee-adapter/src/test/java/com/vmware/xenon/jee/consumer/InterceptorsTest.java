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

package com.vmware.xenon.jee.consumer;

import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer.ClientBuilder;
import com.vmware.xenon.jee.producer.JaxRsBridgeStatelessService;

public class InterceptorsTest extends BasicReusableHostTestCase {

    @Path("/test")
    public interface MockService {

        String SELF_LINK = "/test";

        @Path("/delay")
        @GET
        CompletableFuture<List<String>> delay();

        @Path("/expire")
        @GET
        CompletableFuture<List<String>> expire();

    }

    public static class MockServiceImpl extends JaxRsBridgeStatelessService implements MockService {

        public MockServiceImpl() {
            setContractInterface(MockService.class);
        }

        @Override
        public CompletableFuture<List<String>> delay() {
            return getDelayFuture(TimeUnit.SECONDS.toMillis(65), () -> asList("Success"));
        }

        @Override
        public CompletableFuture<List<String>> expire() {
            return getDelayFuture(TimeUnit.SECONDS.toMillis(150), () -> asList("Success"));
        }
    }

    static <T> CompletableFuture<T> getDelayFuture(long delayTime, Supplier<T> resultSupplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                sleep(delayTime);
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
            }
            future.complete(resultSupplier.get());
        }).start();
        return future;
    }

    private MockService mockService;

    private Logger log = LoggerFactory.getLogger(getClass());

    @Test
    //    @Ignore("Runs for a while. Dont slow down the whole build")
    public void delay() throws Exception {
        InterceptorChain interceptorChain = InterceptorChain.newBuilder()
                .with(Interceptors.expireOperationIn(TimeUnit.SECONDS, 70)) // default 60
                .with(Interceptors.logAfterCompleteOperationInterceptor(this.log))
                .build();
        ClientBuilder<MockService> newBuilder = JaxRsServiceConsumer.newBuilder();
        this.mockService = newBuilder
                .withHost(this.host)
                .withBaseUri("http://localhost:" + this.host.getPort())
                .withResourceInterface(MockService.class)
                .withInterceptor(interceptorChain)
                .build();
        List<String> join = this.mockService.delay().join();
        assertEquals(1, join.size());
    }

    @Test(expected = Exception.class)
    //    @Ignore("Runs for a while. Dont slow down the whole build")
    public void expiry() throws Exception {
        ClientBuilder<MockService> newBuilder = JaxRsServiceConsumer.newBuilder();
        this.mockService = newBuilder
                .withHost(this.host)
                .withBaseUri("http://localhost:" + this.host.getPort())
                .withResourceInterface(MockService.class)
                .withInterceptor(Interceptors.logAfterCompleteOperationInterceptor(this.log))
                .build();
        List<String> join = this.mockService.delay().join();
        assertEquals(1, join.size());
    }

    @Before
    public void init() throws Exception {
        this.host.startService(new MockServiceImpl());
        this.host.waitForServiceAvailable(MockService.SELF_LINK);
    }

}
