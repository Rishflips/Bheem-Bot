package app.bheem.eval.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object AtomicFile {
    fun writeText(target: Path, text: String) {
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temp, text, Charsets.UTF_8)
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
