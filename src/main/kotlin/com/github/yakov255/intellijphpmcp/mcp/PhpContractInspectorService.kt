package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.PhpClass
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class InspectFileResult(
    val contractText: String? = null,
    val error: String? = null,
)

@Service(Service.Level.PROJECT)
class PhpContractInspectorService(private val project: Project) {

    fun inspectFile(relativePath: String): InspectFileResult {
        return ReadAction.compute<InspectFileResult, RuntimeException> {
            val file = resolveFile(relativePath)
                ?: return@compute InspectFileResult(error = "File not found: $relativePath")

            val doc = file.viewProvider.document
                ?: return@compute InspectFileResult(error = "Cannot get document for: $relativePath")

            InspectFileResult(contractText = generateContract(doc.text, PsiTreeUtil.findChildrenOfType(file, PhpClass::class.java), file))
        }
    }

fun generateContract(originalText: String, classes: Collection<PhpClass>, file: PsiFile): String {
         if (classes.isEmpty()) return originalText
 
         val operations = mutableListOf<TextOp>()
         for (phpClass in classes) {
            collectOperations(phpClass, file, operations, originalText)
         }
 
         return applyOperations(originalText, operations)
     }
 
    private fun collectOperations(phpClass: PhpClass, file: PsiFile, ops: MutableList<TextOp>, text: String) {
         for (method in phpClass.methods) {
             if (method.containingFile != file) continue
             if (method.modifier.isPublic) {
                 val body = findMethodBody(method)
                 if (body != null) {
                    var start = body.textRange.startOffset
                    while (start > 0 && text[start - 1].isWhitespace()) start--
                    ops.add(TextOp(TextRange(start, body.textRange.endOffset), ";"))
                 }
             } else {
                 ops.add(TextOp(method.textRange, ""))
             }
         }
 
         for (field in phpClass.fields) {
             if (field.containingFile != file) continue
             if (!field.modifier.isPublic) {
                val range = field.parent?.textRange ?: field.textRange
                 ops.add(TextOp(range, ""))
             }
         }
     }

    private fun findMethodBody(method: PsiElement): PsiElement? {
        return method.children.find { !it.textRange.isEmpty && it.text.first() == '{' }
            ?: PsiTreeUtil.getChildOfType(method, GroupStatement::class.java)
            ?: PsiTreeUtil.findChildOfType(method, GroupStatement::class.java)
    }

    private fun applyOperations(text: String, ops: List<TextOp>): String {
        val sorted = ops.sortedByDescending { it.range.startOffset }
        val sb = StringBuilder(text)
        for (op in sorted) {
            sb.replace(op.range.startOffset, op.range.endOffset, op.replacement)
        }
        return sb.toString()
    }

    private fun resolveFile(relativePath: String): PsiFile? {
        for (root in ProjectRootManager.getInstance(project).contentSourceRoots) {
            val vf = root.findFileByRelativePath(relativePath)
            if (vf != null) return PsiManager.getInstance(project).findFile(vf)
        }

        val base = project.basePath ?: return null
        val vf = LocalFileSystem.getInstance().findFileByPath("$base/$relativePath")
        return vf?.let { PsiManager.getInstance(project).findFile(it) }
    }
}

private data class TextOp(
    val range: TextRange,
    val replacement: String,
)
