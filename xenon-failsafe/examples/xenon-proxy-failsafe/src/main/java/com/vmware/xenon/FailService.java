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

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

public class FailService extends StatelessService {
    public static final String SELF_LINK = "/fail";
    public static final String FACTORY_LINK = "/fail";

    public static AtomicInteger failPercent = new AtomicInteger(1);
    Random random = new Random();

    public static class Update extends ServiceDocument {
        public int failPercent;
    }

    public FailService() {
        super(FailService.Update.class);
    }

    @Override
    public void handlePost(Operation post) {
        FailService.Update update = post.getBody(FailService.Update.class);
        FailService.failPercent.set(update.failPercent);
        post.complete();
    }

    /**
     * fail with an expected exception
     * @param get
     */
    @Override
    public void handleGet(Operation get) {
        if (random.nextInt(100) < FailService.failPercent.get()) {
            get.fail(503, new Exception("Service Failure"), null);
        } else {
            get.setBody(this.getHost().getUri() + "@" + System.currentTimeMillis()).complete();
        }
    }
}
