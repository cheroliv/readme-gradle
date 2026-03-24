package readme

import org.gradle.api.Plugin
import org.gradle.api.Project

class ReadmePlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val config = ReadmePlantUmlConfig.load(project.projectDir)

        val scaffold = project.tasks.register(
            "scaffoldReadme",
            ScaffoldTask::class.java
        ) { task ->
            task.group       = "documentation"
            task.description = "Crée readme-truth.yml et .github/workflows/readme_truth.yml si absents"
            task.projectDir  .set(project.layout.projectDirectory)
        }

        val processReadme = project.tasks.register(
            "processReadme",
            ProcessReadmeTask::class.java
        ) { task ->
            task.group       = "documentation"
            task.description = "Génère README*.adoc et images depuis les sources README_truth*.adoc"

            task.sourceDir  .set(project.layout.projectDirectory.dir(config.source.dir))
            task.imgDir     .set(project.layout.projectDirectory.dir(config.output.imgDir))
            task.buildImgDir.set(project.layout.buildDirectory.dir("img"))
            task.defaultLang.set(config.source.defaultLang)

            task.dependsOn(scaffold)
        }

        project.tasks.register(
            "commitGeneratedReadme",
            CommitGeneratedReadmeTask::class.java
        ) { task ->
            task.group       = "documentation"
            task.description = "Commite et pousse les README*.adoc générés via JGit (CI only)"

            task.repoDir      .set(project.layout.projectDirectory)
            task.gitUserName  .set(config.git.userName)
            task.gitUserEmail .set(config.git.userEmail)
            task.commitMessage.set(config.git.commitMessage)
            task.gitToken     .set(project.provider { config.git.resolvedToken() })

            task.dependsOn(processReadme)
        }
    }
}