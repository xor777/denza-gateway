package dev.denza.adbbridge

import org.apache.sshd.common.digest.BuiltinDigests
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator
import org.apache.sshd.server.forward.DirectTcpipFactory
import org.apache.sshd.server.forward.ForwardingFilter
import org.apache.sshd.server.forward.TcpForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.common.config.keys.KeyUtils
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

class SshGatewayServer(
    private val bindAddress: Inet4Address,
    private val port: Int,
    private val allowedSubnet: Ipv4Subnet,
    private val endpoint: AdbEndpoint,
    private val hostKeyFile: File,
    private val codeProvider: () -> String,
    private val onLog: (LogLevel, String) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onBlockedPeer: (String) -> Unit,
) {
    private var sshServer: SshServer? = null

    fun start(): String {
        val keyProvider = createKeyProvider()
        val fingerprint = keyProvider.loadKeys(null).firstOrNull()?.public?.let {
            KeyUtils.getFingerPrint(BuiltinDigests.sha256, it)
        }.orEmpty()

        val server = SshServer.setUpDefaultServer().apply {
            host = bindAddress.hostAddress
            this.port = this@SshGatewayServer.port
            keyPairProvider = keyProvider
            publickeyAuthenticator = RejectAllPublickeyAuthenticator.INSTANCE
            keyboardInteractiveAuthenticator = null
            channelFactories = listOf(DirectTcpipFactory.INSTANCE)
            forwardingFilter = buildForwardingFilter()
            passwordAuthenticator = buildPasswordAuthenticator()
            shellFactory = null
            commandFactory = null
            subsystemFactories = emptyList()
        }

        server.start()
        sshServer = server
        onLog(LogLevel.Info, "SSH gateway listening on ${bindAddress.hostAddress}:$port")
        return fingerprint
    }

    fun stop() {
        sshServer?.stop(true)
        sshServer = null
        onLog(LogLevel.Info, "SSH gateway stopped")
    }

    private fun createKeyProvider(): SimpleGeneratorHostKeyProvider =
        SimpleGeneratorHostKeyProvider(hostKeyFile.toPath()).apply {
            setStrictFilePermissions(false)
        }

    private fun buildPasswordAuthenticator() =
        org.apache.sshd.server.auth.password.PasswordAuthenticator { username, password, session ->
            val peer = session.clientInetAddress()
            val subnetOk = allowedSubnet.contains(peer)
            val accepted = username == SSH_USER && password == codeProvider() && subnetOk
            when {
                !subnetOk -> blockPeer(peer, "auth denied from outside ${allowedSubnet}")
                accepted -> {
                    onClientConnected()
                    onLog(LogLevel.Info, "SSH auth accepted from ${peer?.hostAddress ?: "unknown peer"}")
                }
                else -> onLog(LogLevel.Warn, "SSH auth rejected for user '$username' from ${peer?.hostAddress ?: "unknown peer"}")
            }
            accepted
        }

    private fun buildForwardingFilter(): ForwardingFilter =
        object : ForwardingFilter {
            override fun canForwardAgent(session: Session, requestType: String): Boolean = false

            override fun canForwardX11(session: Session, requestType: String): Boolean = false

            override fun canListen(address: SshdSocketAddress, session: Session): Boolean {
                onLog(LogLevel.Warn, "Remote forwarding denied: ${address.hostName}:${address.port}")
                return false
            }

            override fun canConnect(
                type: TcpForwardingFilter.Type,
                address: SshdSocketAddress,
                session: Session,
            ): Boolean {
                val peer = session.clientInetAddress()
                if (!allowedSubnet.contains(peer)) {
                    blockPeer(peer, "forward denied from outside ${allowedSubnet}")
                    return false
                }
                if (type != TcpForwardingFilter.Type.Direct) {
                    onLog(LogLevel.Warn, "Forwarding denied: unsupported channel ${type.name}")
                    return false
                }

                val destinationOk = ForwardingPolicy.isAllowedDestination(endpoint, address.hostName, address.port)
                if (!destinationOk) {
                    onLog(
                        LogLevel.Warn,
                        "Forwarding denied to ${address.hostName}:${address.port}; only ${endpoint.host}:${endpoint.port} is allowed",
                    )
                    return false
                }

                onClientConnected()
                onLog(LogLevel.Info, "Forwarding ${peer?.hostAddress ?: "unknown peer"} -> ${endpoint.host}:${endpoint.port}")
                return true
            }
        }

    private fun blockPeer(peer: InetAddress?, reason: String) {
        val peerLabel = peer?.hostAddress ?: "unknown peer"
        onBlockedPeer("$reason ($peerLabel)")
        onLog(LogLevel.Warn, "$reason ($peerLabel)")
    }

    private fun Session.clientInetAddress(): InetAddress? {
        val address = (this as? ServerSession)?.clientAddress as? InetSocketAddress
        return address?.address
    }

}
