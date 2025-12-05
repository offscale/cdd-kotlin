package psi

import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Singleton infrastructure helper to initialize the Kotlin compiler environment.
 * Handles lifecycle to ensure tests can run independently without killing the shared Application.
 */
object PsiInfrastructure {

    private var _disposable: Disposable? = null
    private var _environment: KotlinCoreEnvironment? = null

    // transform library tracking to manual tracking since Disposer.isDisposed is shaded out
    private var _isDisposed: Boolean = true

    /**
     * Retrieves or initializes the Project instance.
     * Automatically re-initializes if the underlying environment was disposed.
     */
    val project: Project
        get() {
            synchronized(this) {
                if (_environment == null || _disposable == null || _isDisposed) {
                    initialize()
                }
                return _environment!!.project
            }
        }

    private fun initialize() {
        val disposable = Disposer.newDisposable("PsiInfrastructure")
        val configuration = CompilerConfiguration()
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        // Fix for Windows/Linux native filesystem access in simple environments
        System.setProperty("idea.use.native.fs.for.win", "false")

        val env = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        _disposable = disposable
        _environment = env
        _isDisposed = false
    }

    /**
     * Creates a new instance of [KtPsiFactory].
     */
    fun createPsiFactory(): KtPsiFactory {
        return KtPsiFactory(project)
    }

    /**
     * Helper to manually dispose if needed.
     */
    fun dispose() {
        synchronized(this) {
            _disposable?.let {
                if (!_isDisposed) {
                    Disposer.dispose(it)
                }
            }
            _disposable = null
            _environment = null
            _isDisposed = true
        }
    }
}