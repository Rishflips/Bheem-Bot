package app.bheem.storage

import java.io.File

/** Deletes everything under [roots] (the roots themselves stay) and returns the files that could not be deleted. */
class AppDataEraser(
    private val roots: List<File>,
    private val deleteFile: (File) -> Boolean = File::delete,
) {
    fun erase(keep: (File) -> Boolean = { false }): List<File> {
        val failed = mutableListOf<File>()
        for (root in roots) {
            root.listFiles().orEmpty().forEach { remove(it, keep, failed) }
        }
        return failed
    }

    private fun remove(file: File, keep: (File) -> Boolean, failed: MutableList<File>): Boolean {
        if (keep(file)) return false
        if (file.isDirectory) {
            var empty = true
            file.listFiles().orEmpty().forEach { if (!remove(it, keep, failed)) empty = false }
            if (empty && !file.delete()) failed += file
            return empty
        }
        val deleted = deleteFile(file)
        if (!deleted) failed += file
        return deleted
    }
}
