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

import com.vmware.xenon.common.Operation
import com.vmware.xenon.common.UriUtils
import com.vmware.xenon.common.Utils
import com.vmware.xenon.common.test.VerificationHost
import com.vmware.xenon.kotlin.getBody
import com.vmware.xenon.kotlin.sendAsync
import com.vmware.xenon.services.common.ExampleService
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Before
import org.junit.Test


/**
 * See [https://kotlinlang.org/docs/reference/coroutines.html]
 */
class TestAsync {
    private lateinit var host: VerificationHost

    /**
     * Uses a coroutine sendAsync to get
     */
    @Test
    fun testHostSendAsync() = runBlocking {
        val body = ExampleServiceState()
        with(body) {
            counter = 1
            name = "my name"
            keyValues = mapOf("test" to "test")
        }

        val op = Operation.createPost(UriUtils.buildUri(host, ExampleService.FACTORY_LINK))
                .setReferer(host.uri)
                .setBody(body)

        val res = host.sendAsync(op)
        println(Utils.toJsonHtml(res.getBody(ExampleServiceState::class)))
    }

    @Before
    fun setup() {
        host = VerificationHost.create(0)

        host.start();
        host.waitForServiceAvailable(ExampleService.FACTORY_LINK)
    }
}