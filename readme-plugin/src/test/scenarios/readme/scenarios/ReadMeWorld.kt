package readme.scenarios

import io.cucumber.java.After
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File

class ReadMeWorld {

    val log: Logger = getLogger(ReadMeWorld::class.java)

    val scope = CoroutineScope(Default + SupervisorJob())

    var projectDir:  File?        = null
    var buildResult: BuildResult? = null
    var exception:   Throwable?   = null

    /**
     * Nullable git root — set by ProcessSteps when the project
     * is nested under a fake git root for path resolution tests.
     * Null in all other scenarios.
     */
    var gitRoot: File? = null

    /**
     * Directories made non-writable by givenDirectoryExistsAndIsNotWritable().
     * Must be restored to writable before deleteRecursively() in cleanup(),
     * otherwise the cleanup itself will fail silently on some filesystems.
     */
    val nonWritableDirs: MutableList<File> = mutableListOf()

    /**
     * Files made non-readable by givenFileIsNotReadable().
     * Must be restored to readable before deleteRecursively() in cleanup().
     */
    val nonReadableFiles: MutableList<File> = mutableListOf()

    /**
     * Internal test property value forwarded to GradleRunner via -P flag.
     * Maps directly to -Preadme.git.validator.mock=<value> in Gradle arguments.
     * Null means no mock — JGitRemoteValidator is used.
     */
    var gitValidatorMockResult: String? = null

    /**
     * Comma-separated list of file names to simulate as unreadable.
     * Forwarded as -Preadme.process.unreadable.files=<value> to GradleRunner.
     * Null means no mock — real filesystem canRead() is used.
     * Filesystem-based approaches are not reliable when the Gradle daemon runs as root.
     */
    var unreadableFilesMock: String? = null

    /**
     * Comma-separated list of generated file names to simulate as read-only.
     * Forwarded as -Preadme.process.readonly.files=<value> to GradleRunner.
     * Null means no mock — real filesystem canWrite() is used.
     * Filesystem-based approaches are not reliable when the Gradle daemon runs as root.
     */
    var readonlyFilesMock: String? = null

    private val asyncJobs = mutableListOf<Deferred<BuildResult>>()

    // ── Gradle runner ─────────────────────────────────────────────────────────

    private fun buildArguments(tasks: Array<out String>): List<String> =
        tasks.toList() +
                "--stacktrace" +
                (gitValidatorMockResult
                    ?.let { listOf("-Preadme.git.validator.mock=$it") }
                    ?: emptyList()) +
                (unreadableFilesMock
                    ?.let { listOf("-Preadme.process.unreadable.files=$it") }
                    ?: emptyList()) +
                (readonlyFilesMock
                    ?.let { listOf("-Preadme.process.readonly.files=$it") }
                    ?: emptyList())

    fun executeGradleAsync(vararg tasks: String): Deferred<BuildResult> {
        require(projectDir != null) { "Project directory must be initialized" }
        log.info("Starting async Gradle execution: ${tasks.joinToString(" ")}")
        return scope.async {
            try {
                GradleRunner.create()
                    .withProjectDir(projectDir!!)
                    .withArguments(buildArguments(tasks))
                    .withPluginClasspath()
                    .build()
            } catch (e: Exception) {
                log.error("Gradle build failed", e)
                exception = e
                throw e
            }
        }.also { asyncJobs.add(it) }
    }

    /**
     * Executes a Gradle task, capturing the BuildResult in both success and
     * failure cases. UnexpectedBuildFailure is caught and its BuildResult stored
     * so that scenarios can assert on BUILD FAILED output via thenBuildShouldFail().
     */
    suspend fun executeGradle(vararg tasks: String): BuildResult {
        require(projectDir != null) { "Project directory must be initialized" }
        return try {
            GradleRunner.create()
                .withProjectDir(projectDir!!)
                .withArguments(buildArguments(tasks))
                .withPluginClasspath()
                .build()
                .also { buildResult = it }
        } catch (e: UnexpectedBuildFailure) {
            // Capture the result even on build failure —
            // lets scenarios assert on BUILD FAILED output
            e.buildResult
                .also { buildResult = it }
        } catch (e: Exception) {
            log.error("Unexpected exception during Gradle execution", e)
            exception = e
            throw e
        }
    }

    suspend fun <T> withTimeout(seconds: Long, block: suspend () -> T): T =
        withTimeout(seconds * 1000) { block() }

    suspend fun awaitAll() {
        if (asyncJobs.isNotEmpty()) {
            log.info("Waiting for ${asyncJobs.size} async operations...")
            asyncJobs.awaitAll()
            log.info("All async operations completed")
        }
    }

    // ── Project setup ─────────────────────────────────────────────────────────

    fun createGradleProject(): File {
        val pluginId = "com.cheroliv.readme"
        return createTempDir("gradle-test-").also { dir ->
            dir.resolve("settings.gradle.kts").writeText(
                "pluginManagement.repositories.gradlePluginPortal()\n" +
                        "rootProject.name = \"${dir.name}\""
            )
            dir.resolve("build.gradle.kts").writeText(
                "plugins { id(\"$pluginId\") }"
            )
            projectDir = dir
        }
    }

    fun writeProjectFile(relativePath: String, content: String) {
        require(projectDir != null) { "Project directory must be initialized" }
        File(projectDir, relativePath).also {
            it.parentFile?.mkdirs()
            it.writeText(content)
        }
    }

    fun readProjectFile(relativePath: String): String {
        require(projectDir != null) { "Project directory must be initialized" }
        return File(projectDir!!, relativePath).readText()
    }

    fun projectFileExists(relativePath: String): Boolean {
        require(projectDir != null) { "Project directory must be initialized" }
        return File(projectDir!!, relativePath).exists()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @After
    fun cleanup() {
        scope.cancel()
        // Restore writable permission before deleteRecursively() —
        // non-writable dirs would cause silent cleanup failures.
        nonWritableDirs.forEach { it.setWritable(true) }
        nonWritableDirs.clear()
        // nonReadableFiles is kept for potential future filesystem-based tests
        nonReadableFiles.forEach { it.setReadable(true) }
        nonReadableFiles.clear()
        // Remove the fake .git created by givenProjectNestedUnderGitRoot().
        // It lives one level above projectDir in the temp filesystem —
        // must be cleaned up explicitly to avoid polluting subsequent scenarios.
        gitRoot?.let { File(it, ".git").deleteRecursively() }
        projectDir?.deleteRecursively()
        projectDir             = null
        buildResult            = null
        exception              = null
        gitRoot                = null
        gitValidatorMockResult = null
        unreadableFilesMock    = null
        readonlyFilesMock      = null
        asyncJobs.clear()
    }
}

// createTempDir is deprecated in Kotlin — using Java API directly
private fun createTempDir(prefix: String): File =
    File.createTempFile(prefix, "").apply {
        delete()
        mkdirs()
    }
