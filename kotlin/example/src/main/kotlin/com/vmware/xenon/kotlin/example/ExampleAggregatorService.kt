package com.vmware.xenon.kotlin.example


import com.google.gson.JsonObject
import com.vmware.xenon.common.*
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName
import com.vmware.xenon.kotlin.getBody
import com.vmware.xenon.kotlin.sendAsync
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState
import com.vmware.xenon.services.common.QueryTask
import com.vmware.xenon.services.common.QueryTask.Query
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType
import com.vmware.xenon.services.common.ServiceUriPaths
import kotlinx.coroutines.experimental.runBlocking
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Example showing coroutine-enabled service handler
 */
class ExampleAggregatorService : StatelessService(ServiceDocument::class.java) {

    companion object {
        // must use JvmField for Xenon's sake
        @JvmField
        val SELF_LINK = "/core/kotlin/example-aggregator"
    }

    init {
        toggleOption(Service.ServiceOption.PERIODIC_MAINTENANCE, true)
        maintenanceIntervalMicros = TimeUnit.SECONDS.toMicros(5)
    }

    /**
     * Notice the *runBlocking* call. This will create a coroutine letting you use the
     * Service#sendAsync method inside the method body.
     *
     * See [https://kotlinlang.org/docs/reference/coroutines.html]
     */
    override fun handleGet(get: Operation) = runBlocking {
        // newest 10 documents whose name start with "a"
        val task = QueryTask.Builder.createDirectTask()
                .setResultLimit(10)
                .addOption(QueryOption.TOP_RESULTS)
                .orderDescending(ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS, TypeName.STRING)
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(Query.Builder.create()
                        .addKindFieldClause(ExampleServiceState::class.java)
                        .addFieldClause(ExampleServiceState.FIELD_NAME_NAME, "a*", MatchType.WILDCARD)
                        .build())
                .build()


        // no callback hell, use the async send
        // This will NOT block the current thread
        var op = Operation.createPost(UriUtils.buildUri(host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(task)
                .sendAsync(this@ExampleAggregatorService)

        val r = op.getBody(QueryTask::class).results;

        var totalCount = 0L
        val uniqueKeys = TreeSet<String>()
        val selectedNames = TreeSet<String>()

        // compute some made-up statistics on the
        if (r.documents != null) {
            for (doc in r.documents.values) {
                when (doc) {
                    is JsonObject -> {
                        totalCount += doc["counter"].asInt
                        for (entry in doc["keyValues"].asJsonObject.entrySet()) {
                            uniqueKeys.add(entry.key)
                        }
                        selectedNames.add(doc["name"].asString)
                    }
                    is ExampleServiceState -> {
                        totalCount += doc.counter;
                        uniqueKeys.addAll(doc.keyValues.keys)
                        selectedNames.add(doc.name)
                    }
                }
            }
        }


        // invent a body on the fly
        get.setBody(mapOf("totalCount" to totalCount,
                "keys" to uniqueKeys,
                "names" to selectedNames))

        // Still have to complete the request
        get.complete()
    }

    /**
     * create some random example services  every few seconds
     */
    override fun handleMaintenance(post: Operation) {
        post.complete()

        for (i in 1..4) {
            host.log(Level.INFO, "creating an example service " + i)
            val state = ExampleServiceState()
            state.name = UUID.randomUUID().toString()
            state.counter = ThreadLocalRandom.current().nextLong(100)

            state.keyValues = mapOf(
                    randShortString() to "first value",
                    randShortString() to "second value"
            )

            val op = Operation.createPost(UriUtils.buildUri(host, KotlinExampleService.FACTORY_LINK))
                    .setBody(state)

            sendRequest(op)
        }
    }

    /**
     * Produces a random string of length 2.
     */
    private fun randShortString(): String {
        val random = ThreadLocalRandom.current()
        return "" + ('a' + random.nextInt(26)) +
                ('a' + random.nextInt(26))
    }
}