package dev.callai

import dev.callai.data.Metric

/**
 * Diagnostics' "Last 7 days" lines: how often Kivi answered, how fast it greeted, how drops went and how calls ended.
 * Empty when there is nothing to report yet.
 */
fun weekly(metrics: List<Metric>): List<String> {
    if (metrics.isEmpty()) return emptyList()
    fun count(kind: String) = metrics.count { it.kind == kind }
    val answered = count("answered")
    val missed = count("rang_through")
    val lines = mutableListOf<String>()
    if (answered + missed > 0) {
        val rate = answered * 100 / (answered + missed)
        lines += "$answered answered, $missed rang through because something wasn't ready ($rate% answered)"
    }
    metrics.filter { it.kind == "greeting" }.map { it.value }.sorted().takeIf { it.isNotEmpty() }?.let {
        lines += "Greeting starts %.1f s after answering (median), %.1f s at worst".format(it[it.size / 2], it.last())
    }
    val drops = count("drop")
    if (drops > 0) lines += "$drops line drop${if (drops == 1) "" else "s"}, ${count("resumed")} recovered"
    metrics.filter { it.kind.startsWith("ended:") }.groupingBy { it.kind.removePrefix("ended:") }.eachCount()
        .entries.sortedByDescending { it.value }.takeIf { it.isNotEmpty() }
        ?.let { e -> lines += "Ended: " + e.joinToString(", ") { "${it.value} ${it.key}" } }
    return lines
}
