package readme

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * Modèle Jackson 2.x — miroir exact de readme-truth.yml
 *
 * Le fichier readme-truth.yml N'EST PAS commité avec un vrai token.
 * Son contenu (token inclus) est stocké dans le secret GitHub
 * README_GRADLE_PLUGIN et écrit sur disque par la CI :
 *
 *   echo "${{ secrets.README_GRADLE_PLUGIN }}" > readme-truth.yml
 *   ./gradlew -q -s commitGeneratedReadme
 *
 * TODO: migrer vers tools.jackson 3.x quand disponible en release stable
 */
data class ReadmePlantUmlConfig(
    val source: SourceConfig = SourceConfig(),
    val output: OutputConfig = OutputConfig(),
    val git:    GitConfig    = GitConfig()
) {
    companion object {

        private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()

        const val CONFIG_FILE_NAME = "readme-truth.yml"

        fun load(projectDir: File): ReadmePlantUmlConfig {
            val configFile = File(projectDir, CONFIG_FILE_NAME)

            return if (configFile.exists()) {
                MAPPER.readValue(configFile, ReadmePlantUmlConfig::class.java)
                    .also { println("[readme] Config chargée : ${configFile.absolutePath}") }
            } else {
                ReadmePlantUmlConfig()
                    .also { println("[readme] Pas de $CONFIG_FILE_NAME — valeurs par défaut") }
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
    val userName: String = "github-actions[bot]",
    val userEmail: String = "github-actions[bot]@users.noreply.github.com",
    val token: String = "",
    val repoUrl: String = "",
    val watchedBranches: List<String> = listOf("main"),
    val commitMessage: String = "chore: generate readme [skip ci]"
) {
    fun resolvedToken(): String =
        token.takeIf { it.isNotBlank() && it != "<YOUR_GITHUB_PAT>" }
            ?: error(
                "Le token GitHub est vide ou non remplacé dans readme-truth.yml.\n" +
                        "→ Vérifiez le secret README_GRADLE_PLUGIN dans :\n" +
                        "   GitHub → Settings → Secrets and variables → Actions"
            )
}