package app.bheem.eval.speech

import java.nio.file.Files
import java.nio.file.Path

object MoonshineModel {
    const val ARCH = 4
    const val FOLDER = "moonshine-small-streaming-en"

    private const val BASE = "https://download.moonshine.ai/model/small-streaming-en/quantized_26_08_21"

    val files = listOf(
        "adapter.ort", "cross_kv.ort", "decoder_kv.ort", "encoder.ort",
        "frontend.model.ort", "frontend.weights.ort", "streaming_config.json", "tokenizer.bin",
    )

    fun url(file: String) = "$BASE/$file"

    fun missing(dir: Path): List<String> = files.filter { name ->
        val path = dir.resolve(name)
        !Files.isRegularFile(path) || Files.size(path) == 0L
    }
}
