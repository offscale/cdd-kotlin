import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import java.io.File
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

setIdeaIoUseFallback()
val disposable = Disposer.newDisposable()
cdd.shared.PsiInfrastructure.project // init
val factory = cdd.shared.PsiInfrastructure.createPsiFactory()
val text = """
sealed interface Shape
data class Circle(val radius: Int) : Shape
"""
val file = factory.createFile("Analysis.kt", text)
val classes = file.collectDescendantsOfType<KtClass>()
println(classes.map { it.name + " " + it.isInterface() + " " + it.isSealed() })
