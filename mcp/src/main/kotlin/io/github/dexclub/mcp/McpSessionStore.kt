package io.github.dexclub.mcp

import io.github.dexclub.core.api.workspace.WorkspaceContext
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class TargetSession(
    val sessionId: String,
    val workspace: WorkspaceContext,
    val createdAt: String,
    val lastAccessedAt: Instant,
)

internal data class SessionStoreSnapshot(
    val now: Instant,
    val idleTimeout: Duration?,
    val sessionCount: Int,
    val methodHandleCount: Int,
    val classHandleCount: Int,
    val sessions: List<TargetSession>,
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
    private val idleTimeout: Duration? = Duration.ofMinutes(30),
    private val nowProvider: () -> Instant = { Instant.now() },
) {
    private val targetSessions = ConcurrentHashMap<String, TargetSession>()
    private val methodHandles = ConcurrentHashMap<String, MethodHandleRef>()
    private val classHandles = ConcurrentHashMap<String, ClassHandleRef>()

    fun openTargetSession(workspace: WorkspaceContext): TargetSession {
        pruneExpiredSessions()
        val now = nowProvider()
        val session = TargetSession(
            sessionId = UUID.randomUUID().toString(),
            workspace = workspace,
            createdAt = now.toString(),
            lastAccessedAt = now,
        )
        targetSessions[session.sessionId] = session
        return session
    }

    fun getTargetSession(sessionId: String): TargetSession? {
        pruneExpiredSessions()
        return touchTargetSession(sessionId)
    }

    fun listTargetSessions(): List<TargetSession> =
        run {
            pruneExpiredSessions()
            targetSessions.values
        }
            .sortedByDescending { it.createdAt }

    fun closeTargetSession(sessionId: String): TargetSession? {
        pruneExpiredSessions()
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
        methodHandles[handle]
            ?.takeIf { it.sessionId == sessionId }

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
        classHandles[handle]
            ?.takeIf { it.sessionId == sessionId }

    fun pruneExpiredSessions() {
        val timeout = idleTimeout ?: return
        if (targetSessions.isEmpty()) return
        val cutoff = nowProvider().minus(timeout)
        targetSessions.entries.removeIf { entry ->
            val expired = entry.value.lastAccessedAt.isBefore(cutoff)
            if (expired) {
                clearSessionHandles(entry.key)
            }
            expired
        }
    }

    private fun touchTargetSession(sessionId: String): TargetSession? {
        val now = nowProvider()
        return targetSessions.computeIfPresent(sessionId) { _, session ->
            session.copy(lastAccessedAt = now)
        }
    }

    fun snapshot(): SessionStoreSnapshot {
        pruneExpiredSessions()
        return SessionStoreSnapshot(
            now = nowProvider(),
            idleTimeout = idleTimeout,
            sessionCount = targetSessions.size,
            methodHandleCount = methodHandles.size,
            classHandleCount = classHandles.size,
            sessions = targetSessions.values.sortedByDescending { it.createdAt },
        )
    }

    private fun clearSessionHandles(sessionId: String) {
        methodHandles.entries.removeIf { it.value.sessionId == sessionId }
        classHandles.entries.removeIf { it.value.sessionId == sessionId }
    }
}
