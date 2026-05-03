package io.github.dexclub.mcp

import io.github.dexclub.core.api.dex.InspectMethodRequest
import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.api.shared.createDefaultServices
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class McpApp(
    private val services: Services = createDefaultServices(),
    private val sessionStore: McpSessionStore = McpSessionStore(),
) {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "dexclub-mcp",
                version = "dev",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )

        server.addTool(
            name = "open_target_session",
            description = "初始化目标输入并创建可复用的 DexClub target session。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("input", buildJsonObject { put("type", "string") })
                },
                required = listOf("input"),
            ),
        ) { request ->
            val input = request.arguments?.get("input")?.jsonPrimitive?.content?.trim().orEmpty()
            if (input.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("""{"error":"input is required"}""")),
                    isError = true,
                )
            }

            val targetSession = openTargetSession(input)
            CallToolResult(
                content = listOf(
                    TextContent(json.encodeToString(OpenTargetSessionResult.serializer(), targetSession.toResult())),
                ),
            )
        }

        server.addTool(
            name = "inspect_method",
            description = "基于已打开的 target session 检查方法的一层事实视图。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                    put("descriptor", buildJsonObject { put("type", "string") })
                    put(
                        "include",
                        buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                        },
                    )
                },
                required = listOf("session_id", "descriptor"),
            ),
        ) { request ->
            val sessionId = request.arguments?.get("session_id")?.jsonPrimitive?.content?.trim().orEmpty()
            if (sessionId.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("""{"error":"session_id is required"}""")),
                    isError = true,
                )
            }
            val session = sessionStore.getTargetSession(sessionId)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("""{"error":"target session not found"}""")),
                    isError = true,
                )

            val descriptor = request.arguments?.get("descriptor")?.jsonPrimitive?.content?.trim().orEmpty()
            if (descriptor.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("""{"error":"descriptor is required"}""")),
                    isError = true,
                )
            }

            val includes = try {
                parseMethodDetailSections(
                    (request.arguments?.get("include") as? JsonArray)
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content },
                )
            } catch (cause: IllegalArgumentException) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("""{"error":"${cause.message}"}""")),
                    isError = true,
                )
            }

            val detail = inspectMethod(
                session = session,
                descriptor = descriptor,
                includes = includes,
            )
            CallToolResult(
                content = listOf(
                    TextContent(
                        json.encodeToString(
                            InspectMethodResult.serializer(),
                            session.toInspectMethodResult(detail),
                        ),
                    ),
                ),
            )
        }

        return server
    }

    fun createTransport(): StdioServerTransport =
        StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered(),
        )

    fun close() {
        (services.dex as? AutoCloseable)?.close()
    }

    internal fun openTargetSession(input: String): TargetSession {
        val workspace = services.workspace.initialize(input)
        return sessionStore.openTargetSession(workspace)
    }

    internal fun inspectMethod(
        session: TargetSession,
        descriptor: String,
        includes: Set<io.github.dexclub.core.api.dex.MethodDetailSection>,
    ) = services.dex.inspectMethod(
        workspace = session.workspace,
        request = InspectMethodRequest(
            descriptor = descriptor,
            includes = includes,
        ),
    )

    internal fun getTargetSession(sessionId: String): TargetSession? = sessionStore.getTargetSession(sessionId)
}
