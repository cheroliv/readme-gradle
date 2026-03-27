@file:Suppress("unused")

package readme.scenarios

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import readme.GitUtils
import readme.ReadmePlantUmlConfig
import java.io.File

class IntegrationSteps(private val world: ReadMeWorld) {

    // Temporary git repo used as the integration project directory
    private var integrationDir: File? = null

    // Branch name pushed to GitHub — deleted in @After cleanup
    private var integrationBranch: String? = null

    // Credentials resolved from readme.yml — reused for push and branch deletion
    private var credentials: UsernamePasswordCredentialsProvider? = null

    // Remote origin URL resolved from the current repo's .git/config
    private var originUrl: String? = null

    // ── Given ─────────────────────────────────────────────────────────────────

    /**
     * Sets up a temporary Gradle project with a real JGit repository
     * and configures origin from the current repo's .git/config.
     *
     * The remote URL is resolved via GitUtils.findGitRoot() + JGit config read —
     * same approach as JGitRemoteValidator.resolveOriginUrl().
     * No URL is hardcoded — works on any fork or mirror automatically.
     *
     * readme.yml must already exist in the current working directory
     * (injected by CI via echo "${{ secrets.README_GRADLE_PLUGIN }}" > readme.yml).
     */
    @Given("a temporary git repository is set up with remote origin")
    fun givenTemporaryRepoWithRemote() {
        val pluginId = "com.cheroliv.readme"

        // Resolve the readme.yml from the current working directory (CI injects it)
        val configFile = File("readme.yml")
        require(configFile.exists()) {
            "readme.yml not found in working directory — " +
            "inject it via: echo \"\${{ secrets.README_GRADLE_PLUGIN }}\" > readme.yml"
        }
        val config = ReadmePlantUmlConfig.load(File("."))

        // Resolve credentials from the loaded config
        val token = config.git.resolvedToken()
        credentials = UsernamePasswordCredentialsProvider("x-access-token", token)

        // Resolve origin URL from current repo's .git — no hardcoded URL
        val gitRoot = GitUtils.findGitRoot(File(".").canonicalFile)
            ?: error("No .git directory found — integration test must run inside the git repo")
        originUrl = Git.open(gitRoot).use { git ->
            git.repository.config.getString("remote", "origin", "url")
                ?.takeIf { it.isNotBlank() }
                ?: error("No 'origin' remote configured in .git/config")
        }

        // Create temporary project directory
        val tmpDir = createTempDir("readme-integration-").also { integrationDir = it }

        // Write minimal Gradle project files
        File(tmpDir, "settings.gradle.kts").writeText(
            "pluginManagement.repositories.gradlePluginPortal()\n" +
            "rootProject.name = \"${tmpDir.name}\""
        )
        File(tmpDir, "build.gradle.kts").writeText(
            "plugins { id(\"$pluginId\") }"
        )

        // Copy readme.yml into the temporary project
        configFile.copyTo(File(tmpDir, "readme.yml"), overwrite = true)

        // Initialize JGit repo + configure remote origin
        Git.init().setDirectory(tmpDir).call().use { git ->
            git.repository.config.apply {
                setString(ConfigConstants.CONFIG_USER_SECTION, null,
                    ConfigConstants.CONFIG_KEY_NAME, config.git.userName)
                setString(ConfigConstants.CONFIG_USER_SECTION, null,
                    ConfigConstants.CONFIG_KEY_EMAIL, config.git.userEmail)
                save()
            }
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish(originUrl))
                .call()

            // .gitignore to keep git status clean
            File(tmpDir, ".gitignore").writeText(".gradle/\nbuild/")

            // Initial commit — establishes HEAD
            git.add().addFilepattern(".").call()
            git.commit().apply {
                message = "chore: integration test initial commit"
                setAuthor(config.git.userName, config.git.userEmail)
                setCommitter(config.git.userName, config.git.userEmail)
            }.call()
        }

        world.projectDir = tmpDir
    }

    /**
     * Creates and checks out a new local branch, then pushes it to origin.
     * The branch is tracked so that commitGeneratedReadme can push to it.
     */
    @Given("the branch {string} is created and checked out")
    fun givenBranchCreatedAndCheckedOut(branch: String) {
        integrationBranch = branch
        val dir = integrationDir ?: error("Integration repo not initialized")

        Git.open(dir).use { git ->
            // Create and checkout the branch
            git.checkout().apply {
                setCreateBranch(true)
                setName(branch)
            }.call()

            // Push the branch to origin so it exists on GitHub
            git.push()
                .setCredentialsProvider(credentials)
                .setRemote("origin")
                .add(branch)
                .call()
        }
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    @Then("the build log should contain {string}")
    fun thenBuildLogContainsString(expected: String) {
        val output = world.buildResult?.output
            ?: error("No build result available — did the task run?")
        assertThat(output)
            .describedAs("Expected build log to contain: $expected")
            .contains(expected)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Cleans up the integration branch from GitHub after the scenario.
     * Runs even if the scenario fails — prevents branch accumulation on GitHub.
     */
    @After("@integration")
    fun cleanupIntegrationBranch() {
        val branch = integrationBranch ?: return
        val dir = integrationDir ?: return
        val creds = credentials ?: return

        runCatching {
            Git.open(dir).use { git ->
                // Delete remote branch: git push origin --delete <branch>
                git.push()
                    .setCredentialsProvider(creds)
                    .setRemote("origin")
                    .setRefSpecs(
                        org.eclipse.jgit.transport.RefSpec(":refs/heads/$branch")
                    )
                    .call()
            }
        }.onFailure { e ->
            println("[integration] WARNING: failed to delete remote branch $branch — ${e.message}")
        }

        integrationDir?.deleteRecursively()
        integrationDir  = null
        integrationBranch = null
        credentials     = null
        originUrl       = null
    }
}

// createTempDir is deprecated in Kotlin — using Java API directly
private fun createTempDir(prefix: String): File =
    File.createTempFile(prefix, "").apply {
        delete()
        mkdirs()
    }
