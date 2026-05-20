package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.serialization.Serializable
import java.io.File

class PhpToolset : McpToolset {

    @McpTool
    @McpDescription(
        "Find all usages of a PHP symbol (function, method, class, constant, property, or parameter). " +
        "If the symbol name is not fully qualified and multiple matches are found, returns a list of possible FQCNs."
    )
    suspend fun find_usages(
        @McpDescription("The symbol to search for. Can be a FQCN like '\\App\\Service\\EmailService::sendEmail' or a short name like 'sendEmail'")
        symbol: String,
        @McpDescription("Absolute path to the project root directory")
        projectPath: String? = null,
    ): FindUsagesResult {
        val project = resolveProject(projectPath)
        val service = project.getService(PhpFindUsagesService::class.java)

        val fqcnResult = service.resolveSymbol(symbol)
        if (fqcnResult is SymbolResolutionResult.Ambiguous) {
            return FindUsagesResult(
                error = "Symbol '$symbol' is ambiguous. Multiple symbols match. " +
                    "Please provide the fully qualified name. Possible matches:\n" +
                    fqcnResult.fqcns.joinToString("\n"),
                usages = emptyList(),
            )
        }

        val fqcn = when (fqcnResult) {
            is SymbolResolutionResult.Resolved -> fqcnResult.fqcn
            is SymbolResolutionResult.NotFound -> return FindUsagesResult(
                error = "Symbol '$symbol' not found in the project.",
                usages = emptyList(),
            )
        }

        val usages = service.findUsages(fqcn)
        return FindUsagesResult(
            error = null,
            usages = usages.map { usage ->
                UsageLocation(
                    file = usage.relativePath,
                    line = usage.line,
                    column = usage.column,
                    lineText = usage.lineText,
                    fqcn = fqcn,
                )
            },
        )
    }

    @McpTool
    @McpDescription(
        "Returns the public API contract of a PHP file: keeps public methods (with bodies stripped to signatures), " +
        "public fields, public constants, and all use/namespace declarations. " +
        "Removes non-public methods, fields, and method bodies. " +
        "Use this to see what a class exposes to the outside world."
    )
    suspend fun inspect_php_file(
        @McpDescription("Path to the PHP file relative to the project root")
        filePath: String,
        @McpDescription("Absolute path to the project root directory")
        projectPath: String? = null,
    ): InspectFileResult {
        val project = resolveProject(projectPath)
        val service = project.getService(PhpContractInspectorService::class.java)
        return service.inspectFile(filePath)
    }

    // TODO: Use McpProjectLocationInputs.resolveProject() from the MCP framework
    // for proper multi-project resolution (session headers, roots capability)
    private fun resolveProject(projectPath: String?): Project {
        val openProjects = ProjectManager.getInstance().openProjects

        if (projectPath != null) {
            val canonicalPath = File(projectPath).canonicalPath
            val matched = openProjects.firstOrNull {
                it.basePath != null && File(it.basePath).canonicalPath == canonicalPath
            }
            if (matched != null) return matched
        }

        if (openProjects.size == 1) return openProjects[0]

        val projectList = openProjects.mapNotNull { it.basePath }.joinToString("\n")
        val hint = if (projectPath != null) {
            "Project path '$projectPath' does not match any open project. "
        } else {
            "No project path provided. "
        }
        throw McpExpectedError(
            "Cannot determine the target project. $hint Open projects:\n$projectList",
        )
    }
}

@Serializable
data class FindUsagesResult(
    val error: String? = null,
    val usages: List<UsageLocation>,
)

@Serializable
data class UsageLocation(
    val file: String,
    val line: Int,
    val column: Int,
    val lineText: String,
    val fqcn: String,
)