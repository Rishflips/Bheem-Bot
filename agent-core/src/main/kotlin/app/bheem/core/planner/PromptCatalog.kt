package app.bheem.core.planner

/** One planner prompt: an id a model profile can name, the version goldens are recorded against, and the text for a given "now". */
data class PlannerPromptSpec(val id: String, val version: String, val command: (String) -> String)

class PromptCatalog(specs: List<PlannerPromptSpec>) {
    private val byId = specs.associateBy { it.id }

    init {
        require(byId.size == specs.size) { "two planner prompts share an id" }
    }

    fun get(id: String): PlannerPromptSpec = byId[id] ?: throw IllegalArgumentException("unknown planner prompt $id; known: ${byId.keys.sorted()}")

    companion object {
        val Default = PromptCatalog(listOf(PlannerPrompt.SPEC))
    }
}
