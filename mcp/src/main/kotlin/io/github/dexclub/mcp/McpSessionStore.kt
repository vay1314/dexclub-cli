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

internal interface SourceBackedHandleRef {
    val sourcePath: String?
    val sourceEntry: String?
}

internal data class MethodHandleRef(
    val sessionId: String,
    val descriptor: String,
    override val sourcePath: String? = null,
    override val sourceEntry: String? = null,
) : SourceBackedHandleRef

internal data class ClassHandleRef(
    val sessionId: String,
    val descriptor: String,
    override val sourcePath: String? = null,
    override val sourceEntry: String? = null,
) : SourceBackedHandleRef

internal class McpSessionStore(
    private val nowProvider: () -> Instant = { Instant.now() },
) {
    private val targetSessions = ConcurrentHashMap<String, TargetSession>()
    private val methodHandles = ConcurrentHashMap<String, MethodHandleRef>()
    private val classHandles = ConcurrentHashMap<String, ClassHandleRef>()

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

    fun listTargetSessions(): List<TargetSession> =
        targetSessions.values
            .sortedByDescending { it.createdAt }

    fun closeTargetSession(sessionId: String): TargetSession? {
        clearSessionHandles(sessionId)
        return targetSessions.remove(sessionId)
    }

    fun putMethodHandle(sessionId: String, descriptor: String, sourcePath: String?, sourceEntry: String?): String {
        val handle = UUID.randomUUID().toString()
        methodHandles[handle] = MethodHandleRef(
            sessionId = sessionId,
            descriptor = descriptor,
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
        )
        return handle
    }

    fun getMethodHandle(sessionId: String, handle: String): MethodHandleRef? =
        methodHandles[handle]?.takeIf { it.sessionId == sessionId }

    fun putClassHandle(sessionId: String, descriptor: String, sourcePath: String?, sourceEntry: String?): String {
        val handle = UUID.randomUUID().toString()
        classHandles[handle] = ClassHandleRef(
            sessionId = sessionId,
            descriptor = descriptor,
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
        )
        return handle
    }

    fun getClassHandle(sessionId: String, handle: String): ClassHandleRef? =
        classHandles[handle]?.takeIf { it.sessionId == sessionId }

    private fun clearSessionHandles(sessionId: String) {
        methodHandles.entries.removeIf { it.value.sessionId == sessionId }
        classHandles.entries.removeIf { it.value.sessionId == sessionId }
    }
}
