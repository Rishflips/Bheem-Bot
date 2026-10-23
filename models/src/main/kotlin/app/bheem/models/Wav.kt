package app.bheem.models

class WavAudio(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val pcm: ByteArray) {
    private val frameSize: Int get() = channels * bitsPerSample / 8

    val frames: Int get() = if (frameSize == 0) 0 else pcm.size / frameSize

    val durationMs: Long get() = if (sampleRate == 0) 0L else frames * 1000L / sampleRate

    fun requireSpeechFormat(): WavAudio {
        require(sampleRate == 16_000 && channels == 1 && bitsPerSample == 16) {
            "expected 16 kHz mono 16-bit PCM but got $sampleRate Hz, $channels channels, $bitsPerSample-bit"
        }
        return this
    }
}

object Wav {
    private const val PCM = 1
    private const val EXTENSIBLE = 0xFFFE
    private const val HEADER = 12
    private const val FORMAT_MIN = 16
    private const val EXTENSIBLE_SUBFORMAT_AT = 24

    fun read(bytes: ByteArray): WavAudio {
        require(bytes.size >= HEADER) { "not a WAV file: only ${bytes.size} bytes" }
        require(ascii(bytes, 0) == "RIFF") { "not a RIFF file" }
        require(ascii(bytes, 8) == "WAVE") { "the RIFF file is not WAVE audio" }
        var format: ByteArray? = null
        var data: ByteArray? = null
        var at = HEADER
        while (at + 8 <= bytes.size) {
            val id = ascii(bytes, at)
            val declared = unsigned32(bytes, at + 4)
            val start = at + 8
            val available = bytes.size - start
            // Streaming recorders leave the data size at 0 or 0xFFFFFFFF; the data then runs to the end of the file.
            val size = if (declared > available || (id == "data" && declared == 0L)) available else declared.toInt()
            when {
                id == "fmt " && format == null -> format = bytes.copyOfRange(start, start + size)
                id == "data" && data == null -> data = bytes.copyOfRange(start, start + size)
            }
            at = start + size + (size and 1)
        }
        val fmt = requireNotNull(format) { "the WAV file has no fmt chunk" }
        val samples = requireNotNull(data) { "the WAV file has no data chunk" }
        require(fmt.size >= FORMAT_MIN) { "the fmt chunk is too short" }
        val tag = unsigned16(fmt, 0)
        val encoding = if (tag == EXTENSIBLE && fmt.size >= EXTENSIBLE_SUBFORMAT_AT + 2) unsigned16(fmt, EXTENSIBLE_SUBFORMAT_AT) else tag
        require(encoding == PCM) { "unsupported WAV encoding $tag, only PCM is supported" }
        val channels = unsigned16(fmt, 2)
        val sampleRate = unsigned32(fmt, 4).toInt()
        val bits = unsigned16(fmt, 14)
        val frameSize = channels * bits / 8
        val whole = if (frameSize == 0) 0 else samples.size / frameSize * frameSize
        return WavAudio(sampleRate, channels, bits, samples.copyOf(whole))
    }

    private fun ascii(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)

    private fun unsigned16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun unsigned32(bytes: ByteArray, at: Int) = unsigned16(bytes, at).toLong() or (unsigned16(bytes, at + 2).toLong() shl 16)
}
