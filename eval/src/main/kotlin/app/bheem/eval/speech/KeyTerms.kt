package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase

enum class KeyTermMode(val tag: String) {
    OFF(""),
    NAMES("+names"),
    DECOYS("+decoys"),
}

object KeyTerms {
    val decoys = listOf(
        "Aarav", "Aditya", "Akash", "Amit", "Ananya", "Anil", "Anjali", "Arjun", "Ashok", "Bharat",
        "Bhavna", "Chetan", "Chitra", "Deepak", "Devika", "Dinesh", "Diya", "Divya", "Geeta", "Gopal",
        "Harish", "Ishaan", "Jaya", "Kavya", "Kiran", "Lakshmi", "Lata", "Madhav", "Mahesh", "Manoj",
        "Mohan", "Nandini", "Naveen", "Neha", "Nikhil", "Nitin", "Pooja", "Prakash", "Pranav", "Rahul",
        "Rajesh", "Ramesh", "Rekha", "Rohan", "Rohit", "Sanjay", "Savita", "Shreya", "Sneha", "Sunil",
        "Sunita", "Swati", "Tanvi", "Uma", "Varun", "Venkat", "Vikram", "Vimala", "Yash", "Zoya",
    )

    fun fromCommands(commands: List<CommandCase>): List<String> {
        val found = linkedMapOf<String, String>()
        for (command in commands) {
            if (command.expected.action == "NoAction") continue
            for (term in namesIn(command)) found.putIfAbsent(term.lowercase(), term)
        }
        return found.values.toList()
    }

    fun forMode(mode: KeyTermMode, commands: List<CommandCase>): List<String> = when (mode) {
        KeyTermMode.OFF -> emptyList()
        KeyTermMode.NAMES -> fromCommands(commands)
        KeyTermMode.DECOYS -> fromCommands(commands) + decoys
    }

    fun withCommandWords(terms: List<String>): List<String> {
        val have = terms.map { it.lowercase() }.toSet()
        return terms + TranscriptionPrompts.commandWordList.filter { it.lowercase() !in have }
    }

    private fun namesIn(command: CommandCase): List<String> {
        val args = command.expected.args
        val named = listOf("payee", "merchant", "to").mapNotNull { args[it]?.trim() }.filter { it.isNotEmpty() }
        val covered = named.flatMap { it.lowercase().split(' ') }.toSet() + listOfNotNull(args["channel"]?.lowercase())
        val capitalized = command.text.split(Regex("""\s+""")).drop(1)
            .map { word -> word.trim { !it.isLetter() }.replace(Regex("""['’]s$"""), "") }
            .filter { EntityExtractor.properNoun.containsMatchIn(it) && it.lowercase() !in EntityExtractor.calendarWords && it.lowercase() !in covered }
        return named + capitalized
    }
}
