package io.github.dexclub.mcp

import io.github.dexclub.core.api.workspace.WorkspaceContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class TargetSession(
    val sessionId: String,
    val workspace: WorkspaceContext,
    val createdAt: String,
)

internal class McpSessionStore(
    private val nowProvider: () -> Instant = { Instant.now() },
) {
    private val targetSessions = ConcurrentHashMap<String, TargetSession>()

    fun openTargetSession(workspace: WorkspaceContext): TargetSession {
        val session = TargetSession(
            sessionId = UUID.randomUUID().toString(),
            workspace = workspace,
            createdAt = nowProvider().toString(),
        )
        targetSessions[session.sessionId] = session
        return session
    }

    fun getTargetSession(sessionId: String): TargetSession? = targetSessions[sessionId]
}
