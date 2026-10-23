package app.bheem.eval.data

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonlTest {
    private val line = """{"id":"a","lang":"en","text":"t","expected":{"action":"NoAction"}}"""

    @Test
    fun aByteOrderMarkAtTheStartOfTheFileIsIgnored() {
        val file = createTempDirectory("jsonl").resolve("x.jsonl")
        Files.write(file, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + line.toByteArray(Charsets.UTF_8))
        assertEquals("a", Jsonl.read<CommandCase>(file).single().id)
    }

    @Test
    fun blankLinesAndWindowsLineEndingsAreFine() {
        val items = Jsonl.readText<CommandCase>("$line\r\n\r\n$line\r\n")
        assertEquals(2, items.size)
    }
}
