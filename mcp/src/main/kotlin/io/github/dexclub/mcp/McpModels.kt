package io.github.dexclub.mcp

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.FindClassesUsingStringsRequest
import io.github.dexclub.core.api.dex.FindMethodsUsingStringsRequest
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.FieldUsageType
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodDetailSection
import io.github.dexclub.core.api.dex.MethodFieldUsage
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.shared.InventoryCounts
import io.github.dexclub.core.api.shared.PageWindow
import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.workspace.TargetHandle
import io.github.dexclub.core.api.workspace.TargetSnapshotSummary
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.query.StringMatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class OpenTargetSessionResult(
    val sessionId: String,
    val createdAt: String,
    val workspace: WorkspaceContextView,
)

@Serializable
internal data class InspectMethodResult(
    val sessionId: String,
    val detail: MethodDetailView,
)

@Serializable
internal data class FindClassesUsingStringsResult(
    val sessionId: String,
    val total: Int,
    val items: List<ClassHitView>,
)

@Serializable
internal data class ExportTextResult(
    val sessionId: String,
    val descriptor: String,
    val view: String,
    val text: String,
)

@Serializable
internal data class FindMethodsUsingStringsResult(
    val sessionId: String,
    val total: Int,
    val items: List<MethodHitView>,
)

internal data class WindowedClassHits(
    val total: Int,
    val items: List<ClassHit>,
)

internal data class WindowedMethodHits(
    val total: Int,
    val items: List<MethodHit>,
)

@Serializable
internal data class WorkspaceContextView(
    val workdir: String,
    val dexclubDir: String,
    val workspaceId: String,
    val activeTargetId: String,
    val activeTarget: TargetHandleView,
    val snapshot: TargetSnapshotSummaryView,
)

@Serializable
internal data class TargetHandleView(
    val targetId: String,
    val inputType: InputType,
    val inputPath: String,
)

@Serializable
internal data class TargetSnapshotSummaryView(
    val kind: WorkspaceKind,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
    val capabilities: CapabilitySetView,
    val inventoryCounts: InventoryCountsView,
)

@Serializable
internal data class CapabilitySetView(
    val inspect: Boolean,
    val findClass: Boolean,
    val findMethod: Boolean,
    val findField: Boolean,
    val exportDex: Boolean,
    val exportSmali: Boolean,
    val exportJava: Boolean,
    val manifestDecode: Boolean,
    val resourceTableDecode: Boolean,
    val xmlDecode: Boolean,
    val resourceEntryList: Boolean,
)

@Serializable
internal data class InventoryCountsView(
    val apkCount: Int,
    val dexCount: Int,
    val manifestCount: Int,
    val arscCount: Int,
    val binaryXmlCount: Int,
)

@Serializable
internal data class MethodDetailView(
    val method: MethodHitView,
    val usingFields: List<MethodFieldUsageView>? = null,
    val callers: List<MethodHitView>? = null,
    val invokes: List<MethodHitView>? = null,
    val strings: List<String>? = null,
    val annotations: List<String>? = null,
)

@Serializable
internal data class MethodHitView(
    val className: String,
    val methodName: String,
    val descriptor: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)

@Serializable
internal data class ClassHitView(
    val className: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)

@Serializable
internal data class FieldHitView(
    val className: String,
    val fieldName: String,
    val descriptor: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)

@Serializable
internal data class MethodFieldUsageView(
    val usingType: FieldUsageType,
    val field: FieldHitView,
)

internal fun parseMethodDetailSections(rawValues: List<String>?): Set<MethodDetailSection> {
    if (rawValues.isNullOrEmpty()) return MethodDetailSection.entries.toSet()
    return rawValues.map { raw ->
        when (raw.trim()) {
            "using-fields" -> MethodDetailSection.UsingFields
            "callers" -> MethodDetailSection.Callers
            "invokes" -> MethodDetailSection.Invokes
            "strings" -> MethodDetailSection.Strings
            "annotations" -> MethodDetailSection.Annotations
            else -> throw IllegalArgumentException("Unsupported include section: $raw")
        }
    }.toSet()
}

internal fun TargetSession.toResult(): OpenTargetSessionResult =
    OpenTargetSessionResult(
        sessionId = sessionId,
        createdAt = createdAt,
        workspace = workspace.toView(),
    )

internal fun TargetSession.toInspectMethodResult(detail: MethodDetail): InspectMethodResult =
    InspectMethodResult(
        sessionId = sessionId,
        detail = detail.toView(),
    )

internal fun TargetSession.toFindClassesUsingStringsResult(result: WindowedClassHits): FindClassesUsingStringsResult =
    FindClassesUsingStringsResult(
        sessionId = sessionId,
        total = result.total,
        items = result.items.map(ClassHit::toView),
    )

internal fun TargetSession.toExportTextResult(
    descriptor: String,
    view: String,
    text: String,
): ExportTextResult = ExportTextResult(
    sessionId = sessionId,
    descriptor = descriptor,
    view = view,
    text = text,
)

internal fun TargetSession.toFindMethodsUsingStringsResult(result: WindowedMethodHits): FindMethodsUsingStringsResult =
    FindMethodsUsingStringsResult(
        sessionId = sessionId,
        total = result.total,
        items = result.items.map(MethodHit::toView),
    )

internal fun WorkspaceContext.toView(): WorkspaceContextView =
    WorkspaceContextView(
        workdir = workdir,
        dexclubDir = dexclubDir,
        workspaceId = workspaceId,
        activeTargetId = activeTargetId,
        activeTarget = activeTarget.toView(),
        snapshot = snapshot.toView(),
    )

internal fun TargetHandle.toView(): TargetHandleView =
    TargetHandleView(
        targetId = targetId,
        inputType = inputType,
        inputPath = inputPath,
    )

internal fun TargetSnapshotSummary.toView(): TargetSnapshotSummaryView =
    TargetSnapshotSummaryView(
        kind = kind,
        inventoryFingerprint = inventoryFingerprint,
        contentFingerprint = contentFingerprint,
        capabilities = capabilities.toView(),
        inventoryCounts = inventoryCounts.toView(),
    )

internal fun CapabilitySet.toView(): CapabilitySetView =
    CapabilitySetView(
        inspect = inspect,
        findClass = findClass,
        findMethod = findMethod,
        findField = findField,
        exportDex = exportDex,
        exportSmali = exportSmali,
        exportJava = exportJava,
        manifestDecode = manifestDecode,
        resourceTableDecode = resourceTableDecode,
        xmlDecode = xmlDecode,
        resourceEntryList = resourceEntryList,
    )

internal fun InventoryCounts.toView(): InventoryCountsView =
    InventoryCountsView(
        apkCount = apkCount,
        dexCount = dexCount,
        manifestCount = manifestCount,
        arscCount = arscCount,
        binaryXmlCount = binaryXmlCount,
    )

internal fun MethodDetail.toView(): MethodDetailView =
    MethodDetailView(
        method = method.toView(),
        usingFields = usingFields?.map(MethodFieldUsage::toView),
        callers = callers?.map(MethodHit::toView),
        invokes = invokes?.map(MethodHit::toView),
        strings = strings,
        annotations = annotations,
    )

internal fun MethodHit.toView(): MethodHitView =
    MethodHitView(
        className = className,
        methodName = methodName,
        descriptor = descriptor,
        sourcePath = sourcePath,
        sourceEntry = sourceEntry,
    )

internal fun ClassHit.toView(): ClassHitView =
    ClassHitView(
        className = className,
        sourcePath = sourcePath,
        sourceEntry = sourceEntry,
    )

internal fun FieldHit.toView(): FieldHitView =
    FieldHitView(
        className = className,
        fieldName = fieldName,
        descriptor = descriptor,
        sourcePath = sourcePath,
        sourceEntry = sourceEntry,
    )

internal fun MethodFieldUsage.toView(): MethodFieldUsageView =
    MethodFieldUsageView(
        usingType = usingType,
        field = field.toView(),
    )

internal fun buildFindMethodsUsingStringsRequest(
    strings: List<String>,
    requireAll: Boolean,
): FindMethodsUsingStringsRequest =
    FindMethodsUsingStringsRequest(
        queryText = Json.encodeToString(
            BatchFindMethodUsingStrings.serializer(),
            buildStringMatcherQuery(BatchFindMethodUsingStrings(), strings, requireAll),
        ),
        window = PageWindow(),
    )

internal fun buildFindClassesUsingStringsRequest(
    strings: List<String>,
    requireAll: Boolean,
): FindClassesUsingStringsRequest =
    FindClassesUsingStringsRequest(
        queryText = Json.encodeToString(
            BatchFindClassUsingStrings.serializer(),
            buildStringMatcherQuery(BatchFindClassUsingStrings(), strings, requireAll),
        ),
        window = PageWindow(),
    )

private fun buildStringMatcherQuery(
    query: BatchFindMethodUsingStrings,
    strings: List<String>,
    requireAll: Boolean,
): BatchFindMethodUsingStrings = query.apply {
    populateStringMatcherGroups(groups, strings, requireAll)
}

private fun buildStringMatcherQuery(
    query: BatchFindClassUsingStrings,
    strings: List<String>,
    requireAll: Boolean,
): BatchFindClassUsingStrings = query.apply {
    populateStringMatcherGroups(groups, strings, requireAll)
}

private fun populateStringMatcherGroups(
    groups: MutableMap<String, List<StringMatcher>>,
    strings: List<String>,
    requireAll: Boolean,
) {
    val normalized = strings.filter { it.isNotBlank() }
    if (requireAll) {
        if (normalized.isNotEmpty()) {
            groups["all"] = normalized.map(::containsMatcher)
        }
    } else {
        normalized.forEachIndexed { index, value ->
            groups["any-$index"] = listOf(containsMatcher(value))
        }
    }

    if (groups.isEmpty()) {
        throw IllegalArgumentException("At least one non-blank string filter is required")
    }
}

private fun containsMatcher(value: String): StringMatcher =
    StringMatcher(value = value, matchType = StringMatchType.Contains)

internal fun applyWindow(items: List<ClassHit>, offset: Int? = null, limit: Int? = null): WindowedClassHits =
    applyWindowSlice(items, offset, limit) { total, slice ->
        WindowedClassHits(total = total, items = slice)
    }

internal fun applyWindow(items: List<MethodHit>, offset: Int? = null, limit: Int? = null): WindowedMethodHits =
    applyWindowSlice(items, offset, limit) { total, slice ->
        WindowedMethodHits(total = total, items = slice)
    }

private fun <T, R> applyWindowSlice(
    items: List<T>,
    offset: Int?,
    limit: Int?,
    builder: (Int, List<T>) -> R,
): R {
    val normalizedOffset = offset ?: 0
    require(normalizedOffset >= 0) { "offset must be non-negative" }
    require(limit == null || limit > 0) { "limit must be positive when specified" }
    if (normalizedOffset >= items.size) {
        return builder(items.size, emptyList())
    }
    val toIndex = if (limit == null) items.size else minOf(items.size, normalizedOffset + limit)
    return builder(items.size, items.subList(normalizedOffset, toIndex))
}
