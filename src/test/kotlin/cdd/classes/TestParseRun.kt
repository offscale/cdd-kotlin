package cdd.classes

import org.junit.jupiter.api.Test
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import cdd.shared.PsiInfrastructure

class TestParseRun {
    @Test
    fun runIt() {
        val factory = PsiInfrastructure.createPsiFactory()
        val text = "sealed interface Shape; data class Circle(val radius: Int) : Shape"
        val file = factory.createFile("Analysis.kt", text)
        val classes = file.collectDescendantsOfType<KtClass>()
        println("FOUND: " + classes.map { it.name + " " + it.isInterface() + " " + it.isSealed() })
    }
}
