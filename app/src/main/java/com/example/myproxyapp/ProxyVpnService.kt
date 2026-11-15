package com.example.myproxyapp

import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var localServer: ServerSocket? = null
    @Volatile private var isRunning = false
    private val executor = Executors.newCachedThreadPool()

    companion object {
        @Volatile var isConnected = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra("HOST") ?: return START_NOT_STICKY
        val port = intent.getIntExtra("PORT", 8080)
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""

        startVpn(host, port, user, pass)
        return START_STICKY
    }

    private fun startVpn(remoteHost: String, remotePort: Int, user: String, pass: String) {
        if (isRunning) return
        isRunning = true

        executor.execute {
            try {
                localServer = ServerSocket(0)
                val localPort = localServer!!.localPort

                val builder = Builder()
                    .setSession("MyProxyVPN")
                    .addAddress("10.1.10.1", 32)
                    .addDisallowedApplication(packageName)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", localPort))
                }

                vpnInterface = builder.establish()
                isConnected = true

                val authHeader = if (user.isNotEmpty()) {
                    val credentials = "$user:$pass"
                    val base64 = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                    "Proxy-Authorization: Basic $base64\r\n"
                } else ""

                while (isRunning) {
                    val clientSocket = localServer!!.accept()
                    executor.execute {
                        handleClient(clientSocket, remoteHost, remotePort, authHeader)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopVpn()
            }
        }
    }

    private fun handleClient(client: Socket, remoteHost: String, remotePort: Int, authHeader: String) {
        var remote: Socket? = null
        try {
            remote = Socket()
            remote.connect(InetSocketAddress(remoteHost, remotePort), 10000)

            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()
            val remoteIn = remote.getInputStream()
            val remoteOut = remote.getOutputStream()

            val headerBuffer = StringBuilder()
            var b: Int
            while (clientIn.read().also { b = it } != -1) {
                headerBuffer.append(b.toChar())
                if (headerBuffer.endsWith("\r\n\r\n")) break
            }

            if (headerBuffer.isNotEmpty()) {
                val originalHeader = headerBuffer.toString()
                val modifiedHeader = if (authHeader.isNotEmpty()) {
                    originalHeader.replaceFirst("\r\n", "\r\n$authHeader")
                } else {
                    originalHeader
                }
                remoteOut.write(modifiedHeader.toByteArray())
                remoteOut.flush()
            }

            val t1 = executor.submit { pipe(clientIn, remoteOut) }
            val t2 = executor.submit { pipe(remoteIn, clientOut) }
            t1.get()
            t2.get()
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { remote?.close() } catch (_: Exception) {}
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        try {
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    private fun stopVpn() {
        isRunning = false
        isConnected = false
        try { localServer?.close() } catch (_: Exception) {}
        try { vpnInterface?.close() } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
