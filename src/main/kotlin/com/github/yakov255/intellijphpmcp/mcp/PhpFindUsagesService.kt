package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

sealed class SymbolResolutionResult {
    data class Resolved(val fqcn: String) : SymbolResolutionResult()
    data class Ambiguous(val fqcns: List<String>) : SymbolResolutionResult()
    data object NotFound : SymbolResolutionResult()
}

data class UsageInfo(
    val relativePath: String,
    val line: Int,
    val column: Int,
    val lineText: String,
)

@Service(Service.Level.PROJECT)
class PhpFindUsagesService(private val project: Project) {

    fun resolveSymbol(symbol: String): SymbolResolutionResult {
        // Stub implementation — will be replaced with real PHP PSI resolution
        // TODO: Use com.jetbrains.php.lang.psi.PhpSymbolUtil or similar to resolve the symbol
        // If symbol contains '\', treat it as potentially fully qualified
        if (symbol.contains('\\') || symbol.contains("::")) {
            // Assume it's a FQCN — will be validated by PSI later
            return SymbolResolutionResult.Resolved(symbol)
        }
        // Short name — in real implementation, search PSI for all matching symbols
        // For now, return ambiguity if it could match multiple symbols
        return SymbolResolutionResult.Ambiguous(
            listOf(
                "\\App\\Service\\$symbol",
                "\\App\\Model\\$symbol",
            )
        )
    }

    fun findUsages(fqcn: String): List<UsageInfo> {
        // Stub implementation — will be replaced with real PHP find usages
        // TODO: Use com.intellij.find.findUsages.FindUsagesManager or
        //       com.jetbrains.php.lang.findUsages.PhpFindUsagesProvider
        return emptyList()
    }
}