package app.bheem.eval.run

fun <T> List<T>.evenlySpaced(count: Int): List<T> =
    if (count >= size) this else List(count) { this[it * size / count] }
