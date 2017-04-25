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

package com.vmware.xenon.jee.producer;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;

public class RequestRouterIntegrationTest extends BasicReusableHostTestCase {

    private ExampleContract serviceProxy;

    @Before
    public void startHost() throws Throwable {
        this.host.startService(new MockedStatelessService());
        this.host.waitForServiceAvailable(MockedStatelessService.SELF_LINK);
        this.serviceProxy = JaxRsServiceConsumer.newProxy(ExampleContract.class, this.host);
    }

    @Test
    public void testGetWithDefaults() throws Throwable {
        List<String> resp = this.serviceProxy.getWithDefaults("actual", 1, 1)
                .join();
        assertEquals(asList("actual", "1", "1"), resp);

        resp = this.serviceProxy.getWithDefaults(null, 1, 1)
                .join();
        assertEquals(asList("default", "1", "1"), resp);

        resp = this.serviceProxy.getWithDefaults("actual", 1, null)
                .join();
        assertEquals(asList("actual", "1", "2"), resp);
    }

    @Test
    public void testGetWithQueryAndPathAndReturn() throws Throwable {
        Map<String, String> body = this.serviceProxy.getWithQueryAndPathAndReturn(
                "value_of_path_param",
                "value_of_query_param");
        assertEquals("success", body.get("result"));
        assertEquals("value_of_path_param", body.get("pathParam"));
        assertEquals("value_of_query_param", body.get("queryParam"));
    }

    @Test
    public void testGetWithQueryAndPathAndReturnAsync() throws Throwable {
        Map<String, String> body = this.serviceProxy
                .getWithQueryAndPathAndReturnAsync("value_of_path_param", "value_of_query_param")
                .join();
        assertEquals("success", body.get("result"));
        assertEquals("value_of_path_param", body.get("pathParam"));
        assertEquals("value_of_query_param", body.get("queryParam"));
    }

    @Test
    public void testPostWithQueryAndPathAndReturnAsync() throws Throwable {
        Map<String, String> body = this.serviceProxy
                .postWithQueryAndPathAndReturnAsync(null, asList("payload"))
                .join();
        assertEquals("[\"payload\"]", body.get("body"));
        assertEquals("defaultQueryParamVal", body.get("queryParam"));
    }

}
