/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*

internal class SocksSocketFactory(
    private val selector: SelectorManager,
    proxy: ProxyConfig
) : SocketFactory {
    companion object {
        private const val SOCKS_VERSION = 0x05.toByte()
        private const val SOCKS_CONNECT = 0x01.toByte()
        private const val SOCKS_NO_AUTH = 0x00.toByte()

        private const val SOCKS_REQUEST_GRANTED = 0x00.toByte()

        private const val SOCKS_ADDR_IPV4 = 0x01.toByte()
        private const val SOCKS_ADDR_DOMAIN = 0x03.toByte()
        private const val SOCKS_ADDR_IPV6 = 0x04.toByte()
    }

    private val proxyAddress: InetSocketAddress

    init {
        val address = proxy.address()
        proxyAddress = InetSocketAddress(address.hostname, address.port)
    }

    override suspend fun create(
        address: InetSocketAddress,
        configuration: SocketOptions.TCPClientSocketOptions.() -> Unit
    ): Connection {
        val socket = aSocket(selector)
            .tcpNoDelay()
            .tcp()
            .connect(proxyAddress)

        val connection = socket.connection()
        performSocksHandshake(connection)
        performSocksConnect(connection, address)
        
        return connection
    }

    private suspend fun performSocksHandshake(connection: Connection) {
        val packet = BytePacketBuilder()
        packet.writeByte(SOCKS_VERSION) // ver
        packet.writeByte(0x01)       // nauth
        packet.writeByte(SOCKS_NO_AUTH) // auth

        connection.output.writePacket(packet.build())
        connection.output.flush()

        // read response
        require (connection.input.readByte() == SOCKS_VERSION)

        val authType = connection.input.readByte()
        require(authType == SOCKS_NO_AUTH)
    }

    private suspend fun performSocksConnect(
        connection: Connection,
        address: InetSocketAddress
    ): InetSocketAddress {
        val packet = BytePacketBuilder()
        packet.writeByte(SOCKS_VERSION)
        packet.writeByte(SOCKS_CONNECT)
        packet.writeByte(0x00) // reserved value

        if (hostIsIp(address.hostname)) {
            if (hostIsIpv4(address.hostname)) {
                packet.writeByte(SOCKS_ADDR_IPV4)
                for (part in address.hostname.split('.', limit = 4)) {
                    packet.writeByte(part.toByte())
                }
            } else {
                packet.writeByte(SOCKS_ADDR_IPV6)
                // TODO: ivp6 to binary
            }
        } else {
            packet.writeByte(SOCKS_ADDR_DOMAIN)
            packet.writeByte(address.hostname.length.toByte())
            packet.writeText(address.hostname)
        }

        packet.writeShort(address.port.toShort())

        connection.output.writePacket(packet.build())
        connection.output.flush()

        // read response
        require (connection.input.readByte() == SOCKS_VERSION)

        val statusCode = connection.input.readByte()
        require (statusCode == SOCKS_REQUEST_GRANTED)

        connection.input.readByte() // rsv

        val host: String = when (val type = connection.input.readByte()) {
            SOCKS_ADDR_IPV4 -> buildString {
                repeat(4) {
                    val part = connection.input.readByte().toUByte()
                    append(part)
                    if (it != 3) append('.')
                }
            }

            SOCKS_ADDR_IPV6 -> TODO()

            SOCKS_ADDR_DOMAIN -> {
                val len = connection.input.readByte().toUByte().toInt()
                connection.input.readUTF8Line(len) ?: error("Unable to read domain address")
            }

            else -> error("Unknown SOCKS address type: $type")
        }

        val port = connection.input.readShort().toUShort().toInt()
        return InetSocketAddress(host, port)
    }
}
