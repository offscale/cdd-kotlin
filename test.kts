import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import java.io.File

setIdeaIoUseFallback()
val disposable = Disposer.newDisposable()
psi.PsiInfrastructure.project // init
val factory = psi.PsiInfrastructure.createPsiFactory()
val text = """
package com.example.auto.dto
import kotlinx.serialization.*
@Serializable
data class User(
    val username: String? = null
)
"""
val file = factory.createFile("Fragment.kt", text)
val classes = file.children.toList()
println(classes.map { it.javaClass.name })
val ktClasses = org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType<org.jetbrains.kotlin.psi.KtClass>(file)
println(ktClasses.map { it.name })
