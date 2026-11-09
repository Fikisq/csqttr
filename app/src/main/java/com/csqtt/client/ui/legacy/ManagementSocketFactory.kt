package com.csqtt.client.ui.legacy

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/** Protect before connect so removing the VPN server cannot disconnect SSH. */
internal class ManagementSocketFactory(private val protect: (Socket) -> Boolean) : SocketFactory() {
    override fun createSocket(): Socket = protectedSocket(null, 0)

    private fun protectedSocket(local: InetAddress?, localPort: Int): Socket {
        val socket = Socket()
        try {
            // Android's VpnService.protect needs a live descriptor. Socket() alone
            // is lazy; binding creates it without connecting through the VPN.
            socket.bind(InetSocketAddress(local, localPort))
            if (!protect(socket)) throw IOException("Не удалось исключить SSH из VPN")
            return socket
        } catch (e: Exception) {
            socket.close()
            throw e
        }
    }

    private fun connected(host: InetAddress, port: Int, local: InetAddress? = null, localPort: Int = 0): Socket {
        val socket = protectedSocket(local, localPort)
        try {
            socket.connect(InetSocketAddress(host, port), SSH_HANDSHAKE_TIMEOUT_MS)
            return socket
        } catch (e: Exception) {
            socket.close()
            throw e
        }
    }
    override fun createSocket(host: String, port: Int) = connected(InetAddress.getByName(host), port)
    override fun createSocket(host: InetAddress, port: Int) = connected(host, port)
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = connected(InetAddress.getByName(host), port, local, localPort)
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = connected(host, port, local, localPort)
}
