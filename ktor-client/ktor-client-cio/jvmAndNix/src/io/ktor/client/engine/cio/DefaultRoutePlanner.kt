/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*

public class DefaultRoutePlanner : RoutePlanner {
    override suspend fun planRoute(data: HttpRequestData, proxy: ProxyConfig?): Route {
        val host: String
        val port: Int
        val protocol: URLProtocol = data.url.protocol

        if (proxy != null) {
            val proxyAddress = proxy.resolveAddress()
            host = proxyAddress.hostname
            port = proxyAddress.port
        } else {
            host = data.url.host
            port = data.url.port
        }

        return Route(InetSocketAddress(host, port), null, proxy, protocol.isSecure())
    }
}
