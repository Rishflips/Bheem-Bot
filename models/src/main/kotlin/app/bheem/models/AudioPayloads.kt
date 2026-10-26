package app.bheem.models

sealed interface AudioPayload {
    data class File(val path: String) : AudioPayload

    class Bytes(val bytes: ByteArray) : AudioPayload
}

object AudioPayloads {
    fun of(clip: AudioClip, read: (String) -> ByteArray = { java.io.File(it).readBytes() }): AudioPayload = if (clip.bytes != null) AudioPayload.Bytes(clip.bytes) else when (clip.form) {
        AudioForm.FILE -> AudioPayload.File(clip.path)
        AudioForm.WAV_BYTES -> AudioPayload.Bytes(read(clip.path))
        AudioForm.PCM_BYTES -> AudioPayload.Bytes(Wav.read(read(clip.path)).requireSpeechFormat().pcm)
    }
}
