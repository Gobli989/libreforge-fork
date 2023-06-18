package com.willfp.libreforge

import com.willfp.eco.core.config.interfaces.Config

class ConfigArguments internal constructor(
    private val arguments: List<ConfigArgument>
) {
    fun test(config: Config): List<ConfigViolation> {
        return arguments.flatMap { it.test(config) }
    }
}

class ConfigArgumentsBuilder {
    private val arguments = mutableListOf<ConfigArgument>()

    fun require(name: String, message: String) {
        require(listOf(name), message)
    }

    fun <T> require(
        name: String,
        message: String,
        getter: Config.(String) -> T,
        predicate: (T) -> Boolean
    ) {
        require(listOf(name), message, getter, predicate)
    }

    fun require(names: Collection<String>, message: String) {
        require(names, message, { get(it) }) {
            true
        }
    }

    fun <T> require(
        names: Collection<String>,
        message: String,
        getter: Config.(String) -> T,
        predicate: (T) -> Boolean
    ) {
        arguments += RequiredArgument(names, message, getter, predicate)
    }

    fun inherit(getter: (Config) -> Compilable<*>?) {
        arguments += InheritedArguments(getter)
    }

    fun inherit(subsection: String, getter: (Config) -> Compilable<*>?) {
        arguments += InheritedArguments(getter, subsection)
    }

    fun optional(name: String, description: String) {
        optional(listOf(name), description)
    }

    fun optional(names: Collection<String>, description: String) {
        arguments += OptionalArgument(names, description) // Currently does nothing
    }

    internal fun build() = ConfigArguments(arguments)
}

fun arguments(block: ConfigArgumentsBuilder.() -> Unit): ConfigArguments {
    return ConfigArgumentsBuilder().apply(block).build()
}

interface ConfigArgument {
    /**
     * Null if valid.
     */
    fun test(config: Config): List<ConfigViolation>
}

// In the future this could allow for ChatGPT integration
private class OptionalArgument(
    private val names: Collection<String>,
    private val description: String
) : ConfigArgument {
    override fun test(config: Config): List<ConfigViolation> {
        return emptyList() // no violations
    }
}

private class RequiredArgument<T>(
    private val names: Collection<String>,
    private val description: String,
    private val getter: Config.(String) -> T,
    private val predicate: (T) -> Boolean
) : ConfigArgument {
    override fun test(config: Config): List<ConfigViolation> {
        if (names.none { config.has(it) }) {
            return listOf(ConfigViolation(names.first(), "You must specify ${names.first()}: $description"))
        }

        val present = names.first { config.has(it) }

        val value = config.getter(present)

        if (!predicate(value)) {
            return listOf(ConfigViolation(present, "Invalid value for $present: $description"))
        }

        return emptyList()
    }
}

private class InheritedArguments(
    private val getter: (Config) -> Compilable<*>?,
    private val subsection: String? = null
) : ConfigArgument {
    override fun test(config: Config): List<ConfigViolation> {
        val section = subsection?.let { config.getSubsection(it) } ?: config
        val compilable = getter(section)

        return compilable?.arguments?.test(section) ?: emptyList()
    }
}
