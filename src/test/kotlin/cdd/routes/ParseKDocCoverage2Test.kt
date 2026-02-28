package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNull
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import java.io.File
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer

class ParseKDocCoverage2Test {
    @Test
    fun `parseContentType covers more expressions`() {
        val parser = NetworkParser()
        val m = NetworkParser::class.java.getDeclaredMethod("parseContentType", org.jetbrains.kotlin.psi.KtCallExpression::class.java)
        m.isAccessible = true

        val rootDisposable = Disposer.newDisposable()
        val config = CompilerConfiguration()
        val env = KotlinCoreEnvironment.createForProduction(rootDisposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val psiFactory = KtPsiFactory(env.project)

        val c1 = psiFactory.createExpression("contentType(someVar)") as KtCallExpression
        assertNull(m.invoke(parser, c1))
        
        val c2 = psiFactory.createExpression("contentType()") as KtCallExpression
        assertNull(m.invoke(parser, c2))
        
        val c3 = psiFactory.createExpression("contentType(ContentType.Application)") as KtCallExpression
        assertNull(m.invoke(parser, c3)) // only 2 parts
        
        val c4 = psiFactory.createExpression("contentType(\"something\")") as KtCallExpression
        assertNull(m.invoke(parser, c4))
        val c5 = psiFactory.createExpression("contentType(ContentType.Application.Json)") as KtCallExpression
        org.junit.jupiter.api.Assertions.assertEquals("application/json", m.invoke(parser, c5))
        val c6 = psiFactory.createExpression("contentType(ContentType.parse(\"a/b\"))") as KtCallExpression
        org.junit.jupiter.api.Assertions.assertEquals("a/b", m.invoke(parser, c6)) // doesn't start with correctly mapped patterns

        Disposer.dispose(rootDisposable)
    }
}
