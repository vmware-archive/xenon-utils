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


package com.vmware.xenon.kotlin.example

import com.vmware.xenon.common.FactoryService
import com.vmware.xenon.common.ServiceHost
import com.vmware.xenon.services.common.RootNamespaceService
import com.vmware.xenon.ui.UiService
import java.util.logging.Level
import java.util.logging.Logger

class KotlinExampleServiceHost : ServiceHost() {

    override fun start(): ServiceHost {
        isAuthorizationEnabled = false
        super.start()
        setAuthorizationContext(systemAuthorizationContext)

        startDefaultCoreServicesSynchronously()
        startService(UiService())
        startService(RootNamespaceService())

        startFactory(KotlinExampleService::class.java, {
            FactoryService.create(KotlinExampleService::class.java)
        })

        startService(ExampleAggregatorService())

        requestLoggingInfo = RequestLoggingInfo()
        requestLoggingInfo.enabled = true
        return this
    }
}


fun main(args: Array<String>) {
    Logger.getGlobal().level = Level.FINE
    val host = KotlinExampleServiceHost()
    host.initialize(args)
    host.start()
}