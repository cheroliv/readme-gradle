package readme

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AdocSourceFileTest {

    @Test
    fun `isSourceOfTruth accepte README_truth adoc`() {
        assertTrue(AdocSourceFile.isSourceOfTruth(File("README_truth.adoc")))
    }

    @Test
    fun `isSourceOfTruth accepte README_truth_fr adoc`() {
        assertTrue(AdocSourceFile.isSourceOfTruth(File("README_truth_fr.adoc")))
    }

    @Test
    fun `isSourceOfTruth rejette README adoc`() {
        assertFalse(AdocSourceFile.isSourceOfTruth(File("README.adoc")))
    }

    @Test
    fun `isSourceOfTruth rejette README_fr adoc`() {
        assertFalse(AdocSourceFile.isSourceOfTruth(File("README_fr.adoc")))
    }

    @Test
    fun `lang est null pour README_truth adoc`() {
        assertNull(AdocSourceFile(File("README_truth.adoc")).lang)
    }

    @Test
    fun `lang est fr pour README_truth_fr adoc`() {
        assertEquals("fr", AdocSourceFile(File("README_truth_fr.adoc")).lang)
    }

    @Test
    fun `generatedFileName pour README_truth adoc`() {
        assertEquals("README.adoc", AdocSourceFile(File("README_truth.adoc")).generatedFileName())
    }

    @Test
    fun `generatedFileName pour README_truth_fr adoc`() {
        assertEquals("README_fr.adoc", AdocSourceFile(File("README_truth_fr.adoc")).generatedFileName())
    }

    @Test
    fun `effectiveLang retourne la langue du fichier si presente`() {
        assertEquals("de", AdocSourceFile(File("README_truth_de.adoc")).effectiveLang("en"))
    }

    @Test
    fun `effectiveLang retourne le defaut si pas de langue dans le nom`() {
        assertEquals("en", AdocSourceFile(File("README_truth.adoc")).effectiveLang("en"))
    }

    // ── scanDir ordering ──────────────────────────────────────────────────────

    @Test
    fun `scanDir retourne les fichiers en ordre alphabetique`(@TempDir tempDir: File) {
        // Create files in reverse alphabetical order to expose non-deterministic listFiles()
        File(tempDir, "README_truth_fr.adoc").writeText("= FR")
        File(tempDir, "README_truth_de.adoc").writeText("= DE")
        File(tempDir, "README_truth.adoc").writeText("= EN")

        val names = AdocSourceFile.scanDir(tempDir).map { it.file.name }

        assertEquals(
            listOf("README_truth.adoc", "README_truth_de.adoc", "README_truth_fr.adoc"),
            names
        )
    }

    @Test
    fun `scanDir place README_truth adoc sans suffixe de langue en premier`(@TempDir tempDir: File) {
        // fr and de created before the no-lang variant — filesystem order would put them first
        File(tempDir, "README_truth_fr.adoc").writeText("= FR")
        File(tempDir, "README_truth_de.adoc").writeText("= DE")
        File(tempDir, "README_truth.adoc").writeText("= EN")

        val first = AdocSourceFile.scanDir(tempDir).first().file.name

        assertEquals("README_truth.adoc", first)
    }
}
