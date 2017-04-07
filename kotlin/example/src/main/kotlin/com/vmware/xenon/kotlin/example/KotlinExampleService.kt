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

import com.vmware.xenon.common.Operation
import com.vmware.xenon.common.Service.ServiceOption
import com.vmware.xenon.common.StatefulService
import com.vmware.xenon.kotlin.getBody
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState

/**
 * This is a copy of ExampleService from xenon-common project showing off Kotlin
 * capabilities.
 */
class KotlinExampleService : StatefulService(ExampleServiceState::class.java) {

    companion object {
        // must annotate so that kotlinc generates a field
        // as expected by Xenon internals
        @JvmField
        val FACTORY_LINK = "/core/kotlin/examples"
    }

    init {
        toggleOption(ServiceOption.PERSISTENCE, true)
        toggleOption(ServiceOption.OWNER_SELECTION, true)
        toggleOption(ServiceOption.REPLICATION, true)
    }

    override fun handleStart(startPost: Operation) {
        if (!startPost.hasBody()) {
            startPost.fail(IllegalArgumentException("initial state is required"))
            return
        }

        val s = startPost.getBody(ExampleServiceState::class)
        if (s.name == null) {
            startPost.fail(IllegalArgumentException("name is required"))
            return
        }

        startPost.complete()
    }

    private fun updateCounter(body: ExampleServiceState,
                              currentState: ExampleServiceState,
                              hasStateChanged: Boolean): Boolean {
        var changed = hasStateChanged
        if (body.counter != null) {
            if (currentState.counter == null) {
                currentState.counter = body.counter
            }
            // deal with possible operation re-ordering by simply always
            // moving the counter up
            currentState.counter = Math.max(body.counter, currentState.counter)
            body.counter = currentState.counter
            changed = true
        }
        return changed
    }

    override fun handleDelete(delete: Operation) {
        if (!delete.hasBody()) {
            delete.complete()
            return
        }

        // A DELETE can be used to both stop the service, mark it deleted in the index
        // so its excluded from queries, but it can also set its expiration so its state
        // history is permanently removed
        val currentState = getState<ExampleServiceState>(delete)
        val st = delete.getBody(ExampleServiceState::class)
        if (st.documentExpirationTimeMicros > 0) {
            currentState.documentExpirationTimeMicros = st.documentExpirationTimeMicros
        }
        delete.complete()
    }

    override fun handlePut(put: Operation) {
        val newState = getBody<ExampleServiceState>(put)
        val currentState = getState<ExampleServiceState>(put)

        // example of structural validation: check if the new state is acceptable
        if (currentState.name != null && newState.name == null) {
            put.fail(IllegalArgumentException("name must be set"))
            return
        }

        updateCounter(newState, currentState, false)

        // replace current state, with the body of the request, in one step
        setState(put, newState)
        put.complete()
    }
}
