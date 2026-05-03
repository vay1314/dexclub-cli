package io.github.dexclub.mcp

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.DexAnalysisService
import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportMethodDexRequest
import io.github.dexclub.core.api.dex.ExportMethodJavaRequest
import io.github.dexclub.core.api.dex.ExportMethodSmaliRequest
import io.github.dexclub.core.api.dex.ExportResult
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.FindClassesRequest
import io.github.dexclub.core.api.dex.FindClassesUsingStringsRequest
import io.github.dexclub.core.api.dex.FindFieldsRequest
import io.github.dexclub.core.api.dex.FindMethodsRequest
import io.github.dexclub.core.api.dex.FindMethodsUsingStringsRequest
import io.github.dexclub.core.api.dex.InspectMethodRequest
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodDetailSection
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.shared.CacheState
import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.shared.InventoryCounts
import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.api.shared.WorkspaceIssue
import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.resource.DecodeXmlRequest
import io.github.dexclub.core.api.resource.DecodedXmlResult
import io.github.dexclub.core.api.resource.FindResourcesRequest
import io.github.dexclub.core.api.resource.ManifestResult
import io.github.dexclub.core.api.resource.ResourceEntry
import io.github.dexclub.core.api.resource.ResourceEntryValueHit
import io.github.dexclub.core.api.resource.ResourceService
import io.github.dexclub.core.api.resource.ResourceTableResult
import io.github.dexclub.core.api.resource.ResourceValue
import io.github.dexclub.core.api.resource.ResolveResourceRequest
import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.api.workspace.InspectResult
import io.github.dexclub.core.api.workspace.TargetHandle
import io.github.dexclub.core.api.workspace.TargetSnapshotSummary
import io.github.dexclub.core.api.workspace.TargetSummary
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceService
import io.github.dexclub.core.api.workspace.WorkspaceStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class McpAppTest {
    @Test
    fun openTargetSessionDelegatesToWorkspaceInitializeAndCachesSession() {
        val workspace = fakeWorkspaceContext()
        val workspaceService = FakeWorkspaceService(workspace)
        val dexService = FakeDexAnalysisService()
        val app = McpApp(
            services = Services(
                workspace = workspaceService,
                dex = dexService,
                resource = FakeResourceService(),
            ),
            sessionStore = McpSessionStore(),
        )

        val session = app.openTargetSession("sample.apk")

        assertEquals("sample.apk", workspaceService.initializedInput)
        assertEquals(workspace.workdir, session.workspace.workdir)
        assertEquals(session, app.getTargetSession(session.sessionId))
    }

    @Test
    fun inspectMethodUsesSessionWorkspaceAndIncludes() {
        val workspace = fakeWorkspaceContext()
        val workspaceService = FakeWorkspaceService(workspace)
        val dexService = FakeDexAnalysisService(
            detail = MethodDetail(
                method = MethodHit(
                    className = "Lsample/Test;",
                    methodName = "foo",
                    descriptor = "Lsample/Test;->foo()V",
                    sourcePath = "sample.apk",
                    sourceEntry = "classes.dex",
                ),
                strings = listOf("alpha"),
                annotations = listOf("Lsample/Anno;"),
            ),
        )
        val app = McpApp(
            services = Services(
                workspace = workspaceService,
                dex = dexService,
                resource = FakeResourceService(),
            ),
            sessionStore = McpSessionStore(),
        )
        val session = app.openTargetSession("sample.apk")

        val detail = app.inspectMethod(
            session = session,
            descriptor = "Lsample/Test;->foo()V",
            includes = setOf(MethodDetailSection.Strings, MethodDetailSection.Annotations),
        )

        assertEquals(workspace, dexService.lastWorkspace)
        assertEquals("Lsample/Test;->foo()V", dexService.lastInspectRequest?.descriptor)
        assertEquals(setOf(MethodDetailSection.Strings, MethodDetailSection.Annotations), dexService.lastInspectRequest?.includes)
        assertEquals(listOf("alpha"), detail.strings)
        assertEquals(listOf("Lsample/Anno;"), detail.annotations)
    }

    @Test
    fun parseMethodDetailSectionsSupportsCliStyleNames() {
        val sections = parseMethodDetailSections(listOf("using-fields", "callers", "invokes", "strings", "annotations"))

        assertEquals(
            setOf(
                MethodDetailSection.UsingFields,
                MethodDetailSection.Callers,
                MethodDetailSection.Invokes,
                MethodDetailSection.Strings,
                MethodDetailSection.Annotations,
            ),
            sections,
        )
    }

    @Test
    fun parseMethodDetailSectionsFallsBackToAllWhenMissing() {
        val sections = parseMethodDetailSections(null)

        assertEquals(MethodDetailSection.entries.toSet(), sections)
    }

    private fun fakeWorkspaceContext(): WorkspaceContext =
        WorkspaceContext(
            workdir = "D:/tmp/workspace",
            dexclubDir = "D:/tmp/workspace/.dexclub",
            workspaceId = "ws-1",
            activeTargetId = "target-1",
            activeTarget = TargetHandle(
                targetId = "target-1",
                inputType = InputType.File,
                inputPath = "sample.apk",
            ),
            snapshot = TargetSnapshotSummary(
                kind = WorkspaceKind.Apk,
                inventoryFingerprint = "inv-1",
                contentFingerprint = "content-1",
                capabilities = CapabilitySet(
                    inspect = true,
                    findClass = true,
                    findMethod = true,
                    exportSmali = true,
                ),
                inventoryCounts = InventoryCounts(
                    apkCount = 1,
                    dexCount = 2,
                    manifestCount = 1,
                    arscCount = 1,
                    binaryXmlCount = 3,
                ),
            ),
        )
}

private class FakeWorkspaceService(
    private val workspace: WorkspaceContext,
) : WorkspaceService {
    var initializedInput: String? = null

    override fun initialize(input: String): WorkspaceContext {
        initializedInput = input
        return workspace
    }

    override fun switchTarget(ref: WorkspaceRef, input: String): WorkspaceRef = ref

    override fun open(ref: WorkspaceRef): WorkspaceContext = workspace

    override fun listTargets(ref: WorkspaceRef): List<TargetSummary> = emptyList()

    override fun loadStatus(ref: WorkspaceRef): WorkspaceStatus =
        WorkspaceStatus(
            workspaceId = workspace.workspaceId,
            activeTargetId = workspace.activeTargetId,
            state = WorkspaceState.Healthy,
            issues = emptyList<WorkspaceIssue>(),
            activeTarget = workspace.activeTarget,
            snapshot = workspace.snapshot,
            cacheState = CacheState.Present,
        )

    override fun gc(workspace: WorkspaceContext): GcResult =
        GcResult(
            workdir = workspace.workdir,
            targetId = workspace.activeTargetId,
            deletedFiles = 0,
            deletedBytes = 0,
        )

    override fun inspect(workspace: WorkspaceContext): InspectResult =
        InspectResult(
            target = workspace.activeTarget,
            snapshot = workspace.snapshot,
            classCount = null,
        )
}

private class FakeDexAnalysisService(
    private val detail: MethodDetail = MethodDetail(
        method = MethodHit(
            className = "Lsample/Test;",
            methodName = "foo",
            descriptor = "Lsample/Test;->foo()V",
        ),
    ),
) : DexAnalysisService {
    var lastWorkspace: WorkspaceContext? = null
    var lastInspectRequest: InspectMethodRequest? = null

    override fun findClasses(workspace: WorkspaceContext, request: FindClassesRequest): List<ClassHit> = emptyList()

    override fun findMethods(workspace: WorkspaceContext, request: FindMethodsRequest): List<MethodHit> = emptyList()

    override fun findFields(workspace: WorkspaceContext, request: FindFieldsRequest): List<FieldHit> = emptyList()

    override fun findClassesUsingStrings(
        workspace: WorkspaceContext,
        request: FindClassesUsingStringsRequest,
    ): List<ClassHit> = emptyList()

    override fun findMethodsUsingStrings(
        workspace: WorkspaceContext,
        request: FindMethodsUsingStringsRequest,
    ): List<MethodHit> = emptyList()

    override fun inspectMethod(workspace: WorkspaceContext, request: InspectMethodRequest): MethodDetail {
        lastWorkspace = workspace
        lastInspectRequest = request
        return detail
    }

    override fun exportClassDex(workspace: WorkspaceContext, request: ExportClassDexRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportClassSmali(workspace: WorkspaceContext, request: ExportClassSmaliRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportClassJava(workspace: WorkspaceContext, request: ExportClassJavaRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportMethodSmali(workspace: WorkspaceContext, request: ExportMethodSmaliRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportMethodDex(workspace: WorkspaceContext, request: ExportMethodDexRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportMethodJava(workspace: WorkspaceContext, request: ExportMethodJavaRequest): ExportResult =
        ExportResult(request.outputPath)
}

private class FakeResourceService : ResourceService {
    override fun decodeManifest(workspace: WorkspaceContext): ManifestResult =
        ManifestResult(text = "<manifest/>")

    override fun dumpResourceTable(workspace: WorkspaceContext): ResourceTableResult =
        ResourceTableResult(packageCount = 0, typeCount = 0, entryCount = 0)

    override fun decodeXml(workspace: WorkspaceContext, request: DecodeXmlRequest): DecodedXmlResult =
        DecodedXmlResult(text = "<xml/>")

    override fun listResourceEntries(workspace: WorkspaceContext): List<ResourceEntry> = emptyList()

    override fun resolveResourceValue(workspace: WorkspaceContext, request: ResolveResourceRequest): ResourceValue =
        ResourceValue(type = "string", name = "name", value = null)

    override fun findResourceEntries(
        workspace: WorkspaceContext,
        request: FindResourcesRequest,
    ): List<ResourceEntryValueHit> = emptyList()
}
