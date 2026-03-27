@file:Suppress("unused")

package readme.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ConfigConstants
import java.io.File

class CommitSteps(private val world: ReadMeWorld) {

    // ── Given ─────────────────────────────────────────────────────────────────

    /**
     * Initializes a real JGit repository in the project directory.
     * Required by CommitGeneratedReadmeTask which calls Git.open(root).
     *
     * Steps:
     *  1. git init
     *  2. Write .gitignore covering Gradle internals (.gradle/, build/)
     *  3. Stage ALL files currently present in the project directory
     *     (build.gradle.kts, settings.gradle.kts, readme.yml, .github/, etc.)
     *     so they are part of the initial commit and not seen as untracked.
     *  4. Create an initial commit — HEAD exists, git status is clean.
     *
     * This step must run after all project files have been created by the
     * Background (createGradleProject, writeProjectFile, scaffoldReadme side-effects).
     * Staging "." covers everything present at init time.
     */
    @Given("a git repository is initialized")
    fun givenGitRepositoryIsInitialized() {
        val dir = world.projectDir
            ?: error("Project directory must be initialized before git init")

        Git.init().setDirectory(dir).call().use { git ->
            // Configure minimal user identity — required for commits in bare environments
            git.repository.config.apply {
                setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, "test-bot")
                setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, "test-bot@example.com")
                save()
            }

            // Write .gitignore so Gradle build artifacts don't pollute future git status
            File(dir, ".gitignore").writeText(
                """
                .gradle/
                build/
                """.trimIndent()
            )

            // Stage ALL files currently present — covers build.gradle.kts,
            // settings.gradle.kts, readme.yml, .github/, .gitignore, etc.
            // This ensures git status is clean after the initial commit.
            git.add().addFilepattern(".").call()

            // Initial commit — establishes HEAD and leaves the working tree clean
            git.commit().apply {
                message = "chore: initial commit"
                setAuthor("test-bot", "test-bot@example.com")
                setCommitter("test-bot", "test-bot@example.com")
            }.call()
        }
    }

    /**
     * Deletes the .git directory from the project directory.
     * Used by groupe 5 scenarios to simulate a project not under git
     * after the Background has already initialized a repository.
     */
    @Given("the git repository is deleted")
    fun givenGitRepositoryIsDeleted() {
        val dir = world.projectDir
            ?: error("Project directory must be initialized")
        File(dir, ".git").deleteRecursively()
    }

    /**
     * Activates the local-only commit mock in CommitGeneratedReadmeTask.
     * Forwards -Preadme.commit.mock=true to GradleRunner.
     * When active: git add + commit runs locally, push is skipped.
     * No token resolution occurs — safe without a real GitHub PAT.
     */
    @Given("the commit task is mocked")
    fun givenCommitTaskIsMocked() {
        world.commitMock = true
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    /**
     * Asserts that the build log contains the "dépôt propre" message
     * emitted by CommitGeneratedReadmeTask when git status is clean.
     */
    @Then("the build log should mention a clean repository")
    fun thenBuildLogMentionsCleanRepository() {
        val output = world.buildResult?.output
            ?: error("No build result available — did the task run?")
        assertThat(output)
            .describedAs("Expected build log to mention clean repository")
            .containsIgnoringCase("propre")
    }

    /**
     * Asserts that a commit exists with a message containing [expectedMessage].
     * Skips the initial "chore: initial commit" created by givenGitRepositoryIsInitialized().
     */
    @Then("a commit should have been created with message {string}")
    fun thenCommitCreatedWithMessage(expectedMessage: String) {
        val dir = world.projectDir
            ?: error("Project directory must be initialized")

        Git.open(dir).use { git ->
            val commits = runCatching { git.log().all().call().toList() }.getOrElse { emptyList() }
            val matching = commits.filter { it.fullMessage.contains(expectedMessage) }
            assertThat(matching)
                .describedAs("Expected a commit with message containing: $expectedMessage")
                .isNotEmpty()
        }
    }

    /**
     * Asserts that no commit beyond the initial setup commit exists.
     * The initial commit ("chore: initial commit") is created by
     * givenGitRepositoryIsInitialized() and does not count as a generated commit.
     */
    @Then("no commit should have been created")
    fun thenNoCommitCreated() {
        val dir = world.projectDir
            ?: error("Project directory must be initialized")

        Git.open(dir).use { git ->
            val commits = runCatching { git.log().all().call().toList() }.getOrElse { emptyList() }
            val nonSetupCommits = commits.filter { it.fullMessage.trim() != "chore: initial commit" }
            assertThat(nonSetupCommits)
                .describedAs("Expected no README commits — only the initial setup commit should exist")
                .isEmpty()
        }
    }
}
