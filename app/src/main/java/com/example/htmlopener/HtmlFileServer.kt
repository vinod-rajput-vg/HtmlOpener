package com.example.htmlopener

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class HtmlFileServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()
    private var rootUri: Uri? = null

    fun start(uri: Uri): String {
        stop()
        rootUri = uri
        serverSocket = ServerSocket(0)
        val port = serverSocket!!.localPort

        serverThread = Thread {
            while (serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val socket = serverSocket!!.accept()
                    executor.execute { handleClient(socket) }
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        return "http://127.0.0.1:$port/"
    }

    private fun handleClient(socket: Socket) {
        socket.use {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()
                val requestLine = reader.readLine() ?: return

                parsePath(requestLine)

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                serveFile(output)
            } catch (_: Exception) {
            }
        }
    }

    private fun parsePath(requestLine: String): String {
        val parts = requestLine.split(" ")
        if (parts.size < 2) return "/"
        return try {
            URLDecoder.decode(parts[1], "UTF-8")
        } catch (_: Exception) {
            "/"
        }
    }

    private fun serveFile(output: java.io.OutputStream) {
        val uri = rootUri ?: run {
            sendError(output)
            return
        }

        val inputStream = try {
            if (uri.scheme == "file") {
                FileInputStream(File(uri.path ?: return sendError(output)))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }

        if (inputStream == null) {
            sendError(output)
            return
        }

        inputStream.use { input ->
            val content = input.readBytes()
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("Content-Length: ${content.size}\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }

            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(content)
            output.flush()
        }
    }

    private fun sendError(output: java.io.OutputStream) {
        val body = "HTML file could not be opened."
        val response = buildString {
            append("HTTP/1.1 404 Not Found\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Length: ${body.toByteArray().size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append(body)
        }

        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
        serverThread = null
        rootUri = null
    }
}
