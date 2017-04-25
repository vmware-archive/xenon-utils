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

package com.vmware.xenon.jee.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.spotify.futures.CompletableFutures;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.producer.JaxRsBridgeStatelessService;

public class ServiceExceptionTest extends BasicReusableHostTestCase {

    private ExampleStatelessService localHostContract;

    @Before
    public void init() {
        this.host.startService(new ExampleStatelessServiceImpl());
        this.host.waitForServiceAvailable(ExampleStatelessService.SELF_LINK);
        this.localHostContract = JaxRsServiceConsumer.newProxy(ExampleStatelessService.class, this.host);
    }

    /**
     * Bug Description : When server returns error, proxy handler's default error handler adds context information.
     * When context information includes Referrer's (key) value as String, the JSON deserialization fails because of forward slash. (/)
     * Fix is to change that to URI type.
     */
    @Test
    public void testExceptionSerialization() {
        try {
            this.localHostContract.throwException().join();
            fail("Shouldn't reach here");
        } catch (Exception e) {
            ServiceException error = (ServiceException) e.getCause();
            assertFalse(error.getContext().isEmpty());
            assertNotNull(error.getMessage());
            // force serialization and de-serialization
            ServiceException fromJson = Utils.fromJson(Utils.toJson(error), ServiceException.class);
            assertFalse(fromJson.getContext().isEmpty());
            assertNotNull(fromJson.getMessage());
            assertNotNull(fromJson.getContext().get(Constants.REFERER));
            assertTrue(fromJson.getStatusCode() != 0);
            assertEquals(500, fromJson.getStatusCode() );
        }
    }

    @Path("/example")
    public interface ExampleStatelessService {

        String SELF_LINK = "/example";

        @GET
        @Path("/throwException")
        CompletableFuture<List<String>> throwException();
    }

    class ExampleStatelessServiceImpl extends JaxRsBridgeStatelessService implements ExampleStatelessService {

        public ExampleStatelessServiceImpl() {
            setContractInterface(ExampleStatelessService.class);
        }

        @Override
        public CompletableFuture<List<String>> throwException() {
            return CompletableFutures.exceptionallyCompletedFuture(new ServiceException(500,"Simulated Failure"));
        }
    }
}