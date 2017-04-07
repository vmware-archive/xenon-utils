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


package com.vmware.xenon.kotlin

import com.vmware.xenon.common.Operation
import com.vmware.xenon.common.ServiceRequestSender
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.reflect.KClass

/**
 * This extension lets you use a function literal for a completion handler
 */
inline fun Operation.setCompletion(crossinline c: (o: Operation, e: Throwable?) -> Unit) {
    this.completion = Operation.CompletionHandler { o, e ->
        c(o, e)
    }
}

fun <T : Any> Operation.getBody(c: KClass<T>): T {
    return this.getBody(c.java)
}


suspend fun <T : ServiceRequestSender> T.sendAsync(op: Operation): Operation {
    return suspendCoroutine { cont ->
        op.completion = Operation.CompletionHandler { o, e ->
            if (e != null) {
                cont.resumeWithException(e)
            } else {
                cont.resume(o!!)
            }
        }
        sendRequest(op)
    }
}

suspend fun Operation.sendAsync(sender: ServiceRequestSender): Operation {
    return suspendCoroutine { cont ->
        this.completion = Operation.CompletionHandler { o, e ->
            if (e != null) {
                cont.resumeWithException(e)
            } else {
                cont.resume(o!!)
            }
        }
        sendWith(sender)
    }
}
