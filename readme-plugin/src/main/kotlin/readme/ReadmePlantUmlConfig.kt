package readme

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * Jackson 2.x model — exact mirror of readme.yml
 *
 * readme.yml is NEVER committed with a real token.
 * Its content (token included) is stored in the GitHub secret
 * README_GRADLE_PLUGIN and written to disk by CI:
 *
 *   echo "${{ secrets.README_GRADLE_PLUGIN }}" > readme.yml
 *   ./gradlew -q -s commitGeneratedReadme
 *
 * TODO: migrate to tools.jackson 3.x when stable release is available
 */
data class ReadmePlantUmlConfig(
    val source: SourceConfig = SourceConfig(),
    val output: OutputConfig = OutputConfig(),
    val git:    GitConfig    = GitConfig()
) {
    companion object {

        private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()

        const val CONFIG_FILE_NAME = "readme.yml"

        fun load(projectDir: File): ReadmePlantUmlConfig {
            val configFile = File(projectDir, CONFIG_FILE_NAME)

            // File absent or empty — fall back to defaults silently
            if (!configFile.exists() || configFile.length() == 0L) {
                return ReadmePlantUmlConfig()
                    .also { println("[readme] No $CONFIG_FILE_NAME or empty file — using defaults") }
            }

            // File present but invalid YAML — warn and fall back to defaults
            return try {
                MAPPER.readValue(configFile, ReadmePlantUmlConfig::class.java)
                    .also { println("[readme] Config loaded: ${configFile.absolutePath}") }
            } catch (e: Exception) {
                println(
                    "[readme] WARNING: $CONFIG_FILE_NAME contains invalid YAML — " +
                            "using defaults (${e.message})"
                )
                ReadmePlantUmlConfig()
            }
        }
    }
}

data class SourceConfig(
    val dir:         String = ".",
    val defaultLang: String = "en"
)

data class OutputConfig(
    val imgDir: String = ".github/workflows/readmes/images"
)

data class GitConfig(
    val userName:        String       = "github-actions[bot]",
    val userEmail:       String       = "github-actions[bot]@users.noreply.github.com",
    val commitMessage:   String       = "chore: generate readme [skip ci]",
    val token:           String       = "",
    val watchedBranches: List<String> = listOf("main", "master")
) {
    fun resolvedToken(): String =
        token.takeIf { it.isNotBlank() && it != "<YOUR_GITHUB_PAT>" }
            ?: error(
                "GitHub token is empty or still a placeholder in readme.yml.\n" +
                        "→ Check the README_GRADLE_PLUGIN secret in :\n" +
                        "   GitHub → Settings → Secrets and variables → Actions"
            )
}
