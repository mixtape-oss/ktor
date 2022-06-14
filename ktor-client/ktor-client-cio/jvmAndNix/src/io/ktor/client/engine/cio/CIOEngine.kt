/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.network.selector.*
import io.ktor.util.*
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
internal class CIOEngine(
    override val config: CIOEngineConfig
) : HttpClientEngineBase("ktor-cio") {

    override val dispatcher by lazy {
        Dispatchers.clientDispatcher(config.threadsCount, "ktor-cio-dispatcher")
    }

    override val supportedCapabilities = setOf(HttpTimeout, WebSocketCapability, WebSocketExtensionsCapability)

    private val endpoints = ConcurrentMap<String, Endpoint>()

    private val selectorManager: SelectorManager by lazy { SelectorManager(dispatcher) }

    private val connectionFactory = ConnectionFactory(selectorManager, config.maxConnectionsCount)

    private val requestsJob: CoroutineContext

    override val coroutineContext: CoroutineContext

    private val proxy: ProxyConfig? = when (val type = config.proxy?.type) {
        ProxyType.SOCKS,
        null -> null
        ProxyType.HTTP -> config.proxy
        else -> throw IllegalStateException("CIO engine does not currently support $type proxies.")
    }

    init {
        val parentContext = super.coroutineContext
        val parent = parentContext[Job]!!

        requestsJob = SilentSupervisor(parent)

        val requestField = requestsJob
        coroutineContext = parentContext + requestField

        val requestJob = requestField[Job]!!
        val selector = selectorManager

        @OptIn(ExperimentalCoroutinesApi::class)
        GlobalScope.launch(parentContext, start = CoroutineStart.ATOMIC) {
            try {
                requestJob.join()
            } finally {
                selector.close()
                selector.coroutineContext[Job]!!.join()
            }
        }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        while (coroutineContext.isActive) {
            val route = config.routePlanner.planRoute(data, proxy)
            val endpoint = selectEndpoint(route)

            try {
                return endpoint.execute(data, callContext)
            } catch (cause: ClosedSendChannelException) {
                continue
            } finally {
                if (!coroutineContext.isActive) {
                    endpoint.close()
                }
            }
        }

        throw ClientEngineClosedException()
    }

    override fun close() {
        super.close()

        endpoints.forEach { (_, endpoint) ->
            endpoint.close()
        }

        (requestsJob[Job] as CompletableJob).complete()
    }

    private fun selectEndpoint(route: Route): Endpoint {
        return endpoints.computeIfAbsent(route.id) {
            Endpoint(
                route,
                config,
                connectionFactory,
                coroutineContext,
                onDone = { endpoints.remove(route.id) }
            )
        }
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Use ClientEngineClosedException instead",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ClientEngineClosedException")
)
public class ClientClosedException(cause: Throwable? = null) : IllegalStateException("Client already closed", cause)
