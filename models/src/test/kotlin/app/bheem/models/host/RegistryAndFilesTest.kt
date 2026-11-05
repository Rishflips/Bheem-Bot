package app.bheem.models.host

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RegistryAndFilesTest {
    @Test
    fun theRegistryParsesModelsWithPinnedFiles() {
        val json = """{"models":[{"id":"m","role":"llm","runtime":"litert-lm","license":"Apache-2.0","minRamGb":6,"profile":{},
            "files":[{"name":"m.litertlm","sha256":"${"c".repeat(64)}","bytes":42}]}]}"""
        val model = ModelRegistry.parse(json).single()
        assertEquals("m", model.id)
        assertEquals(42L, model.files.single().bytes)
        assertEquals(6, model.minRamGb)
    }

    @Test
    fun aFileMayCarryAnHttpsDownloadUrlAndNothingElseIsAccepted() {
        fun registry(url: String) = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"profile":{},
            "files":[{"name":"f","sha256":"${"c".repeat(64)}","bytes":1,"url":"$url"}]}]}"""
        assertEquals("https://example.org/f", ModelRegistry.parse(registry("https://example.org/f")).single().files.single().url)
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry("http://example.org/f")) }
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry("file:///sdcard/f")) }
    }

    @Test
    fun aFileWithoutAUrlHasNone() {
        val json = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"profile":{},
            "files":[{"name":"f","sha256":"${"c".repeat(64)}","bytes":1}]}]}"""
        assertNull(ModelRegistry.parse(json).single().files.single().url)
    }

    @Test
    fun aMalformedHashOrAnEmptyFileListIsRejected() {
        val bad = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"files":[{"name":"f","sha256":"abc","bytes":1}]}]}"""
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(bad) }
        val none = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"files":[]}]}"""
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(none) }
    }

    @Test
    fun theShippedRegistryPinsTheGemmaFileUsedForEveryPhase0Run() {
        val asset = File("../app/src/main/assets/models.json")
        val gemma = ModelRegistry.parse(asset.readText()).single { it.id == "gemma-4-e2b" }
        val file = gemma.files.single()
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", file.sha256)
        assertEquals(2588147712L, file.bytes)
        assertEquals("gemma-4-E2B-it.litertlm", file.name)
        assertEquals("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm", file.url)
        assertTrue(gemma.license.isNotBlank())
    }

    @Test
    fun theStreamingHashMatchesAKnownDigest() = runBlocking<Unit> {
        val file = Files.createTempFile("hash", ".bin").toFile().apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256FileVerifier.sha256(file.path))
        file.delete()
    }

    @Test
    fun theStateStoreKeepsWhatWasVerifiedAndWhichBackendFailed() {
        val file = Files.createTempFile("state", ".json").toFile().apply { delete() }
        val store = JsonFileHostStateStore(file)
        assertNull(store.verified())
        store.saveVerified(VerifiedFile("/m", 100, 5, "a".repeat(64), 7))
        store.saveGpuFailed("0.18.1")
        store.saveLastLoadMillis(777)
        val reloaded = JsonFileHostStateStore(file)
        assertEquals(VerifiedFile("/m", 100, 5, "a".repeat(64), 7), reloaded.verified())
        assertEquals("0.18.1", reloaded.gpuFailedVersion())
        assertEquals(777L, reloaded.lastLoadMillis())
        reloaded.saveGpuFailed(null)
        assertNull(JsonFileHostStateStore(file).gpuFailedVersion())
        file.delete()
    }

    @Test
    fun aCorruptStateFileIsTreatedAsEmptyNotAsACrash() {
        val file = Files.createTempFile("state", ".json").toFile().apply { writeText("{not json") }
        val store = JsonFileHostStateStore(file)
        assertNull(store.verified())
        assertNull(store.gpuFailedVersion())
        file.delete()
    }

    private fun registry(vararg models: String) = """{"models":[${models.joinToString(",")}]}"""

    private fun model(id: String, role: String = "llm", profile: String = "") = """{"id":"$id","role":"$role","runtime":"x","license":"y","minRamGb":1$profile,
        "files":[{"name":"f","sha256":"${"c".repeat(64)}","bytes":1}]}"""

    @Test
    fun aSpeechModelWithoutAProfileGetsTheDefaultProfile() {
        val profile = ModelRegistry.parse(registry(model("m", role = "stt"))).single().profile
        assertEquals(ModelProfile(), profile)
        assertEquals("line", profile.answerFormat)
        assertEquals(setOf(app.bheem.models.Capability.TEXT), profile.capabilities)
    }

    @Test
    fun aProfileIsReadFromTheRegistryAsData() {
        val json = registry(
            model(
                "m",
                profile = ""","profile":{"answerFormat":"line","promptId":"planner-compact","nowSuffix":" (IST)","capabilities":["TEXT","AUDIO_IN"],"thinking":"OFF"}""",
            ),
        )
        val profile = ModelRegistry.parse(json).single().profile
        assertEquals("planner-compact", profile.promptId)
        assertEquals(" (IST)", profile.nowSuffix)
        assertEquals(setOf(app.bheem.models.Capability.TEXT, app.bheem.models.Capability.AUDIO_IN), profile.capabilities)
    }

    @Test
    fun anUnknownCapabilityOrAProfileWithoutTextIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ModelRegistry.parse(registry(model("m", profile = ""","profile":{"capabilities":["TEXT","TELEPATHY"]}""")))
        }
        assertFailsWith<IllegalArgumentException> {
            ModelRegistry.parse(registry(model("m", profile = ""","profile":{"capabilities":["AUDIO_IN"]}""")))
        }
        assertFailsWith<IllegalArgumentException> {
            ModelRegistry.parse(registry(model("m", profile = ""","profile":{"promptId":""}""")))
        }
    }

    @Test
    fun theShippedRegistryDeclaresTheAudioCapabilityOfTheWiredModel() {
        val text = java.io.File("../app/src/main/assets/models.json").readText()
        val gemma = ModelRegistry.parse(text).single { it.id == "gemma-4-e2b" }
        assertTrue(app.bheem.models.Capability.AUDIO_IN in gemma.profile.capabilities)
        assertEquals(" (IST)", gemma.profile.nowSuffix)
    }

    @Test
    fun aMisspelledProfileKeyIsAnErrorNotASilentDefault() {
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry(model("m", profile = ""","profile":{"capabilites":["TEXT","AUDIO_IN"]}"""))) }
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry(model("m", profile = ""","profile":{"nowSufix":" (IST)"}"""))) }
    }

    @Test
    fun anLlmEntryMustCarryItsOwnProfileSoNobodyInheritsAnUnmeasuredDefault() {
        val noProfile = """{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"files":[{"name":"f","sha256":"${"c".repeat(64)}","bytes":1}]}"""
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry(noProfile)) }
        ModelRegistry.parse(registry(model("s", role = "stt")))
    }

    @Test
    fun duplicateIdsAndUnknownRolesAreRejected() {
        val p = ""","profile":{}"""
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry(model("a", profile = p), model("a", profile = p))) }
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry(model("a", role = "oracle", profile = p))) }
    }

    @Test
    fun theSelectionUsesTheNamedModelOrTheOneMarkedDefaultAndNeverGuesses() {
        val p = ""","profile":{}"""
        val models = ModelRegistry.parse(registry(model("a", profile = p), model("b", profile = p + ""","default":true"""), model("s", role = "stt")))
        assertEquals("b", ModelSelection.pick(models, "llm", null).id)
        assertEquals("a", ModelSelection.pick(models, "llm", "a").id)
        assertEquals("s", ModelSelection.pick(models, "stt", null).id)
        assertFailsWith<IllegalArgumentException> { ModelSelection.pick(models, "llm", "not-there") }
        assertFailsWith<IllegalArgumentException> { ModelSelection.pick(models, "vision", null) }
        val twoNoDefault = ModelRegistry.parse(registry(model("a", profile = p), model("b", profile = p)))
        assertFailsWith<IllegalArgumentException> { ModelSelection.pick(twoNoDefault, "llm", null) }
        assertEquals("a", ModelSelection.pick(ModelRegistry.parse(registry(model("a", profile = p))), "llm", null).id)
    }

    @Test
    fun theShippedRegistryListsBothModelsAndStillPicksE2bByDefault() {
        val models = ModelRegistry.parse(File("../app/src/main/assets/models.json").readText())
        val llms = models.filter { it.role == "llm" }
        assertEquals(setOf("gemma-4-e2b", "gemma-4-e4b"), llms.map { it.id }.toSet())
        assertEquals("gemma-4-e2b", ModelSelection.pick(models, "llm", null).id)
        val e2b = llms.single { it.id == "gemma-4-e2b" }
        val e4b = llms.single { it.id == "gemma-4-e4b" }
        assertTrue(e2b.isDefault)
        assertFalse(e4b.isDefault)
        assertEquals(e2b.profile, e4b.profile)
        assertEquals("litert-lm", e4b.runtime)
        assertEquals("Apache-2.0", e4b.license)
        assertEquals(8, e4b.minRamGb)
        val file = e4b.files.single()
        assertEquals("gemma-4-E4B-it.litertlm", file.name)
        assertEquals("0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", file.sha256)
        assertEquals(3659530240L, file.bytes)
        assertEquals("https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm", file.url)
    }

    @Test
    fun aStoredModelIdSelectsThatModelAndAnUnknownOneFallsBackToTheDefault() {
        val models = ModelRegistry.parse(File("../app/src/main/assets/models.json").readText())
        val picked = ModelSelection.choose(models, "llm", "gemma-4-e4b")
        assertEquals("gemma-4-e4b", picked.model.id)
        assertFalse(picked.fellBack)
        val unknown = ModelSelection.choose(models, "llm", "not-in-the-registry")
        assertEquals("gemma-4-e2b", unknown.model.id)
        assertTrue(unknown.fellBack)
        val absent = ModelSelection.choose(models, "llm", null)
        assertEquals("gemma-4-e2b", absent.model.id)
        assertFalse(absent.fellBack)
    }

    @Test
    fun eachSelectionKeepsItsOwnFileAndTheOtherModelFileIsTheOneNotSelected() {
        val models = ModelRegistry.parse(File("../app/src/main/assets/models.json").readText())
        val e2b = ModelSelection.pick(models, "llm", "gemma-4-e2b")
        val e4b = ModelSelection.pick(models, "llm", "gemma-4-e4b")
        val root = File("/models-root")
        assertEquals(File("/models-root/models/gemma-4-E2B-it.litertlm"), ModelFiles.inModelsFolder(root, e2b))
        assertEquals(File("/models-root/models/gemma-4-E4B-it.litertlm"), ModelFiles.inModelsFolder(root, e4b))
        assertEquals(e4b.files.single().name, ModelFiles.otherFile(root, models, "llm", e2b.id)?.name)
        assertEquals(e2b.files.single().name, ModelFiles.otherFile(root, models, "llm", e4b.id)?.name)
        assertNull(ModelFiles.otherFile(root, listOf(e2b), "llm", e2b.id))
    }

    @Test
    fun removingTheOtherModelLeavesTheSelectedFileAlone() {
        val models = ModelRegistry.parse(File("../app/src/main/assets/models.json").readText())
        val e2b = ModelSelection.pick(models, "llm", "gemma-4-e2b")
        val e4b = ModelSelection.pick(models, "llm", "gemma-4-e4b")
        val root = Files.createTempDirectory("models").toFile()
        val selected = ModelFiles.inModelsFolder(root, e2b).apply { parentFile.mkdirs(); writeText("selected") }
        val other = ModelFiles.inModelsFolder(root, e4b).apply { writeText("other") }
        assertTrue(ModelFiles.removeOther(root, models, "llm", e2b.id))
        assertFalse(other.exists())
        assertTrue(selected.exists())
        root.deleteRecursively()
    }
}
