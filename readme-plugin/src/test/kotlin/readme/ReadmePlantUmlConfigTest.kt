package readme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReadmePlantUmlConfigTest {

    @Test
    fun `load retourne les valeurs par defaut si fichier absent`(@TempDir tempDir: File) {
        val config = ReadmePlantUmlConfig.load(tempDir)

        assertEquals(".", config.source.dir)
        assertEquals("en", config.source.defaultLang)
        assertEquals(".github/workflows/readmes/images", config.output.imgDir)
        assertEquals("github-actions[bot]", config.git.userName)
        assertEquals(listOf("main"), config.git.watchedBranches)
    }

    @Test
    fun `load mappe correctement un fichier YAML complet`(@TempDir tempDir: File) {
        File(tempDir, "readme-truth.yml").writeText(
            """
            source:
              dir: "docs"
              defaultLang: "fr"
            output:
              imgDir: ".github/workflows/readmes/images"
            git:
              userName: "bot"
              userEmail: "bot@example.com"
              commitMessage: "chore: regen [skip ci]"
              token: "ghp_test_token"
              watchedBranches:
                - "develop"
                - "release"
        """.trimIndent()
        )

        val config = ReadmePlantUmlConfig.load(tempDir)

        assertEquals("docs",            config.source.dir)
        assertEquals("fr",              config.source.defaultLang)
        assertEquals("bot",             config.git.userName)
        assertEquals("bot@example.com", config.git.userEmail)
        assertEquals("ghp_test_token",  config.git.token)
        assertEquals(listOf("develop", "release"), config.git.watchedBranches)
    }

    @Test
    fun `resolvedToken leve une erreur si token est vide`(@TempDir tempDir: File) {
        val config = ReadmePlantUmlConfig.load(tempDir)
        assertThrows(IllegalStateException::class.java) {
            config.git.resolvedToken()
        }
    }

    @Test
    fun `resolvedToken leve une erreur si token est le placeholder`(@TempDir tempDir: File) {
        File(tempDir, "readme-truth.yml").writeText(
            """
            git:
              token: "<YOUR_GITHUB_PAT>"
        """.trimIndent()
        )
        val config = ReadmePlantUmlConfig.load(tempDir)
        assertThrows(IllegalStateException::class.java) {
            config.git.resolvedToken()
        }
    }
}