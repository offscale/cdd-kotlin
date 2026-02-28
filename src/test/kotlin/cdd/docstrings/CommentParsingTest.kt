package cdd.docstrings

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.junit.jupiter.api.Test

class CommentParsingTest {
    @Test
    fun `see how comments are parsed`() {
        val src = """
            class MyApi {
                // Manual comment 1
                suspend fun getUsers() {}
                
                // Old comment
                suspend fun getOld() {}
            }
        """.trimIndent()
        
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Fragment.kt", src)
        val funcs = file.collectDescendantsOfType<KtNamedFunction>()
        
        val getOld = funcs.find { it.name == "getOld" }!!
        
        println("getOld startOffset: " + getOld.startOffset)
        println("getOld text: '" + getOld.text + "'")
        
        // how to get the true start without the comment?
        println("getOld modifierList startOffset: " + getOld.modifierList?.startOffset)
        println("getOld funKeyword startOffset: " + getOld.funKeyword?.startOffset)
    }
}
