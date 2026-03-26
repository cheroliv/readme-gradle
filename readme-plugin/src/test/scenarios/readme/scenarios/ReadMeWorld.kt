package readme.scenarios

import io.cucumber.java.After
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.File.createTempFile

class ReadMeWorld {

    val log: Logger = getLogger(ReadMeWorld::class.java)

    val scope = CoroutineScope(Default + SupervisorJob())

    var projectDir:  File?         = null
    var buildResult: BuildResult?  = null
    var exception:   Throwable?    = null

    /**
     * Internal test property value forwarded to GradleRunner via -P flag.
     * Maps directly to -Preadme.git.validator.mock=<value> in Gradle arguments.
     * Null means no mock — JGitRemoteValidator is used.
     */
    var gitValidatorMockResult: String? = null

    private val asyncJobs = mutableListOf<Deferred<BuildResult>>()

    // ── Gradle runner ─────────────────────────────────────────────────────────

    private fun buildArguments(tasks: Array<out String>): List<String> =
        tasks.toList() +
                "--stacktrace" +
                (gitValidatorMockResult
                    ?.let { listOf("-Preadme.git.validator.mock=$it") }
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

    suspend fun executeGradle(vararg tasks: String): BuildResult =
        try {
            GradleRunner.create()
                .withProjectDir(projectDir!!)
                .withArguments(buildArguments(tasks))
                .withPluginClasspath()
                .build()
                .also { buildResult = it }
        } catch (e: org.gradle.testkit.runner.UnexpectedBuildFailure) {
            // Capture the result even on build failure — lets scenarios assert on it
            e.buildResult
                .also { buildResult = it }
        } catch (e: Exception) {
            log.error("Gradle build failed", e)
            exception = e
            throw e
        }
    /**
     * Executes a Gradle task expecting a build failure.
     * Uses buildAndFail() — does not throw on non-zero exit code.
     */
    suspend fun executeGradleExpectingFailure(vararg tasks: String): BuildResult {
        require(projectDir != null) { "Project directory must be initialized" }
        return try {
            GradleRunner.create()
                .withProjectDir(projectDir!!)
                .withArguments(buildArguments(tasks))
                .withPluginClasspath()
                .buildAndFail()
                .also { buildResult = it }
        } catch (e: Exception) {
            log.error("Unexpected exception during failing build", e)
            exception = e
            throw e
        }
    }

    suspend fun <T> withTimeout(seconds: Long, block: suspend () -> T)
            : T = withTimeout(seconds * 1000) { block() }

    /**
     * Attend la fin de toutes les opérations asynchrones
     */
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
        projectDir?.deleteRecursively()
        projectDir           = null
        buildResult          = null
        exception            = null
        gitValidatorMockResult = null
        asyncJobs.clear()
    }
}

private fun createTempDir(prefix: String): File =
    createTempFile(prefix, "").apply {
        delete()
        mkdirs()
    }