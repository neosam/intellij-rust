package org.rust.ide.annotator

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.util.cargoProject
import org.rust.ide.actions.RustExpandModuleAction
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.impl.mixin.isPathAttributeRequired
import org.rust.lang.core.psi.impl.mixin.pathAttribute
import org.rust.lang.core.psi.util.module

class RustItemsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RustElementVisitor() {
            override fun visitModDeclItem(o: RustModDeclItemElement) = checkModDecl(holder, o)
            override fun visitImplItem(o: RustImplItemElement) = checkImpl(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkModDecl(holder: AnnotationHolder, modDecl: RustModDeclItemElement) {
        if (modDecl.isPathAttributeRequired && modDecl.pathAttribute == null) {
            val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
            holder.createErrorAnnotation(modDecl, message)
            return
        }

        val containingMod = modDecl.containingMod ?: return
        if (!containingMod.ownsDirectory) {
            // We don't want to show the warning if there is no cargo project
            // associated with the current module. Without it we can't know for
            // sure that a mod is not a directory owner.
            if (modDecl.module?.cargoProject != null) {
                holder.createErrorAnnotation(modDecl, "Cannot declare a new module at this location")
                    .registerFix(AddModuleFile(modDecl, expandModuleFirst = true))
            }
            return
        }

        if (modDecl.reference?.resolve() == null) {
            holder.createErrorAnnotation(modDecl, "Unresolved module")
                .registerFix(AddModuleFile(modDecl, expandModuleFirst = false))
        }
    }

    private fun checkImpl(holder: AnnotationHolder, impl: RustImplItemElement) {
        val trait = impl.traitRef?.path?.reference?.resolve() as? RustTraitItemElement ?: return
        val implBody = impl.implBody ?: return
        val implHeaderTextRange = TextRange.create(
            impl.textRange.startOffset,
            impl.type?.textRange?.endOffset ?: implBody.textRange.startOffset
        )

        val needsToImplement = trait.traitBody.traitMethodMemberList.filter { it.isAbstract }.associateBy { it.name }
        val implemented = implBody.implMethodMemberList.associateBy { it.name }

        val notImplemented = needsToImplement.keys - implemented.keys
        if (!notImplemented.isEmpty()) {
            holder.createErrorAnnotation(implHeaderTextRange,
                "Not all trait items implemented, missing: `${notImplemented.first()}`")
        }
    }
}

private class AddModuleFile(
    modDecl: RustModDeclItemElement,
    private val expandModuleFirst: Boolean
) : LocalQuickFixAndIntentionActionOnPsiElement(modDecl) {
    override fun getText(): String = "Create module file"

    override fun getFamilyName(): String = text


    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val modDecl = startElement as RustModDeclItemElement
        if (expandModuleFirst) {
            val containingFile = modDecl.containingFile as RustFile
            RustExpandModuleAction.expandModule(containingFile)
        }
        modDecl.getOrCreateModuleFile()?.let { it.navigate(true) }
    }

}
