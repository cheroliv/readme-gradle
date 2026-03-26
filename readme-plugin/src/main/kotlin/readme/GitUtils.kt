package readme

import java.io.File

/**
 * Shared git filesystem utilities.
 * Used by JGitRemoteValidator and ReadmePlugin to locate the git root
 * without duplicating the directory walk logic.
 */
object GitUtils {

    /**
     * Walks up the filesystem from [startDir] to find the directory
     * containing the .git folder.
     * Returns null if no .git directory is found in the hierarchy.
     */
    fun findGitRoot(startDir: File): File? {
        var dir = startDir.canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, ".git").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    /**
     * Resolves the absolute path of the .github directory
     * rooted at the git repository root.
     * Falls back to [projectDir] if no git root is found —
     * safe for projects not yet under git.
     */
    fun resolveGitHubDir(projectDir: File): File {
        val gitRoot = findGitRoot(projectDir) ?: projectDir
        return File(gitRoot, ".github")
    }

    /**
     * Resolves the absolute imgDir anchored at the git root.
     *
     * Searches for .git by walking UP from [projectDir] with a bounded depth
     * of [maxLevels] levels (default 3). This covers all legitimate layouts:
     *  - 0 levels: project IS the git root
     *  - 1 level:  project is one subdirectory below git root (monorepo)
     *  - 2 levels: project is two subdirectories below git root
     *
     * The bounded search prevents GradleRunner test isolation issues where
     * findGitRoot() would otherwise walk all the way up the host filesystem
     * and find the developer's own .git repository from a /tmp temp directory.
     *
     * Falls back to [projectDir] if no .git is found within [maxLevels].
     */
    fun resolveImgDir(
        projectDir: File,
        imgDirConfig: String,
        maxLevels: Int = 3
    ): File {
        val canonical = projectDir.canonicalFile
        val gitRoot   = findGitRootBounded(canonical, maxLevels)
        val resolvedRoot = gitRoot ?: canonical
        return File(resolvedRoot, imgDirConfig)
    }

    /**
     * Walks up the filesystem from [startDir] looking for a .git folder,
     * stopping after [maxLevels] levels above [startDir].
     * Returns null if no .git is found within the bounded range.
     */
    fun findGitRootBounded(startDir: File, maxLevels: Int): File? {
        var dir   = startDir.canonicalFile
        var level = 0
        while (level <= maxLevels && dir.parentFile != null) {
            if (File(dir, ".git").exists()) return dir
            dir = dir.parentFile
            level++
        }
        return null
    }
}