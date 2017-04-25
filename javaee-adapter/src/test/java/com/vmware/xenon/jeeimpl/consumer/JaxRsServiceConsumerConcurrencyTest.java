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

package com.vmware.xenon.jeeimpl.consumer;

import static org.junit.Assert.assertEquals;

import static com.vmware.xenon.jee.inject.InjectUtils.extractPath;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.spotify.futures.CompletableFutures;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer.ClientBuilder;
import com.vmware.xenon.jee.host.InjectableHost;
import com.vmware.xenon.jee.producer.JaxRsBridgeStatelessService;
import com.vmware.xenon.jeeimpl.consumer.JaxRsServiceConsumerConcurrencyTest.EchoService.EchoServiceImpl;

public class JaxRsServiceConsumerConcurrencyTest extends BasicReusableHostTestCase {

    private EchoService echoService;

    private ConcurrentHashMap<String, AtomicInteger> tracker = new ConcurrentHashMap<>();
    private ConcurrentSkipListSet<String> outputs = new ConcurrentSkipListSet<>();

    @Before
    public void startServices() throws Throwable {
        this.host.setStressTest(true);
        InjectableHost.newBuilder().wrapHost(this.host).withStatelessService(new EchoServiceImpl()).buildAndStart();
        this.host.waitForServiceAvailable(extractPath(EchoService.class));
        ClientBuilder<EchoService> newBuilder = JaxRsServiceConsumer.newBuilder();
        this.echoService = newBuilder.withResourceInterface(EchoService.class).withBaseUri(this.host.getUri()).build();
        this.tracker.put("1", new AtomicInteger(0));
        this.tracker.put("2", new AtomicInteger(0));
        this.tracker.put("3", new AtomicInteger(0));
        this.tracker.put("4", new AtomicInteger(0));
    }

    @Test
    public void testConcurrentAccess() throws Throwable {
        int count = 10000;
        Instant start = Instant.now();
        Thread first = new Thread(newRunnable("1", count));
        Thread second = new Thread(newRunnable("2", count));
        Thread third = new Thread(newRunnable("3", count));
        Thread fourth = new Thread(newRunnable("4", count));
        startAndJoin(first, second, third, fourth);
        Instant end = Instant.now();
        System.out.println("Time taken in millis is " + (end.toEpochMilli() - start.toEpochMilli()));
        System.out.println(this.tracker);
        assertEquals(count, this.tracker.get("1").intValue());
        assertEquals(count, this.tracker.get("2").intValue());
        assertEquals(count, this.tracker.get("3").intValue());
        assertEquals(count, this.tracker.get("4").intValue());
        assertEquals(4 * count, this.outputs.size());
        this.outputs.forEach(s -> {
            String[] tokens = s.split(":");
            assertEquals(tokens[0], tokens[1]);
        });
    }

    private void startAndJoin(Thread... threads) throws Exception {
        Stream.of(threads).forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }
    }

    Runnable newRunnable(String id, int count) {
        return () -> {
            List<CompletableFuture<Void>> collect = IntStream.range(0, count).mapToObj(value -> {
                return this.echoService.echo("" + id + ":" + this.tracker.get(id).incrementAndGet()).thenAccept(output -> this.outputs.add(id + ":" + output));
            })
                    .collect(Collectors.toList());

            CompletableFutures.allAsList(collect)
                    .join();
        };
    }


    @Path("/echo") public interface EchoService {

        @GET
        @Path("/{id}")
        CompletableFuture<String> echo(@PathParam("id") String id);

        class EchoServiceImpl extends JaxRsBridgeStatelessService implements EchoService {

            public EchoServiceImpl() {
                setContractInterface(EchoService.class);
            }

            @Override
            public CompletableFuture<String> echo(String id) {
                return CompletableFuture.completedFuture(id);
            }
        }
    }
}
