package io.github.dexclub.mcp

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    DexKitMcpBootstrap.configureNativeLibraryDir()
    val app = McpApp()
    try {
        val server = app.createServer()
        server.createSession(app.createTransport())
        awaitCancellation()
    } finally {
        app.close()
    }
}
