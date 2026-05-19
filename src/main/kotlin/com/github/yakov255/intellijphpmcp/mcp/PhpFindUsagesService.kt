package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.jetbrains.php.PhpIndex

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
        val clean = symbol.trimStart('\\')
        if (clean.contains("::")) return resolveMemberSymbol(clean)
        if (clean.contains('\\')) return resolveFqcn(clean)
        return resolveShortName(clean)
    }

    private fun resolveMemberSymbol(fullSymbol: String): SymbolResolutionResult {
        val className = fullSymbol.substringBefore("::").trimStart('\\')
        return ReadAction.compute<SymbolResolutionResult, RuntimeException> {
            val phpIndex = PhpIndex.getInstance(project)
            if (classExists(className, phpIndex)) SymbolResolutionResult.Resolved(fullSymbol)
            else SymbolResolutionResult.NotFound
        }
    }

    private fun resolveFqcn(fqcn: String): SymbolResolutionResult {
        return ReadAction.compute<SymbolResolutionResult, RuntimeException> {
            val phpIndex = PhpIndex.getInstance(project)
            if (classExists(fqcn, phpIndex)) SymbolResolutionResult.Resolved(fqcn)
            else SymbolResolutionResult.NotFound
        }
    }

    private fun resolveShortName(name: String): SymbolResolutionResult {
        return ReadAction.compute<SymbolResolutionResult, RuntimeException> {
            val phpIndex = PhpIndex.getInstance(project)
            val matches = phpIndex.getClassesByName(name).mapNotNull { it.fqn }
            when {
                matches.size == 1 -> SymbolResolutionResult.Resolved(matches[0])
                matches.isEmpty() -> SymbolResolutionResult.NotFound
                else -> SymbolResolutionResult.Ambiguous(matches)
            }
        }
    }

    private fun classExists(fqcn: String, phpIndex: PhpIndex): Boolean {
        return phpIndex.getClassesByFQN(fqcn).isNotEmpty()
            || phpIndex.getInterfacesByFQN(fqcn).isNotEmpty()
            || phpIndex.getTraitsByFQN(fqcn).isNotEmpty()
    }

    fun findUsages(fqcn: String): List<UsageInfo> {
        return ReadAction.compute<List<UsageInfo>, RuntimeException> {
            if (fqcn.contains("::")) findMemberUsages(fqcn)
            else findClassUsages(fqcn)
        }
    }

    private fun findClassUsages(fqcn: String): List<UsageInfo> {
        val clean = fqcn.trimStart('\\')
        val phpIndex = PhpIndex.getInstance(project)
        val phpClass = phpIndex.getClassesByFQN(clean).firstOrNull()
            ?: phpIndex.getInterfacesByFQN(clean).firstOrNull()
            ?: phpIndex.getTraitsByFQN(clean).firstOrNull()
            ?: return emptyList()
        return ReferencesSearch.search(phpClass).findAll()
            .mapNotNull { it.toUsageInfo() }
    }

    private fun findMemberUsages(fullSymbol: String): List<UsageInfo> {
        val parts = fullSymbol.split("::", limit = 2)
        val className = parts[0].trimStart('\\')
        val memberName = parts[1].trimStart('$')
        val phpIndex = PhpIndex.getInstance(project)
        val phpClass = phpIndex.getClassesByFQN(className).firstOrNull()
            ?: return emptyList()

        phpClass.methods.firstOrNull { it.name == memberName }?.let { method ->
            return ReferencesSearch.search(method).findAll()
                .mapNotNull { it.toUsageInfo() }
        }
        phpClass.fields.firstOrNull { it.name == memberName }?.let { field ->
            return ReferencesSearch.search(field).findAll()
                .mapNotNull { it.toUsageInfo() }
        }
        return emptyList()
    }

    private fun PsiReference.toUsageInfo(): UsageInfo? {
        val element = element
        val file = element.containingFile ?: return null
        val doc = file.viewProvider.document ?: return null
        val range = element.textRange ?: return null
        val offset = range.startOffset
        val line = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val lineText = doc.charsSequence.substring(lineStart, lineEnd).trim()
        return UsageInfo(
            relativePath = relativePath(file.virtualFile.path),
            line = line + 1,
            column = offset - lineStart + 1,
            lineText = lineText,
        )
    }

    private fun relativePath(absolutePath: String): String {
        val base = project.basePath ?: return absolutePath
        if (absolutePath.startsWith(base)) {
            return absolutePath.removePrefix(base).trimStart('/')
        }
        return absolutePath
    }
}
