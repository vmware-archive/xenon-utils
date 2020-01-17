/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.failsafe;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.time.temporal.ChronoUnit.MILLIS;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import net.jodah.failsafe.AsyncExecution;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.RetryPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import wiremock.org.apache.http.HttpEntity;
import wiremock.org.apache.http.HttpResponse;
import wiremock.org.apache.http.client.HttpClient;
import wiremock.org.apache.http.client.methods.HttpGet;
import wiremock.org.apache.http.impl.client.HttpClientBuilder;
import wiremock.org.apache.http.util.EntityUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestNodeGroupManager;
import com.vmware.xenon.common.test.TestRequestSender;

/**
 * Demonstrate using the failsafe library with xenon client
 */
public class FailsafeTest {
    ServiceHost clientHost;
    TestNodeGroupManager nodeGroupManager;
    TemporaryFolder folder = new TemporaryFolder();
    TestRequestSender sender;
    int nodeCount = 3; //number of xenon nodes to use

    static int mockPort = 8990;
    public String mockUri = "http://localhost:" + mockPort;
    public String xenonUri = "http://localhost:" + (mockPort + 1);

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    @Before
    public void setup() throws Throwable {
        this.folder.create();
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        ServiceHost[] hostList = new ServiceHost[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            args.id = "host" + i;   // human readable name instead of GUID
            args.sandbox = Paths.get(this.folder.getRoot().toString() + i);
            args.port = mockPort + 1 + i;
            args.isAuthorizationEnabled = true;
            hostList[i] = startHost(new String[0], args);
        }

        TestNodeGroupManager nodeGroup = new TestNodeGroupManager();
        for (ServiceHost host : hostList) {
            nodeGroup.addHost(host);
        }

        nodeGroup.waitForConvergence();
        this.clientHost = nodeGroup.getHost();  // grabs a random one of the hosts.
        this.nodeGroupManager = nodeGroup;
        sender = new TestRequestSender(this.clientHost);
    }

    public ServiceHost startHost(String[] stringArgs, ServiceHost.Arguments args) throws Throwable {
        // run tests using a FailsetTestServiceHost, note that this is _not_ the FailsafeServiceHost
        //   which is used in FailsafeServiceHostTest
        ServiceHost h = FailsafeServiceHost.startHost(stringArgs, args);
        h.startService(new FailsafeTestPingService());
        return h;
    }

    @After
    public void cleanup() {
        if  (this.nodeGroupManager != null && this.nodeGroupManager.getAllHosts() != null) {
            this.nodeGroupManager.getAllHosts().forEach((h) -> h.stop());
        }
        this.folder.delete();
    }

    /**
     * demonstrate that wire mock can work with java http client
     */
    @Test
    public void testsWireMock() throws IOException {
        String resource = "/test/wire/mock";
        String body = "test wire mock response";

        mockGet(resource, body);
        String content = getRequest(resource);

        assertTrue(content.equals(body));
    }

    /**
     * demonstrate that wire mock can work with xenon http client
     */
    @Test
    public void testXenonWireMock() throws IOException {
        String resource = "/test/xenon/wire/mock";
        String body = "test xenon wire mock response";

        mockGet(resource, body);
        Operation rop = xenonGetRequest(resource);

        assertTrue(rop.getBodyRaw().equals(body));
    }

    @Test
    public void testWireMockNeedRetry() throws IOException {
        String resource = "/test/failsafe/wire/mock/fails";
        String body = "test failsafe wire mock fail response";

        mockGetNeedRetry(resource, body);

        String content = getRequest(resource);
        assertTrue(content.isEmpty());
    }

    /**
     * demonstrate that failsafe works with java http client using wiremock
     */
    @Test
    public void testFailsafeWireMock() throws IOException {
        String resource = "/test/failsafe/wire/mock";
        String body = "test failsafe wire mock response";

        mockGetNeedRetry(resource, body);

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryIf(result -> result == null || result.toString().isEmpty())
                .withMaxRetries(1);

        String content = Failsafe.with(retryPolicy).get(() -> getRequest(resource));

        assertTrue(content.equals(body));
    }

    @Test
    public void testFailsafeWireMockFails() throws IOException {
        String resource = "/test/failsafe/wire/mock/failure";

        mockGetFail(resource);

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryIf(result -> result == null || result.toString().isEmpty())
                .withMaxRetries(5);

        AtomicInteger cnt = new AtomicInteger(0);
        String content = Failsafe.with(retryPolicy).get(ctx -> {
            cnt.incrementAndGet();
            return getRequest(resource);
        });

        assertTrue(content.isEmpty());
        assertEquals(cnt.get(), 6);
    }

    @Test
    public void testWireMockProxyToXenon() throws IOException {
        stubFor(get(urlMatching("/ping"))
                .willReturn(aResponse().proxiedFrom(xenonUri)));

        String content = getRequest("/ping");

        assertTrue(content.startsWith("PING"));
    }

    @Test
    public void testFailsafeToXenon() throws IOException {
        String resource = "/ping";

        mockProxyFail(10, resource);

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryIf(result -> result == null || result.toString().isEmpty())
                .withMaxRetries(15);

        AtomicInteger cnt = new AtomicInteger(0);
        String content = Failsafe.with(retryPolicy).get(ctx -> {
            cnt.incrementAndGet();
            return getRequest(resource);
        });

        assertEquals(cnt.get(), 11);
        assertTrue(content.startsWith("PING"));
    }

    /**
     * demonstrate that failsafe works with xenon http client using wiremock
     */
    @Test
    public void testXenonFailsafeWireMock() throws IOException {
        String resource = "/test/xenon/failsafe/wire/mock";
        String body = "test xenon failsafe wire mock response";

        mockGetNeedRetry(resource, body);

        AtomicReference<Operation> opRef = new AtomicReference<>();
        AtomicInteger cnt = new AtomicInteger();
        TestContext waitContext = new TestContext(1, Duration.of(250, MILLIS));
        RetryPolicy retryPolicy = new RetryPolicy().withMaxRetries(1);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(16);

        xenonFailsafeGetOp(
                URI.create(mockUri + resource),
                retryPolicy,
                cnt,
                executor,
                (o,e)->{
                    if (e == null) {
                        opRef.set(o);
                    }
                    waitContext.complete();
                }
        );

        waitContext.await();
        assertTrue(opRef.get().getBodyRaw().equals(body));
        assertEquals(2, cnt.get());
    }

    @Test
    public void testXenonFailsafeWireMockFails() throws IOException {
        String resource = "/test/xenon/failsafe/wire/mock/failure";

        AtomicReference<Operation> opRef = new AtomicReference<>();
        AtomicInteger cnt = new AtomicInteger(0);
        TestContext waitContext = new TestContext(1, Duration.of(250, MILLIS));
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(16);

        mockGetFail(resource);

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryIf(result -> result == null || ((Operation)(result)).getStatusCode() != 200)
                .withMaxRetries(5);

        xenonFailsafeGetOp(
                URI.create(mockUri + resource),
                retryPolicy,
                cnt,
                executor,
                (o,e)->{
                    if (e == null) {
                        opRef.set(o);
                    }
                    waitContext.complete();
                }
        );
        waitContext.await();

        assertTrue(opRef.get() == null);
        assertEquals(6, cnt.get());
    }

    /**
     * demonstrate that failsafe works with xenon http client using wiremock
     */
    @Test
    public void testXenonFailsafeCircuitBreaker() throws IOException, InterruptedException {
        int warmup = 5;
        int testCount = 25000;
        String resource = "/test/xenon/failsafe/circuit/breaker";

        CircuitBreaker breaker = new CircuitBreaker()
                .failIf((result, failure) -> failure != null || result == null || ((Operation)(result)).getStatusCode() != 200)
                .withDelay(5000, TimeUnit.MILLISECONDS)
                .withFailureThreshold(warmup);

        // make all requests fail
        mockGetFail(resource);

        AtomicReference<Operation> opRef = new AtomicReference<>();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(16);

        // open the circuit
        int preCount = xenonFailsafeGetOperations(opRef, warmup, executor, breaker, resource);

        // breaker stays open for a little while
        Thread.sleep(15);

        // expect all requests were made
        assertEquals(warmup, preCount);
        assertTrue(breaker.isOpen());

        // run with circuit open and failing requests
        int postCount = xenonFailsafeGetOperations(opRef, testCount, executor, breaker, resource);

        // expect not all of the request are made
        assertTrue(postCount < testCount);
        assertTrue(opRef.get() == null);
    }

    /**
     * demonstrate that failsafe works with xenon http client using wiremock
     */
    @Test
    public void testXenonToXenonWithCircuitBreaker() throws IOException, InterruptedException {
        int warmup = 15;
        int recoverCount = 15;
        int testCount = 100;

        String resource = "/ping";

        AtomicReference<Operation> opRef = new AtomicReference<>();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(16);

        // fail to open circuit then proxy to xenon
        mockGetFail(resource);

        CircuitBreaker breaker = new CircuitBreaker()
                .failIf((result, failure) -> failure != null || result == null || ((Operation)(result)).getStatusCode() != 200)
                .withDelay(1000, TimeUnit.MILLISECONDS)
                .withFailureThreshold(warmup);

        // make enough requests to open the circuit
        int warmupCount = xenonFailsafeGetOperations(opRef, warmup, executor, breaker, resource);

        assertEquals("warmups operations attempted", warmup, warmupCount);

        // breaker stays open for a little while
        Thread.sleep(15);

        assertTrue("breaker is open", breaker.isOpen());

        mockProxy(resource);

        Thread.sleep(1000); // wait for breaker to close

        // make some requests
        int sendCount = xenonFailsafeGetOperations(opRef, recoverCount, executor, breaker, resource);

        assertTrue("breaker is closed", breaker.isClosed());
        assertTrue("sent messages", sendCount > 0);

        // send all successful requests
        assertEquals(
                testCount,
                xenonFailsafeGetOperations(opRef, testCount, executor, breaker, resource)
        );

        assertTrue("breaker is closed", breaker.isClosed());
        assertTrue("reply is config blob", opRef.get().getBodyRaw().toString().equals("PING"));

        System.out.println(WireMock.findUnmatchedRequests().toString());
    }

    /**
     * perform a number of xenon get operations with a failsafe circuit breaker
     * @param opRef
     * @param opCount
     * @param executor
     * @param breaker
     * @param resource
     */
    private int xenonFailsafeGetOperations(AtomicReference<Operation> opRef,
                                           int opCount,
                                           ScheduledExecutorService executor,
                                           CircuitBreaker breaker,
                                           String resource) {
        AtomicInteger count = new AtomicInteger(0);

        List<FailsafeFuture<?>> futures = new ArrayList<>(opCount);
        for (int i = 0; i < opCount; i++) {
            futures.add(xenonFailsafeGetOperation(
                    URI.create(mockUri + resource),
                    breaker,
                    count,
                    executor,
                    (o, e) -> {
                        if (e == null) {
                            opRef.set(o);
                        }
                    }
            ));
        }

        for (FailsafeFuture<?> future :futures) {
            try {
                future.get(25, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                ; // Circuit Breaker Open
            }
        }

        return count.get();
    }

    private FailsafeFuture<?> xenonFailsafeGetOp(URI uri,
                                                 RetryPolicy retryPolicy,
                                                 AtomicInteger sendCount,
                                                 ScheduledExecutorService executor,
                                                 Operation.CompletionHandler onComplete) {
        return Failsafe.with(retryPolicy)
                .with(executor)
                .runAsync(
                        execution -> xenonFailsafeGetOpCallback(
                                uri,
                                onComplete,
                                sendCount,
                                execution)
                );
    }

    private FailsafeFuture<?> xenonFailsafeGetOperation(URI uri,
                                                        CircuitBreaker breaker,
                                                        AtomicInteger sendCount,
                                                        ScheduledExecutorService executor,
                                                        Operation.CompletionHandler onComplete) {
        return Failsafe.with(breaker)
                .with(executor)
                .runAsync(
                        execution -> xenonFailsafeGetOpCallback(
                                uri,
                                onComplete,
                                sendCount,
                                execution)
                );
    }

    private void xenonFailsafeGetOpCallback(URI uri,
                                            Operation.CompletionHandler onComplete,
                                            AtomicInteger sendCount,
                                            AsyncExecution execution) {
        if (!execution.isComplete()) {
            sendCount.incrementAndGet();
            sender.sendRequest(Operation
                    .createGet(uri)
                    .forceRemote()
                    .setCompletion(
                            (result, failure) -> xenonFailsafeGetOpCallbackComplete(
                                    onComplete,
                                    execution,
                                    result,
                                    failure
                            )
                    )
            );
        }
    }

    private void xenonFailsafeGetOpCallbackComplete(Operation.CompletionHandler onComplete,
                                                    AsyncExecution execution,
                                                    Operation result,
                                                    Throwable failure) {
        if (execution.complete(result, failure)) {
            result.complete();
            onComplete.handle(result, failure);
        } else if (!execution.retry()) {
            result.fail(failure);
            onComplete.handle(result, failure);
        }
    }

    protected Operation xenonGetRequest(String resource) {
        AtomicReference<Operation> opRef = new AtomicReference<>();

        Operation op = Operation.createGet(URI.create(mockUri + resource))
                .forceRemote()
                .nestCompletion((o,e) -> {
                    opRef.set(o);
                    o.complete();
                });

        sender.sendAndWait(op);
        return opRef.get();
    }

    private String getRequest(String resource) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(mockUri + resource);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        return EntityUtils.toString(entity);
    }

    protected void mockGet(String resource, String body) {
        stubFor(get(urlEqualTo(resource))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(body)));
    }

    private void mockProxy(String resource) {
        stubFor(get(urlEqualTo(resource))
                .willReturn(aResponse().proxiedFrom(xenonUri)));
    }

    protected void mockGetFail(String resource) {
        stubFor(get(urlEqualTo(resource))
                .willReturn(aResponse()
                        .withStatus(503)));
    }

    private void mockGetNeedRetry(String resource, String body) {
        stubFor(get(urlEqualTo(resource))
                .inScenario("failsafe wire mock")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503))
                .willSetStateTo("retry"));

        stubFor(get(urlEqualTo(resource))
                .inScenario("failsafe wire mock")
                .whenScenarioStateIs("retry")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(body)));
    }

    /**
     * fail a given number of times and then proxy to xenon
     * @param failures
     * @param resource
     */
    private void mockProxyFail(int failures, String resource) {
        stubFor(get(urlEqualTo(resource))
                .inScenario("xenon to xenon")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503))
                .willSetStateTo("retry0"));

        for (int i = 0; i < failures - 1; i++) {
            stubFor(get(urlEqualTo(resource))
                    .inScenario("xenon to xenon")
                    .whenScenarioStateIs("retry" + i)
                    .willReturn(aResponse()
                            .withStatus(503))
                    .willSetStateTo("retry" + ( i + 1 )));
        }

        stubFor(get(urlEqualTo(resource))
                .inScenario("xenon to xenon")
                .whenScenarioStateIs("retry" + (failures - 1))
                .willReturn(aResponse().proxiedFrom(xenonUri)));
    }
}
