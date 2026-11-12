package io.github.michael3dprint.mybambuddy.domain.help

/** The text sent by "Send to my computer": titles and source links only, never a server address, key or anything from this phone. */
object HelpShare {
    /** Text for one [topic]: its title and where its steps came from. */
    fun textFor(topic: HelpTopic): String = buildString {
        appendLine(topic.title)
        topic.sources.forEach { appendLine("${it.title}: ${it.url}") }
    }.trimEnd()

    /** Text for a whole list of [topics]. */
    fun textFor(topics: List<HelpTopic>): String = topics.joinToString("\n\n") { textFor(it) }
}
