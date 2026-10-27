package app.bheem.eval.redteam

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the source of every module that ships in the APK (not the tests) and checks every `Log.*` call. A message must be exactly
 * one string literal, the call must have exactly a tag and a message, and the only values a message may interpolate are the numbers,
 * counts and class names listed here for that file. A change that breaks a rule fails until a person edits the list on purpose.
 * The checker is also run on bad snippets below, so it cannot silently stop seeing a shape.
 */
class LogHygieneTest {
    private val root: Path = Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it.parent else it }

    private val allowedValues = mapOf(
        "AppController.kt" to setOf("millis", "audioMillis"),
        "ReminderReceiver.kt" to setOf("e::class.java.simpleName"),
        "ReminderSupport.kt" to setOf("reason", "report.rearmed.size", "report.missed.size", "report.failed.size", "System.currentTimeMillis() - started"),
    )

    /** (file, tag) pairs whose message is a variable on purpose, with why it is safe. */
    private val variableMessages = mapOf(
        ("Distribution.kt" to "BheemDownload") to "the downloader trace names host, path, range and status, never a query string; debug builds only",
    )

    private val shipped = listOf(
        "app/src/main", "app/src/full", "app/src/offline", "app/src/debug", "app/src/release",
        "agent-core/src/main", "models/src/main", "storage/src/main", "downloader/src/main", "models-litertlm/src/main",
    )
    private val banned = listOf("println(", "printStackTrace(", "Log.getStackTraceString", "Timber.", "java.util.logging")
    private val logToken = Regex("""(?<![A-Za-z0-9_.])Log\s*(?:\.|::)""")
    private val logCall = Regex("""(?<![A-Za-z0-9_.])Log\s*\.\s*(\w+)\s*\(""")

    private fun sources(): List<Path> = shipped.map { root.resolve(it) }.filter { Files.isDirectory(it) }.flatMap { dir ->
        Files.walk(dir).use { s -> s.filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") }.toList() }
    }

    /** Index just past the closing quote of the string literal that starts at [start], following `${...}` templates. */
    private fun literalEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == '"' -> return i + 1
                text.startsWith("\${", i) -> {
                    var depth = 1
                    i += 2
                    while (i < text.length && depth > 0) {
                        when (text[i]) {
                            '{' -> depth++
                            '}' -> depth--
                            '"' -> {
                                i = literalEnd(text, i)
                                continue
                            }
                        }
                        i++
                    }
                }
                else -> i++
            }
        }
        return text.length
    }

    /** The top-level arguments of the call whose opening parenthesis is at [open], or null when it never closes. */
    private fun arguments(text: String, open: Int): List<String>? {
        val args = mutableListOf<String>()
        var depth = 0
        var from = open + 1
        var i = open + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    i = literalEnd(text, i)
                    continue
                }
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> {
                    if (depth == 0) {
                        args += text.substring(from, i).trim()
                        return args
                    }
                    depth--
                }
                c == ',' && depth == 0 -> {
                    args += text.substring(from, i).trim()
                    from = i + 1
                }
            }
            i++
        }
        return null
    }

    private val interpolation = Regex("""\$\{([^}]*)}|\$([A-Za-z_][A-Za-z0-9_]*)""")

    fun problems(fileName: String, text: String): List<String> {
        val problems = mutableListOf<String>()
        val code = text.lines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("//") }.joinToString("\n")
        if (text.lines().any { it.trimStart().startsWith("import android.util.Log") && " as " in it }) problems += "$fileName: android.util.Log is imported under another name"
        banned.filter { it in code }.forEach { problems += "$fileName uses $it" }
        val tokens = logToken.findAll(code).count()
        val calls = logCall.findAll(code).toList()
        if (tokens != calls.size) problems += "$fileName: ${tokens - calls.size} use(s) of Log that are not a plain call"
        for (m in calls) {
            val level = m.groupValues[1]
            if (level !in setOf("v", "d", "i", "w", "e")) {
                problems += "$fileName: Log.$level is not allowed"
                continue
            }
            val args = arguments(code, m.range.last) ?: run {
                problems += "$fileName: a Log.$level call could not be read"
                null
            } ?: continue
            if (args.size != 2) {
                problems += "$fileName: Log.$level has ${args.size} arguments; only a tag and a message are allowed (no throwable)"
                continue
            }
            val tag = args[0].trim('"')
            val message = args[1]
            if (!message.startsWith("\"")) {
                if ((fileName to tag) !in variableMessages) problems += "$fileName: Log message for tag $tag is not a string literal: $message"
                continue
            }
            if (literalEnd(message, 0) != message.length) {
                problems += "$fileName: Log message for tag $tag is not a single string literal: $message"
                continue
            }
            for (v in interpolation.findAll(message)) {
                val value = v.groupValues[1].ifEmpty { v.groupValues[2] }
                if (value !in allowedValues[fileName].orEmpty()) problems += "$fileName: Log message for tag $tag interpolates '$value', which is not on the list for this file"
            }
        }
        return problems
    }

    @Test
    fun theShippedSourceHasNoLogProblems() {
        val files = sources()
        assertTrue(files.any { it.fileName.toString() == "AppController.kt" }, "the scan is not looking at the app source")
        val found = files.sumOf { logCall.findAll(Files.readString(it)).count() }
        assertTrue(found >= 4, "found only $found Log calls; the scan may be pointing at the wrong folder")
        assertEquals(emptyList(), files.flatMap { problems(it.fileName.toString(), Files.readString(it)) })
    }

    @Test
    fun theCheckerSeesEveryShapeThatCouldCarryUserText() {
        fun bad(code: String, expect: String) = assertTrue(problems("Other.kt", code).any { expect in it }, "not caught: $code")
        bad("""Log.i("T", "heard " + transcript)""", "single string literal")
        bad("""Log.i("T", "heard ${'$'}{transcript}")""", "not on the list")
        bad("""Log.i("T", "heard ${'$'}transcript")""", "not on the list")
        bad("""Log.e("T", "failed", e)""", "3 arguments")
        bad("""Log.i("T", "x ${'$'}{f(g(text))}")""", "not on the list")
        bad("""Log.wtf("T", "boom")""", "not allowed")
        bad("""Log.i("T", message)""", "not a string literal")
        bad("import android.util.Log as L\nL.i(\"T\", \"x\")", "another name")
        bad("""println("heard")""", "println(")
        bad("""e.printStackTrace()""", "printStackTrace(")
        bad("""val f = Log::i""", "not a plain call")
        assertEquals(emptyList(), problems("Other.kt", """Log.i("T", "took 5 ms")"""))
        assertEquals(emptyList(), problems("AppController.kt", """Log.i("BheemVoice", "release to result: ${'$'}millis ms, ${'$'}audioMillis ms of audio")"""))
    }
}
