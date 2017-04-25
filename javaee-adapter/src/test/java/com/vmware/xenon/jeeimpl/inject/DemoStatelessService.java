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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.slf4j.Logger;

import com.vmware.xenon.jee.inject.InjectLogger;
import com.vmware.xenon.jee.inject.InjectRestProxy;
import com.vmware.xenon.jee.producer.JaxRsBridgeStatelessService;

@Path("/demo")
public interface DemoStatelessService {

    String REMOTE_SVC_URI = "REMOTE_SVC_URI";

    @GET
    @Path("/sayHello")
    CompletableFuture<List<String>> sayHello();

    @GET
    @Path("/remote/sayHello")
    CompletableFuture<List<String>> remoteSayHello();

    @GET
    @Path("/nested/sayHello")
    CompletableFuture<List<String>> nestedSayHello();

    @GET
    @Path("/throwException")
    CompletableFuture<List<String>> throwException();

    class DemoStatelessServiceImpl extends JaxRsBridgeStatelessService implements DemoStatelessService {

        @InjectLogger
        Logger log;

        @InjectRestProxy(baseUri = "http://host-not-available:8000")
        DemoStatelessService unavailableSvc;

        @InjectRestProxy
        DemoStatelessService self;

        @InjectRestProxy(baseUri = REMOTE_SVC_URI)
        DemoStatelessService remote;


        public DemoStatelessServiceImpl() {
            setContractInterface(DemoStatelessService.class);
        }

        @Override
        public CompletableFuture<List<String>> sayHello() {
            this.log.info("logger successfully injected");
            return CompletableFuture.completedFuture(asList("hello", "CDI"));
        }

        @Override
        public CompletableFuture<List<String>> throwException() {
            return this.unavailableSvc.sayHello();
        }

        @Override
        public CompletableFuture<List<String>> remoteSayHello() {
            return this.remote.sayHello();
        }

        @Override
        public CompletableFuture<List<String>> nestedSayHello() {
            return this.self.sayHello();
        }
    }
}
