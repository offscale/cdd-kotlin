/** Headless Kotlin compiler PSI environment and factory initialization utilities. */
package psi

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Singleton infrastructure helper to initialize the Kotlin compiler environment. Handles lifecycle
 * to ensure tests can run independently without killing the shared Application.
 */
@OptIn(org.jetbrains.kotlin.K1Deprecation::class)
object PsiInfrastructure {

  private data class EnvHolder(val disposable: Disposable, val environment: KotlinCoreEnvironment)

  private var holder: EnvHolder? = null

  /**
   * Retrieves or initializes the Project instance. Automatically re-initializes if the underlying
   * environment was disposed.
   */
  val project: Project
    get() {
      synchronized(this) {
        val current = holder ?: initialize()
        return current.environment.project
      }
    }

  private fun initialize(): EnvHolder {
    val disposable = Disposer.newDisposable("PsiInfrastructure")
    val configuration = CompilerConfiguration()
    configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

    // Fix for Windows/Linux native filesystem access in simple environments
    System.setProperty("idea.use.native.fs.for.win", "false")

    val env =
        KotlinCoreEnvironment.createForProduction(
            disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

    val created = EnvHolder(disposable, env)
    holder = created
    return created
  }

  /**
   * Creates a new instance of [KtPsiFactory].
   *
   * @return A [KtPsiFactory] bound to [project].
   */
  fun createPsiFactory(): KtPsiFactory {
    return KtPsiFactory(project)
  }

  /** Helper to manually dispose if needed. */
  fun dispose() {
    synchronized(this) {
      val current = holder
      if (current != null) {
        Disposer.dispose(current.disposable)
        holder = null
      }
    }
  }
}
