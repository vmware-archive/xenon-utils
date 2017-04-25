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

package com.vmware.xenon.jee.consumer;

import static com.vmware.xenon.services.common.authn.AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

/**
 * Utility class providing a set of interceptors
 */
public class Interceptors {

    static class AuthContextPopulator implements OperationInterceptor {

        private final Service svc;

        public AuthContextPopulator(Service svc) {
            this.svc = svc;
        }

        @Override
        public Operation interceptBeforeComplete(Operation op) {
            this.svc.setAuthorizationContext(op, this.svc.getSystemAuthorizationContext());
            return op;
        }
    }

    static class AuthTokenPopulator implements OperationInterceptor {

        private final String authToken;

        public AuthTokenPopulator(String authToken) {
            this.authToken = authToken;
        }

        @Override
        public Operation interceptBeforeComplete(Operation op) {
            if (this.authToken == null || this.authToken.isEmpty()) {
                return op;
            }
            Map<String, String> cookies = op.getCookies();
            if (cookies == null) {
                cookies = new HashMap<>();
            }
            cookies.put(REQUEST_AUTH_TOKEN_COOKIE, this.authToken);
            op.setCookies(cookies);
            return op;
        }
    }

    static class LogAfterCompleteOperation implements OperationInterceptor {

        private final Logger log;
        private final boolean logPayload;

        public LogAfterCompleteOperation(Logger log) {
            this(log, false);
        }

        public LogAfterCompleteOperation(Logger log, boolean logPayload) {
            this.log = log;
            this.logPayload = logPayload;
        }

        @Override
        public Map.Entry<Operation, Throwable> interceptAfterComplete(Operation op,
                Map.Entry<Operation, Throwable> result) {
            if (result.getValue() == null) {
                if (this.logPayload) {
                    this.log.info(
                            "Operation {} on {} completed successfully with response body {} ",
                            new Object[] { result.getKey().getAction(), result.getKey().getUri(),
                                    String.valueOf(result.getKey().getBodyRaw()) });
                } else {
                    this.log.info("Operation {} on {} completed successfully ",
                            result.getKey().getAction(), result.getKey().getUri());
                }
            } else {
                this.log.error("Operation {} on {} failed due to {} ", new Object[] {
                        result.getKey().getAction(), result.getKey().getUri(), result.getValue() });
            }

            return result;
        }
    }

    static class OperationExpiryConfigurer implements OperationInterceptor {
        private long timeInMicros;

        public OperationExpiryConfigurer(long timeInMicros) {
            this.timeInMicros = timeInMicros;
        }

        @Override
        public Operation interceptBeforeComplete(Operation op) {
            op.setExpiration(Utils.fromNowMicrosUtc(this.timeInMicros));
            return op;
        }
    }

    /**
     * Interceptor to add  REQUEST_AUTH_TOKEN_COOKIE to the operation if not present
     */
    public static OperationInterceptor withAuthToken(String authToken) {
        return new AuthTokenPopulator(authToken);
    }

    /**
     * Interceptor to inherit the auth context from given svc.
     * If the passed svc instance is a privileged service, then all operation using this interceptor will be elevated to system context
     */
    public static OperationInterceptor withAuthContextFrom(Service svc) {
        return new AuthContextPopulator(svc);
    }


    public static OperationInterceptor expireOperationIn(TimeUnit unit, int duration) {
        return new OperationExpiryConfigurer(unit.toMicros(duration));
    }

    public static OperationInterceptor logAfterCompleteOperationInterceptor(Logger log) {
        return new LogAfterCompleteOperation(log);
    }

    public static OperationInterceptor logAfterCompleteOperationInterceptor(Logger log,
            boolean logPayload) {
        return new LogAfterCompleteOperation(log, logPayload);
    }

}
