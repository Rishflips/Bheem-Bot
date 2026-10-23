package app.bheem.eval.data

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Jsonl {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> write(path: Path, items: List<T>) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, items.map { json.encodeToString(it) }, Charsets.UTF_8)
    }

    inline fun <reified T> read(path: Path): List<T> =
        readText(Files.readString(path, Charsets.UTF_8))

    inline fun <reified T> readText(text: String): List<T> =
        text.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.map { json.decodeFromString<T>(it) }.toList()
}
