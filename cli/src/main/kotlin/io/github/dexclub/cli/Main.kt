package io.github.dexclub.cli

import io.github.dexclub.core.api.shared.createDefaultServices
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    DexKitCliBootstrap.configureNativeLibraryDir()
    val services = createDefaultServices()
    val exitCode = try {
        CliApp(services = services).run(
            argv = args.toList(),
            stdout = System.out,
            stderr = System.err,
        )
    } finally {
        services.closeDexService()
    }
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

private fun io.github.dexclub.core.api.shared.Services.closeDexService() {
    (dex as? AutoCloseable)?.close()
}
