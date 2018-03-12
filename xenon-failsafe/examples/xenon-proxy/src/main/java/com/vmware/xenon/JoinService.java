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

package com.vmware.xenon;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

public class JoinService extends StatelessService {
    public static final String SELF_LINK = "/join";
    public static final String FACTORY_LINK = "/join";

    public static AtomicReference<String> url1 = new AtomicReference<>("http://localhost:8000/forward");
    public static AtomicReference<String> url2 = new AtomicReference<>("http://localhost:8000/forward");
    public static AtomicInteger failPercent = new AtomicInteger(1);
    Random random = new Random();

    public static class Update extends ServiceDocument {
        public String url1;
        public String url2;
        public int failPercent;
    }

    public JoinService() {
        super(JoinService.Update.class);
    }

    @Override
    public void handlePost(Operation post) {
        JoinService.Update update = post.getBody(JoinService.Update.class);
        JoinService.url1.set(update.url1);
        JoinService.url2.set(update.url2);
        JoinService.failPercent.set(update.failPercent);
        post.complete();
    }

    /**
     * fail with an expected exception
     * @param op
     */
    @Override
    public void handleGet(Operation op) {
        if (random.nextInt(100) < ForwardService.failPercent.get()) {
            op.fail(503, new Exception("Join Failure"), null);
        } else {
            Operation op1 = Operation
                    .createGet(URI.create(url1.get()))
                    .forceRemote();
            Operation op2 = Operation
                    .createGet(URI.create(url2.get()))
                    .forceRemote();
            OperationJoin.create(op1, op2).setCompletion(
                    (ops, errs) -> {
                        if (errs != null) {
                            errs.values().forEach((e) -> {
                                if (e != null) {
                                    op.fail(e);
                                }
                            });
                        } else {
                            StringBuilder result = new StringBuilder();
                            result.append(this.getHost().getUri() + "[");
                            ops.values().forEach((o) -> result.append(o.getBodyRaw()).append(','));
                            result.append(']');
                            op.setBodyNoCloning(result.toString());
                            op.complete();
                        }
                    }
            ).sendWith(this);
        }
    }
}
