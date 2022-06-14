/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.network.sockets.*

public class Route(
    public val host: SocketAddress,
    public val localAddress: SocketAddress?,
    public val proxy: ProxyConfig?,
    public val secure: Boolean
) {
    public companion object {
        /**
         * Returns the ID of the provided [Route].
         */
        public fun getId(route: Route): String = buildString {
            route.localAddress?.let { append("$it->") }
            append("{${if (route.secure) "tls" else ""}}")
            route.proxy?.let { append("$it->") }
            append("${route.host}")
        }
    }

    /**
     * The ID of this route.
     */
    public val id: String
        get() = getId(this)
}
