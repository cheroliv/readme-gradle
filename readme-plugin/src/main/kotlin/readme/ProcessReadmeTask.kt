package readme

import net.sourceforge.plantuml.SourceStringReader
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.io.FileOutputStream

@CacheableTask
abstract class ProcessReadmeTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imgDir: DirectoryProperty

    @get:OutputDirectory
    abstract val buildImgDir: DirectoryProperty

    @get:Input
    abstract val defaultLang: Property<String>

    // Matches any AsciiDoc block delimiter of 4+ identical chars: ----, ````, ......
    // Accepts both unquoted and quoted diagram names — quoted names may contain spaces:
    //   [plantuml, architecture, png]    → name = "architecture"
    //   [plantuml, "my diagram", png]    → name = "my diagram"
    // Capture group 2 ensures opening and closing delimiter match exactly.
    private val plantUmlBlockRegex = Regex(
        """\[plantuml,\s*\"?([^,\]"]+)\"?[^\]]*]\s*\n(-{4,}|`{4,}|\.{4,})\n(.*?)\n\2""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Rewrites inter-language links in all three AsciiDoc forms:
    //   href="README_truth_fr.adoc"    → href="README_fr.adoc"
    //   link:README_truth_fr.adoc[...] → link:README_fr.adoc[...]
    //   xref:README_truth_fr.adoc[...] → xref:README_fr.adoc[...]
    private val langLinkRegex = Regex(
        """(href="|link:|xref:)([^"\[]*README_truth(?:_[a-z]{2})?\.adoc)"""
    )

    @TaskAction
    fun process() {
        val root    = sourceDir.get().asFile
        val sources = AdocSourceFile.scanDir(root)

        if (sources.isEmpty()) {
            logger.warn("No README_truth*.adoc file found in: ${root.absolutePath}")
            return
        }

        sources.forEach { processSource(it) }
    }

    private fun processSource(src: AdocSourceFile) {
        // Guard: skip unreadable files instead of crashing — other sources continue.
        // Also checks the internal test mock property -Preadme.process.unreadable.files
        // which simulates unreadable files without relying on filesystem permissions
        // (unreliable when the Gradle daemon runs as root on Linux).
        if (isUnreadableMock(src.file.name) || !src.file.canRead()) {
            logger.warn(
                "[WARN]  ${src.file.name} is not readable — skipping this source file"
            )
            return
        }

        val lang    = src.effectiveLang(defaultLang.get())
        val content = src.file.readText()

        logger.lifecycle("╔═ Processing : ${src.file.name}  [lang=$lang]")

        val buildLangDir = File(buildImgDir.get().asFile, lang).also { it.mkdirs() }
        val repoLangDir  = File(imgDir.get().asFile,      lang).also { it.mkdirs() }

        // Track seen diagram names to detect duplicates within this file
        val seenDiagramNames = mutableSetOf<String>()
        var diagramCount = 0

        // ── Step 1: replace PlantUML blocks ───────────────────────────────────
        val afterPlantuml = plantUmlBlockRegex.replace(content) { match ->
            val name = match.groupValues[1].trim()
            val body = match.groupValues[3]
            diagramCount++

            // Warn on duplicate diagram name within the same source file
            if (!seenDiagramNames.add(name)) {
                logger.warn(
                    "[WARN]  $name — duplicate diagram name in ${src.file.name}, " +
                            "previous PNG will be overwritten"
                )
            }

            val buildPng = File(buildLangDir, "${name}.png")
            val generated = generatePng(body, buildPng, name)
            logger.lifecycle("║  PNG  : ${buildPng.path}")

            if (!generated) {
                // generatePng logged the warning already — skip copy and image:: replacement
                return@replace match.value
            }

            val repoPng = File(repoLangDir, "${name}.png")
            buildPng.copyTo(repoPng, overwrite = true)
            logger.lifecycle("║  COPY : ${repoPng.path}")

            // Compute path relative to the source file using Path.relativize()
            // so it works correctly when the project is nested under the git root.
            val relPath = src.file.parentFile.toPath()
                .relativize(
                    File(imgDir.get().asFile, "$lang/${name}.png").toPath()
                )
                .toString()
                .replace('\\', '/')  // normalize separators on Windows

            "image::${relPath}[${name}]"
        }

        // ── Step 2: rewrite inter-language links ──────────────────────────────
        // README_truth.adoc    → link:README_truth_fr.adoc → link:README_fr.adoc
        // README_truth_fr.adoc → link:README_truth.adoc    → link:README.adoc
        val rewritten = langLinkRegex.replace(afterPlantuml) { match ->
            val prefix       = match.groupValues[1]
            val linkedSource = match.groupValues[2]

            // Warn if the linked truth file does not exist in the project
            val linkedFile = File(src.file.parentFile, linkedSource)
            if (!linkedFile.exists()) {
                logger.warn(
                    "[WARN]  $linkedSource does not exist — " +
                            "link rewritten but target truth file is missing"
                )
            }

            val generatedName = AdocSourceFile(File(linkedSource)).generatedFileName()

            logger.lifecycle("║  LINK : $linkedSource → $generatedName")
            "${prefix}${generatedName}"
        }

        // ── Step 3: write generated file ──────────────────────────────────────
        val generated = src.generatedFile()

        // Guard: skip read-only generated files instead of crashing.
        // Also checks the internal test mock property -Preadme.process.readonly.files
        // which simulates read-only output files without relying on filesystem permissions
        // (unreliable when the Gradle daemon runs as root on Linux).
        if (isReadOnlyMock(generated.name) || (generated.exists() && !generated.canWrite())) {
            logger.warn(
                "[WARN]  ${generated.name} is read-only — skipping write for ${src.file.name}"
            )
            return
        }

        generated.writeText(rewritten)

        logger.lifecycle("║  OUT  : ${generated.name}  ($diagramCount diagram(s) replaced)")
        logger.lifecycle("╚═════════════════════════════════════════")
    }

    /**
     * Returns true if [fileName] is listed in the internal test mock property
     * -Preadme.process.unreadable.files (comma-separated list of file names).
     * Always returns false in production — property is absent.
     */
    private fun isUnreadableMock(fileName: String): Boolean =
        project.findProperty("readme.process.unreadable.files")
            ?.toString()
            ?.split(",")
            ?.any { it.trim() == fileName }
            ?: false

    /**
     * Returns true if [fileName] is listed in the internal test mock property
     * -Preadme.process.readonly.files (comma-separated list of file names).
     * Always returns false in production — property is absent.
     */
    private fun isReadOnlyMock(fileName: String): Boolean =
        project.findProperty("readme.process.readonly.files")
            ?.toString()
            ?.split(",")
            ?.any { it.trim() == fileName }
            ?: false

    /**
     * Generates a PNG from [body] into [output].
     * Returns true on success, false if PlantUML reported an error.
     *
     * PlantUML never throws for syntax errors — it generates an error image instead.
     * We detect failure via two complementary checks:
     *  1. The description returned by outputImage() contains "error" (ignoring case)
     *  2. Fallback: the generated file is suspiciously small (< 100 bytes) —
     *     a valid diagram PNG is always larger; a near-empty or corrupt output signals failure.
     *
     * On failure: logs a WARN and returns false so the caller preserves
     * the original PlantUML block in the generated README instead of
     * replacing it with a broken image:: reference.
     */
    private fun generatePng(body: String, output: File, name: String): Boolean {
        val src = if (body.trim().startsWith("@startuml")) body
        else "@startuml\n$body\n@enduml"

        val reader = SourceStringReader(src)
        val description = FileOutputStream(output).use { fos ->
            reader.outputImage(fos)?.description
        }

        // Check 1: description explicitly reports an error
        val descriptionIndicatesError = description != null &&
                description.contains("error", ignoreCase = true)

        // Check 2: output file is too small to be a valid PNG
        val outputTooSmall = output.length() < 100L

        return if (descriptionIndicatesError || outputTooSmall) {
            logger.warn(
                "[WARN]  $name — invalid PlantUML syntax" +
                        (if (description != null) ": $description" else "") +
                        " — block preserved as-is in generated README"
            )
            output.delete() // remove the corrupt/error PNG
            false
        } else {
            true
        }
    }
}
