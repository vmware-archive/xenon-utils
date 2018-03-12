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

import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

public class SlowService extends StatelessService {
    public static final String SELF_LINK = "/slow";
    public static final String FACTORY_LINK = "/slow";

    public static class Update extends ServiceDocument {
        public Integer delay; // seconds to wait until completing
    }
    static AtomicInteger delay = new AtomicInteger(500);

    @Override
    public void handlePost(Operation post) {
        SlowService.Update update = post.getBody(SlowService.Update.class);
        SlowService.delay.set(update.delay);
        post.complete();
    }

    /**
     * delay for an amount of time then return how long of delay
     * @param get
     */
    @Override
    public void handleGet(Operation get) {
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        get.setBody(new String("{ \"delay\":" + delay.get() + " }"));
                        get.complete();
                    }
                },
                delay.get()
        );
    }
}
