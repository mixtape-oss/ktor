/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*

internal class DefaultSocketFactory(private val selector: SelectorManager) : SocketFactory {
    override suspend fun create(
        address: InetSocketAddress,
        configuration: SocketOptions.TCPClientSocketOptions.() -> Unit
    ): Connection {
        val socket = aSocket(selector).tcpNoDelay().tcp().connect(address, configuration)
        return socket.connection()
    }
}
