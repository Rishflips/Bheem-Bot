package app.bheem.core.assistant

/** The numbers behind the waiting states: how full the load bar is, when "still on it" shows, and when the dots stand still. */
object Waiting {
    const val STILL_ON_IT_AFTER_MS = 6_000L
    private const val BAR_CAP = 0.95f
    private const val LARGE_TEXT = 1.3f

    /** Null when the usual load time is unknown, which means an indeterminate bar. Never reaches 100 percent before the model is ready. */
    fun loadProgress(elapsedMs: Long, usualSeconds: Long?): Float? =
        if (usualSeconds == null || usualSeconds <= 0) null else (elapsedMs / (usualSeconds * 1000f)).coerceIn(0f, BAR_CAP)

    fun showStillOnIt(elapsedMs: Long): Boolean = elapsedMs >= STILL_ON_IT_AFTER_MS

    fun staticDots(animatorScale: Float, fontScale: Float): Boolean = animatorScale == 0f || fontScale > LARGE_TEXT
}
