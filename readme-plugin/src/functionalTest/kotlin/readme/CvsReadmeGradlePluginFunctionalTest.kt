@file:Suppress("FunctionName")

package readme

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests for the 'com.cheroliv.readme' plugin.
 */
class CvsReadmeGradlePluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile    by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    private fun setupProject() {
        settingsFile.writeText("""rootProject.name = "test-project"""")
        buildFile.writeText("""plugins { id("com.cheroliv.readme") }""")
    }

    // YAML valide minimal pour les tests qui pré-créent readme-truth.yml
    private val validConfigYaml = """
        source:
          dir: "."
          defaultLang: "en"
        output:
          imgDir: ".github/workflows/readmes/images"
        git:
          userName: "test-bot"
          userEmail: "test@example.com"
          commitMessage: "chore: test"
          token: "<YOUR_GITHUB_PAT>"
          watchedBranches:
            - "main"
    """.trimIndent()

    @Test
    fun `processReadme succeeds on empty project`() {
        setupProject()

        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("processReadme", "--stacktrace")
            .withProjectDir(projectDir)
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `scaffoldReadme creates readme-truth yml`() {
        setupProject()

        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("scaffoldReadme")
            .withProjectDir(projectDir)
            .build()

        assertTrue(File(projectDir, "readme-truth.yml").exists())
    }

    @Test
    fun `scaffoldReadme creates github actions workflow`() {
        setupProject()

        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("scaffoldReadme")
            .withProjectDir(projectDir)
            .build()

        assertTrue(File(projectDir, ".github/workflows/readme_truth.yml").exists())
    }

    @Test
    fun `scaffoldReadme does not overwrite existing files`() {
        setupProject()

        // ← YAML valide obligatoire — Jackson parse le fichier au chargement du plugin
        val configFile = File(projectDir, "readme-truth.yml")
            .also { it.writeText(validConfigYaml) }
        val workflowDir  = File(projectDir, ".github/workflows").also { it.mkdirs() }
        val workflowFile = File(workflowDir, "readme_truth.yml")
            .also { it.writeText("# existing workflow") }

        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("scaffoldReadme")
            .withProjectDir(projectDir)
            .build()

        // Les fichiers existants ne doivent pas être écrasés
        assertEquals(validConfigYaml,        configFile.readText())
        assertEquals("# existing workflow",  workflowFile.readText())
    }

    @Test
    fun `processReadme generates README from README_truth source file`() {
        setupProject()

        File(projectDir, "README_truth.adoc").writeText("""
            = Test

            Hello world — no diagram blocks here.
        """.trimIndent())

        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("processReadme")
            .withProjectDir(projectDir)
            .build()

        assertTrue(File(projectDir, "README.adoc").exists())
    }
}