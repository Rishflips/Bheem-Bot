package app.bheem.core.brief

data class BriefViewSection(val title: String, val rows: List<String>)

data class BriefView(val headline: String, val sections: List<BriefViewSection>, val note: String?)

/** Reads the brief's own text back into a headline and sections, so the screen can draw it as a page instead of a block of lines. */
object BriefLayout {
    fun parse(text: String): BriefView {
        val lines = text.lines().filter { it.isNotBlank() }
        val headline = lines.firstOrNull().orEmpty().trimEnd(':')
        val sections = ArrayList<BriefViewSection>()
        var title: String? = null
        var rows = ArrayList<String>()
        val notes = ArrayList<String>()
        fun close() {
            title?.let { sections += BriefViewSection(it, rows) }
            title = null
            rows = ArrayList()
        }
        for (line in lines.drop(1)) {
            when {
                line.startsWith("- ") -> if (title != null) rows += line.removePrefix("- ") else notes += line
                line.endsWith(":") -> {
                    close()
                    title = line.trimEnd(':')
                }
                else -> notes += line
            }
        }
        close()
        return BriefView(headline, sections, notes.joinToString(" ").ifEmpty { null })
    }
}
