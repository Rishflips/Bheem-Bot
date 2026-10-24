package app.bheem.eval.speech

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoonshineModelTest {
    @Test
    fun smallStreamingEnglishIsArchitectureFourWithEightFiles() {
        assertEquals(4, MoonshineModel.ARCH)
        assertEquals(
            listOf(
                "adapter.ort", "cross_kv.ort", "decoder_kv.ort", "encoder.ort",
                "frontend.model.ort", "frontend.weights.ort", "streaming_config.json", "tokenizer.bin",
            ),
            MoonshineModel.files,
        )
    }

    @Test
    fun theFilesComeFromTheProjectDownloadServer() {
        assertEquals(
            "https://download.moonshine.ai/model/small-streaming-en/quantized_26_08_21/encoder.ort",
            MoonshineModel.url("encoder.ort"),
        )
    }

    @Test
    fun theFolderOnThePhoneIsNamedAfterTheModel() {
        assertEquals("moonshine-small-streaming-en", MoonshineModel.FOLDER)
    }

    @Test
    fun missingListsFilesThatAreAbsentOrEmpty() {
        val dir = createTempDirectory("moonshine")
        assertEquals(MoonshineModel.files, MoonshineModel.missing(dir))
        for (f in MoonshineModel.files) Files.write(dir.resolve(f), byteArrayOf(1))
        assertTrue(MoonshineModel.missing(dir).isEmpty())
        Files.write(dir.resolve("tokenizer.bin"), ByteArray(0))
        Files.delete(dir.resolve("encoder.ort"))
        assertEquals(listOf("encoder.ort", "tokenizer.bin"), MoonshineModel.missing(dir))
    }

    @Test
    fun aFolderThatDoesNotExistIsMissingEverything() {
        assertEquals(MoonshineModel.files, MoonshineModel.missing(createTempDirectory("none").resolve("absent")))
    }
}
