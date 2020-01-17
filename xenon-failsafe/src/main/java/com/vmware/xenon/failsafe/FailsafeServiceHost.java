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

package com.vmware.xenon.failsafe;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.jodah.failsafe.AsyncExecution;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;
import net.jodah.failsafe.function.CheckedRunnable;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.ui.UiService;

/**
 * Stand alone process entry point
 */
public class FailsafeServiceHost extends ServiceHost {
    private FailsafeService failsafeService = null;

    class RuleRetryBreaker {
        FailsafeService.Rule rule;
        AtomicReference<RetryPolicy> retry = new AtomicReference<>(null);
        AtomicReference<CircuitBreaker> breaker = new AtomicReference<>(null);
        // boolean check to avoid rebroadcasts on manual trigger of close() and open() on circuit
        AtomicBoolean autoTrigger = new AtomicBoolean(true);
    }
    // each rule shares a CircuitBreaker and RetryPolicy instances
    static ConcurrentHashMap<String, RuleRetryBreaker> rules = new ConcurrentHashMap<>();

    URI subscriptionUri = null;

    protected static void clearRules() {
        rules.clear();
    }

    protected static boolean closeCircuit(String prefix) {
        if (rules.containsKey(prefix)){
            CircuitBreaker breaker = rules.get(prefix).breaker.get();
            if (breaker != null) {
                breaker.close();
            }
            return true;
        } else
            return false;
    }

    private void incStatForRule(String stat, FailsafeService.Rule rule) {
        ServiceStats.ServiceStat s = new ServiceStats.ServiceStat();

        s.name = stat;
        s.latestValue = 1;

        try {
            sendRequest(
                    Operation.createPatch(new URI(this.getUri() + rule.documentSelfLink + "/stats"))
                            .setBody(s)
                            .setReferer(this.getUri())
            );
        } catch (URISyntaxException e) {
                ;
        }
    }

    @Override
    public void sendRequest(Operation op) {
        RuleRetryBreaker rrb = null;

        // determine which rule matches the operation
        // local requests are direct operations and match no rules
        if (op.isRemote() && failsafeService != null) {
            rrb = getRuleForOperation(op);
        }

        // if no rule matches or the matching rule retry and breaker are both disabled send direct
        // (note this means disabled rules do not get stats on requests)
        if (rrb == null || (!rrb.rule.retryEnabled && !rrb.rule.breakerEnabled)) {
            super.sendRequest(op);
        } else {
            incStatForRule(FailsafeService.STAT_REMOTE_REQUESTS, rrb.rule);

            // remote requests apply retry and circuit breaker policy
            final RetryPolicy retry = rrb.rule.retryEnabled ? getRetryPolicy(rrb) : null;
            final CircuitBreaker breaker = rrb.rule.breakerEnabled ? getCircuitBreaker(rrb) : null;

            SyncFailsafe<?> fs = null;

            if (retry != null) {
                fs = Failsafe.with(retry);
                if (breaker != null) {
                    fs = fs.with(breaker);
                }
            } else if (breaker != null){
                fs = Failsafe.with(breaker);
            }

            if (fs == null) {
                super.sendRequest(op);
            } else {
                final FailsafeService.Rule r = rrb.rule;

                FailsafeFuture<Void> f = fs.with(this.getScheduledExecutor()).runAsync(
                    execution -> failsafeOperationCallback(r, op, execution)
                );

                if (f.isDone() || f.isCancelled()) {
                    incStatForRule(FailsafeService.STAT_CALLBACK_OPEN, rrb.rule);
                    op.fail(new CircuitBreakerOpenException());
                }
            }
        }
    }

    private void failsafeOperationCallback(FailsafeService.Rule rule, Operation op, AsyncExecution execution) {
        if (!execution.isComplete()) {
            incStatForRule(FailsafeService.STAT_SENT_REQUESTS, rule);

            Operation.CompletionHandler completed = op.getCompletion();
            super.sendRequest(op.setCompletion(
                    (result, failure) -> failsafeOperationCallbackComplete(
                            completed,
                            rule,
                            execution,
                            result,
                            failure
                    )
            ));
        } else {
            incStatForRule(FailsafeService.STAT_CALLBACK_OPEN, rule);

            op.fail(new CircuitBreakerOpenException());
        }
    }

    private void failsafeOperationCallbackComplete(Operation.CompletionHandler completed, FailsafeService.Rule rule,
                                                   AsyncExecution execution,
                                                   Operation result,
                                                   Throwable failure) {
        if (execution.complete(result, failure)) {
            if (isFailedOperation(result, failure)) {
                incStatForRule(FailsafeService.STAT_CALLBACK_FAIL, rule);
                result.fail(failure);
            } else {
                incStatForRule(FailsafeService.STAT_CALLBACK_SUCCESS, rule);
                result.complete();
            }
            completed.handle(result, failure);
        } else if (!execution.retry()) {
            incStatForRule(FailsafeService.STAT_CALLBACK_FAIL, rule);
            result.fail(failure);
            completed.handle(result, failure);
        }
    }

    /**
     * Launch the FailsafeService to configure and report metrics
     */
    @Override
    public ServiceHost start() throws Throwable {
        super.start();

        this.failsafeService = new FailsafeService();

        setAuthorizationContext(this.getSystemAuthorizationContext());

        super.startFactory(this.failsafeService);
        super.startService(new FailsafeUiService());
        super.startService(new FailsafeRulesResetService());

        return this;
    }

    public static String QUERY_TASK_LINK = "failsafe";

    public QueryTask createContinuousQuery() {
        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(FailsafeService.Rule.class)
                .build();

        QueryTask queryTask = QueryTask.Builder.create()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .addOption(QueryTask.QuerySpecification.QueryOption.CONTINUOUS)
                .setQuery(query).build();

        queryTask.documentExpirationTimeMicros = Long.MAX_VALUE;
        queryTask.documentSelfLink = QUERY_TASK_LINK;

        return queryTask;
    }

    public void startDefaultCoreServicesSynchronously() throws Throwable {
        super.startDefaultCoreServicesSynchronously();
        initFailsafe();
    }

    public void initFailsafe() {
        QueryTask queryTask = createContinuousQuery();
        Operation post = Operation.createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(queryTask)
                .setReferer(this.getUri());

        sendWithDeferredResult(post)
                .thenAccept((state) -> subscribeToContinuousQuery());
    }

    public void subscribeToContinuousQuery() {
        Operation post = Operation
                .createPost(UriUtils.buildUri(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS + "/" + QUERY_TASK_LINK))
                .setReferer(getUri());

        subscriptionUri = startSubscriptionService(post, this::processResults);
    }

    public void processResults(Operation op) {
        QueryTask body = op.getBody(QueryTask.class);

        if (body.results == null || body.results.documentLinks.isEmpty()) {
            return;
        }

        for (Object doc : body.results.documents.values()) {

            FailsafeService.Rule rule = Utils.fromJson(doc, FailsafeService.Rule.class);
            FailsafeService.Rule existingRule = rules.containsKey(rule.prefix)?rules.get(rule.prefix).rule:null;
            if (rule.documentUpdateAction.equals("DELETE")) {
                rules.remove(rule.prefix);
            }
            else if (rule.documentUpdateAction.equals("PUT")
                    && existingRule != null
                    && (rule.circuitOpenTimestamp > existingRule.circuitOpenTimestamp)
                    && (rule.circuitOpenTimestamp > rule.circuitCloseTimestamp)){

                existingRule.circuitOpenTimestamp = rule.circuitOpenTimestamp;
                setBreakerState(rule.prefix, false);
            }
            else if (rule.documentUpdateAction.equals("PUT")
                    && existingRule != null
                    && (rule.circuitCloseTimestamp > existingRule.circuitCloseTimestamp)
                    && (rule.circuitCloseTimestamp > rule.circuitOpenTimestamp)){

                existingRule.circuitCloseTimestamp = rule.circuitCloseTimestamp;
                setBreakerState(rule.prefix, true);
            }
            else {
                RuleRetryBreaker rrb = new RuleRetryBreaker();
                rrb.rule = rule;
                rules.put(rule.prefix, rrb);
            }
        }
    }

    private void setBreakerState(String prefix, boolean close) {
        RuleRetryBreaker rrb = rules.get(prefix);
        CircuitBreaker cb = rrb.breaker.get();
        if (cb != null) {
            rrb.autoTrigger.set(false);
            if (close) {
                cb.close();
            }
            else {
                cb.open();
            }
            rrb.autoTrigger.set(true);
        }
    }

    static FailsafeServiceHost startHost(String[] stringArgs, Arguments args) throws Throwable {
        FailsafeServiceHost h = new FailsafeServiceHost();

        h.initialize(stringArgs, args);
        h.start();
        h.startDefaultCoreServicesSynchronously();

        Runtime.getRuntime().addShutdownHook(new Thread(h::stop));
        return h;
    }

    // determine if an operation is a failure for the circuit breaker and retry
    private boolean isFailedOperation(Object result, Throwable failure) {
        int statusCode = ((Operation) (result)).getStatusCode();

        switch (statusCode) {
            case 502: // Bad Gateway
            case 503: // Service Unavailable
            case 504: // Gateway Timeout
                return true;
        }

        return false;
    }

    public RuleRetryBreaker getRuleForOperation(Operation op) {
        String pre = null;

        // find longest matching prefix
        for (String prefix : rules.keySet()) {
            if (prefix != null && op.getUri().toString().startsWith(prefix)
                    && (pre == null || prefix.length() > pre.length())) {
                pre = prefix;
            }
        }
        if (pre == null || !rules.containsKey(pre)) {
            return null;
        }

        return rules.get(pre);
    }

    public RetryPolicy getRetryPolicy(RuleRetryBreaker rule) {
        RetryPolicy retry = rule.retry.get();

        if (retry == null) {
            if (!rule.rule.retryEnabled || rule.rule.retryMaxCount == 0) {
                return new RetryPolicy()
                        .withMaxRetries(0);
            }

            retry = new RetryPolicy()
                    .withMaxRetries(rule.rule.retryMaxCount)
                    .retryIf(this::isFailedOperation);

            if (rule.rule.retryMaxDurationMs > 0) {
                retry = retry.withMaxDuration(rule.rule.retryMaxDurationMs, TimeUnit.MILLISECONDS);
            }
            if (rule.rule.retryBackoffMs > 0 && rule.rule.retryBackoffMaxMs > 0) {
                retry = retry.withBackoff(rule.rule.retryBackoffMs, rule.rule.retryBackoffMaxMs, TimeUnit.MILLISECONDS);
            } else if (rule.rule.retryDelayMs > 0) {
                retry = retry.withDelay(rule.rule.retryDelayMs, TimeUnit.MILLISECONDS);
            }
            if (rule.rule.retryJitterMs > 0) {
                retry = retry.withJitter(rule.rule.retryJitterMs, TimeUnit.MILLISECONDS);
            }
            rule.retry.set(retry);
        }

        return rule.retry.get();
    }

    private CheckedRunnable createOnCloseCallback(final RuleRetryBreaker rrb){
       return (()->{
           if (rrb.autoTrigger.get()){
               rrb.rule.circuitCloseTimestamp = Utils.getNowMicrosUtc();

               Operation.createPut(this, rrb.rule.documentSelfLink)
                       .setBody(rrb.rule)
                       .setReferer(this.getUri())
                       .complete();
           }
        });
    }

    private CheckedRunnable createOnOpenCallback(final RuleRetryBreaker rrb){
        return (()->{
            if (rrb.autoTrigger.get()) {
                rrb.rule.circuitOpenTimestamp = Utils.getNowMicrosUtc();

                Operation.createPut(this, rrb.rule.documentSelfLink)
                        .setBody(rrb.rule)
                        .setReferer(this.getUri())
                        .complete();
            }
        });
    }

    public CircuitBreaker getCircuitBreaker(RuleRetryBreaker rrb) {
        CircuitBreaker breaker = rrb.breaker.get();
        if (breaker == null) {
            breaker = new CircuitBreaker().failIf(this::isFailedOperation)
                        .onClose(createOnCloseCallback(rrb))
                        .onOpen(createOnOpenCallback(rrb));

            if (rrb.rule.breakerFailureThreshold > 0) {
                breaker = breaker.withFailureThreshold(rrb.rule.breakerFailureThreshold);
            }
            if (rrb.rule.breakerOpenTimeMs > 0) {
                breaker = breaker.withDelay(rrb.rule.breakerOpenTimeMs, TimeUnit.MILLISECONDS);
            }
            if (rrb.rule.breakerTimeoutMs > 0) {
                breaker = breaker.withTimeout(rrb.rule.breakerTimeoutMs, TimeUnit.MILLISECONDS);
            }
            if (rrb.rule.breakerSuccessThreshold > 0) {
                breaker = breaker.withSuccessThreshold(rrb.rule.breakerSuccessThreshold);
            }
            rrb.breaker.set(breaker);
        }

        return rrb.breaker.get();
    }

    public static void main(String[] args) throws Throwable {
        startHost(args, new Arguments())
                .startService(new UiService()); // start the UI when running the jar
    }

    /*
    // enable CORS for local UI development using yarn, uncomment in rule.service.ts as well
    public static class CorsAccessControl {
        public static final String ORIGIN = "Origin";
        public static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
        public static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
        public static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
        public static final String REQUEST_METHOD = "Access-Control-Request-Method";
        public static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
        public static final String REQUEST_HEADERS = "Access-Control-Request-Headers";
        public static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    }

    @Override
    public boolean handleRequest(Service service, Operation inboundOp) {
        if (inboundOp != null && inboundOp.hasRequestHeaders()) {
            // Turn on CORS access for all services
            String origin = inboundOp.getRequestHeader(CorsAccessControl.ORIGIN);
            if (origin != null) {
                inboundOp.addResponseHeader(CorsAccessControl.ALLOW_ORIGIN, origin);
                inboundOp.addResponseHeader(CorsAccessControl.EXPOSE_HEADERS,
                        Operation.REQUEST_AUTH_TOKEN_HEADER);
                inboundOp.addResponseHeader(CorsAccessControl.ALLOW_CREDENTIALS, "true");
            }

            String methods = inboundOp.getRequestHeader(CorsAccessControl.REQUEST_METHOD);
            if (origin != null && methods != null) {
                inboundOp.addResponseHeader(CorsAccessControl.ALLOW_METHODS, methods);
            }

            String headers = inboundOp.getRequestHeader(CorsAccessControl.REQUEST_HEADERS);
            if (origin != null && headers != null) {
                inboundOp.addResponseHeader(CorsAccessControl.ALLOW_HEADERS, headers);
            }

            if (origin != null && inboundOp.getAction() == Service.Action.OPTIONS) {
                inboundOp.setBody(null);
                inboundOp.complete();
                return true;
            }

        }
        return super.handleRequest(service, inboundOp);
    }
    */
}