package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
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

            val originalText = doc.text
            val classes = PsiTreeUtil.findChildrenOfType(file, PhpClass::class.java)

            if (classes.isEmpty()) {
                return@compute InspectFileResult(contractText = originalText)
            }

            val operations = mutableListOf<TextOp>()
            for (phpClass in classes) {
                collectOperations(phpClass, file, operations)
            }

            InspectFileResult(contractText = applyOperations(originalText, operations))
        }
    }

    private fun collectOperations(phpClass: PhpClass, file: PsiFile, ops: MutableList<TextOp>) {
        for (method in phpClass.methods) {
            if (method.containingFile != file) continue
            if (method.modifier.isPublic) {
                val body = findMethodBody(method)
                if (body != null) {
                    ops.add(TextOp(body.textRange, ";"))
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
        val base = project.basePath ?: return null
        val ioFile = File(base, relativePath)
        if (!ioFile.exists()) return null
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile) ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }
}

private data class TextOp(
    val range: TextRange,
    val replacement: String,
)
