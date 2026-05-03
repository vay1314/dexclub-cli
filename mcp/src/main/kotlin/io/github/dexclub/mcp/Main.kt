package io.github.dexclub.mcp

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    // MCP stdio 握手不能被任何 stdout 噪音污染。
    configureSlf4jSimpleDefaults()
    disableKotlinLoggingStartupMessage()
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

private fun configureSlf4jSimpleDefaults() {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error")
}

private fun disableKotlinLoggingStartupMessage() {
    runCatching {
        val configClass = Class.forName("io.github.oshai.kotlinlogging.KotlinLoggingConfiguration")
        val instance = configClass.getField("INSTANCE").get(null)
        configClass.getMethod("setLogStartupMessage", Boolean::class.javaPrimitiveType)
            .invoke(instance, false)
    }
}
