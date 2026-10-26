package app.bheem.eval.redteam

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the source of every module that ships in the APK and checks every `Log.*` call. A log message must be a string literal, and the only values it may
 * interpolate are the numbers, counts and class names listed here. Adding a value to a message, or a message that is not a
 * literal, fails this test until a person adds it to the list on purpose.
 */
class LogHygieneTest {
    private val root: Path = Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it.parent else it }

    private val allowedValues = setOf(
        "millis", "audioMillis", "reason",
        "e::class.java.simpleName",
        "report.rearmed.size", "report.missed.size", "report.failed.size", "System.currentTimeMillis() - started",
    )

    /** Tags whose message is a variable on purpose, with why it is safe. */
    private val variableMessages = mapOf(
        "BheemDownload" to "the downloader trace names host, path, range and status, never a query string; debug builds only",
    )

    private val call = Regex("""Log\.[vdiwe]\(((?:[^()]|\([^()]*\))*)\)""")
    private val interpolation = Regex("""\$\{([^}]*)}|\$([A-Za-z_][A-Za-z0-9_]*)""")
    private val banned = listOf("println(", "printStackTrace(", "Log.getStackTraceString", "Timber.", "Logger.")

    private val shipped = listOf("app/src", "agent-core/src/main", "models/src/main", "storage/src/main", "downloader/src/main", "models-litertlm/src/main")

    private fun sources(): List<Path> = shipped.flatMap { dir ->
        Files.walk(root.resolve(dir)).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
    }

    private fun callsIn(path: Path): List<String> = call.findAll(Files.readString(path)).map { it.groupValues[1] }.toList()

    @Test
    fun theScanFindsTheLogCallsThatExistToday() {
        val all = sources().flatMap { callsIn(it) }
        assertTrue(all.size >= 4, "found only ${all.size} Log calls; the scan may be pointing at the wrong folder")
    }

    @Test
    fun everyLogMessageIsAStringLiteralThatInterpolatesOnlyListedNumbersAndClassNames() {
        val problems = mutableListOf<String>()
        for (file in sources()) {
            for (args in callsIn(file)) {
                val tag = args.substringBefore(",").trim().trim('"')
                val message = args.substringAfter(",", "").trim()
                if (!message.startsWith("\"")) {
                    if (tag !in variableMessages) problems += "${file.fileName}: Log message for tag $tag is not a string literal: $message"
                    continue
                }
                for (m in interpolation.findAll(message)) {
                    val value = m.groupValues[1].ifEmpty { m.groupValues[2] }
                    if (value !in allowedValues) problems += "${file.fileName}: Log message for tag $tag interpolates '$value', which is not on the allowed list"
                }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun nothingPrintsOrDumpsAStackTraceAnywhereInTheApp() {
        val problems = sources().flatMap { file ->
            val text = Files.readString(file)
            banned.filter { it in text }.map { "${file.fileName} uses $it" }
        }
        assertEquals(emptyList(), problems)
    }
}
