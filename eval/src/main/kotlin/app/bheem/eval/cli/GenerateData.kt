package app.bheem.eval.cli

import app.bheem.eval.data.Dataset
import app.bheem.eval.data.DatasetValidator
import app.bheem.eval.data.Jsonl
import app.bheem.eval.gen.AdversarialGenerator
import app.bheem.eval.gen.AssistantCommands
import app.bheem.eval.gen.CommandGenerator
import app.bheem.eval.gen.NotificationGenerator
import java.nio.file.Paths
import kotlin.system.exitProcess

object GenerateData {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = Paths.get(args.firstOrNull() ?: error("usage: GenerateData <outDir>"))
        val dataset = Dataset(CommandGenerator.generate(), NotificationGenerator.generate(), AdversarialGenerator.generate())
        val assistant = AssistantCommands.cases()
        val errors = DatasetValidator.validate(dataset) + DatasetValidator.validate(Dataset(assistant, emptyList(), emptyList()))
        if (errors.isNotEmpty()) {
            errors.forEach { System.err.println(it) }
            exitProcess(1)
        }
        Jsonl.write(out.resolve("commands.jsonl"), dataset.commands)
        Jsonl.write(out.resolve("notifications.jsonl"), dataset.notifications)
        Jsonl.write(out.resolve("adversarial.jsonl"), dataset.adversarial)
        Jsonl.write(out.resolve("assistant.jsonl"), assistant)
        println("wrote ${dataset.commands.size} commands, ${dataset.notifications.size} notifications, ${dataset.adversarial.size} adversarial, ${assistant.size} assistant commands to $out")
    }
}
