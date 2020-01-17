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

import java.util.EnumSet;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceStatUtils;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * API that services should implement for failsafe settings. Should support
 *   Max Retry per Host
 *   Exponential back-off max enable/disable
 *   Exponential back-off max interval
 *   Exponential back-off max rate
 *   Enable circuit-breaker per dependent service
 *   Reset the per service circuit-breaker state
 *   Globally reset all circuit breakers
 *   Metrics for each service, rate, retry, circuit breaker status
 */
public class FailsafeService extends StatefulService {
    public static final String FACTORY_LINK = ServiceUriPaths.CORE + "/failsafe";
    public static final String SELF_LINK = ServiceUriPaths.CORE + "/failsafe";

    public static String STAT_REMOTE_REQUESTS = "remoteRequestCount";
    public static String STAT_SENT_REQUESTS = "sentRequestCount";
    public static String STAT_CALLBACK_SUCCESS = "successCount";
    public static String STAT_CALLBACK_FAIL = "failCount";
    public static String STAT_CALLBACK_OPEN = "circuitOpenCount";

    public static class Rule extends ServiceDocument {
        // must call Utils.mergeWithState to leverage this
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public String prefix;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public boolean retryEnabled = true;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int retryMaxCount = 3;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int retryMaxDurationMs = 30000;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int retryDelayMs = 250;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int retryBackoffMs = 250;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int retryBackoffMaxMs = 30000;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int retryJitterMs = 100;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public boolean breakerEnabled = true;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int breakerFailureThreshold = 5;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int breakerSuccessThreshold = 5;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int breakerOpenTimeMs = 250;

        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public int breakerTimeoutMs = 5000;

        public long circuitOpenTimestamp;
        public long circuitCloseTimestamp;

    }

    @Override
    public void handleStart(Operation post) {
        initializeStats();
        post.complete();
    }

    private void initializeStats() {
        if (!this.hasOption(ServiceOption.INSTRUMENTATION)) {
            return;
        }

        createStat(STAT_REMOTE_REQUESTS);
        createStat(STAT_SENT_REQUESTS);
        createStat(STAT_CALLBACK_SUCCESS);
        createStat(STAT_CALLBACK_FAIL);
        createStat(STAT_CALLBACK_OPEN);
    }

    private void createStat(String name) {
        ServiceStatUtils.getOrCreateTimeSeriesStat(this, name,
            () -> new ServiceStats.TimeSeriesStats(
                60, 60000, EnumSet.of(ServiceStats.TimeSeriesStats.AggregationType.SUM)
            )
        );
    }

    public FailsafeService() {
        super(FailsafeService.Rule.class);

        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
        toggleOption(ServiceOption.INSTRUMENTATION, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
    }

    @Override
    public void handleCreate(Operation op) {

        if (!op.hasBody()){
            op.fail(new Exception("Empty Body"));
        }
        Rule rule = op.getBody(Rule.class);
        Utils.validateState(getStateDescription(), rule);
        if (FailsafeServiceHost.rules.containsKey(rule.prefix))
            op.fail(new Exception("Rule with prefix exists"));
        op.complete();
    }

    @Override
    public void handlePut(Operation op) {

        if (!op.hasBody()){
            op.fail(new Exception("Empty Body"));
        }
        Rule rule = op.getBody(Rule.class);
        Utils.validateState(getStateDescription(), rule);
        Rule existingRule = getState(op);
        if (!rule.prefix.equals(existingRule.prefix) ) {
            if (FailsafeServiceHost.rules.containsKey(rule.prefix))
                op.fail(new Exception("Rule with prefix exists"));
        }
        super.handlePut(op);
    }

    @Override
    public void handleDelete(Operation op) {
        if (op.getUri().getPath().contains("/default-"))
            op.fail(new Exception("Cannot delete default rules"));
        op.complete();
    }
}
