package app.bheem.eval.data

import app.bheem.core.action.ActionCatalog

object DatasetValidator {
    fun validate(dataset: Dataset): List<String> {
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun checkId(id: String) {
            if (!seen.add(id)) errors += "duplicate id $id"
        }

        fun checkExpected(id: String, expected: ExpectedAction) {
            val required = ActionCatalog.required[expected.action]
            if (required == null) {
                errors += "$id: unknown action ${expected.action}"
                return
            }
            for (key in required) {
                if (expected.args[key].isNullOrBlank()) errors += "$id missing arg $key"
            }
        }

        for (c in dataset.commands) {
            checkId(c.id)
            if (c.text.isBlank()) errors += "${c.id}: blank text"
            checkExpected(c.id, c.expected)
        }
        for (n in dataset.notifications) {
            checkId(n.id)
            if (n.text.isBlank()) errors += "${n.id}: blank text"
            checkExpected(n.id, n.expected)
        }
        for (a in dataset.adversarial) {
            checkId(a.id)
            if (a.text.isBlank()) errors += "${a.id}: blank text"
        }
        return errors
    }
}
