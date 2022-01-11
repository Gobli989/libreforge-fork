@file:JvmName("LibReforgeUtils")

package com.willfp.libreforge

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.Prerequisite
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.integrations.IntegrationLoader
import com.willfp.eco.util.ListUtils
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.conditions.MovementConditionListener
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.integrations.aureliumskills.AureliumSkillsIntegration
import com.willfp.libreforge.integrations.ecoskills.EcoSkillsIntegration
import com.willfp.libreforge.integrations.jobs.JobsIntegration
import com.willfp.libreforge.integrations.mcmmo.McMMOIntegration
import com.willfp.libreforge.integrations.paper.PaperIntegration
import com.willfp.libreforge.triggers.Triggers
import me.clip.placeholderapi.PlaceholderAPI
import org.apache.commons.lang.StringUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Tameable
import redempt.crunch.CompiledExpression
import redempt.crunch.Crunch
import redempt.crunch.functional.EvaluationEnvironment
import java.util.UUID
import java.util.WeakHashMap


private val holderProviders = mutableSetOf<HolderProvider>()
private val previousStates: MutableMap<UUID, Iterable<Holder>> = WeakHashMap()
private val holderCache = mutableMapOf<UUID, Iterable<Holder>>()

typealias HolderProvider = (Player) -> Iterable<Holder>

object LibReforge {
    @JvmStatic
    lateinit var plugin: EcoPlugin

    private val defaultPackage = StringUtils.join(
        arrayOf("com", "willfp", "libreforge"),
        "."
    )

    @JvmStatic
    fun init(plugin: EcoPlugin) {
        this.plugin = plugin

        if (this.javaClass.packageName == defaultPackage) {
            throw IllegalStateException("You must shade and relocate libreforge into your jar!")
        }

        if (Prerequisite.HAS_PAPER.isMet) {
            PaperIntegration.load()
        }
    }

    @JvmStatic
    fun reload(plugin: EcoPlugin) {
        plugin.scheduler.runTimer({
            for (player in Bukkit.getOnlinePlayers()) {
                player.updateEffects()
            }
        }, 30, 30)

        compiledConfigExpressions.clear()
    }

    @JvmStatic
    fun enable(plugin: EcoPlugin) {
        plugin.eventManager.registerListener(TridentHolderDataAttacher(plugin))
        plugin.eventManager.registerListener(MovementConditionListener())
        for (condition in Conditions.values()) {
            plugin.eventManager.registerListener(condition)
        }
        for (effect in Effects.values()) {
            plugin.eventManager.registerListener(effect)
        }
        for (trigger in Triggers.values()) {
            plugin.eventManager.registerListener(trigger)
        }
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun disable(plugin: EcoPlugin) {
        for (player in Bukkit.getOnlinePlayers()) {
            try {
                for (holder in player.getHolders()) {
                    for ((effect) in holder.effects) {
                        effect.disableForPlayer(player)
                    }
                }
            } catch (e: Exception) {
                Bukkit.getLogger().warning("Error disabling effects, not important - do not report this")
            }
        }
    }

    @JvmStatic
    fun getIntegrationLoaders(): List<IntegrationLoader> {
        return listOf(
            IntegrationLoader("EcoSkills", EcoSkillsIntegration::load),
            IntegrationLoader("AureliumSkills", AureliumSkillsIntegration::load),
            IntegrationLoader("mcMMO", McMMOIntegration::load),
            IntegrationLoader("Jobs", JobsIntegration::load),
        )
    }

    @JvmStatic
    fun registerHolderProvider(provider: HolderProvider) {
        holderProviders.add(provider)
    }


    @JvmStatic
    fun registerJavaHolderProvider(provider: java.util.function.Function<Player, Iterable<Holder>>) {
        holderProviders.add(provider::apply)
    }

    @JvmStatic
    fun logViolation(id: String, context: String, violation: ConfigViolation) {
        plugin.logger.warning("")
        plugin.logger.warning("Invalid configuration for $id in context $context:")
        plugin.logger.warning("(Cause) Argument '${violation.param}'")
        plugin.logger.warning("(Reason) ${violation.message}")
        plugin.logger.warning("")
    }
}

private fun Player.clearEffectCache() {
    holderCache.remove(this.uniqueId)
}

fun Player.getHolders(): Iterable<Holder> {
    if (holderCache.containsKey(this.uniqueId)) {
        return holderCache[this.uniqueId]?.toList() ?: emptyList()
    }

    val holders = mutableListOf<Holder>()
    for (provider in holderProviders) {
        holders.addAll(provider(this))
    }

    holderCache[this.uniqueId] = holders
    LibReforge.plugin.scheduler.runLater({
        holderCache.remove(this.uniqueId)
    }, 40)

    return holders
}

fun Player.updateEffects() {
    val before = mutableListOf<Holder>()
    if (previousStates.containsKey(this.uniqueId)) {
        before.addAll(previousStates[this.uniqueId] ?: emptyList())
    }
    this.clearEffectCache()

    val after = this.getHolders()
    previousStates[this.uniqueId] = after

    val beforeFreq = ListUtils.listToFrequencyMap(before)
    val afterFreq = ListUtils.listToFrequencyMap(after.toList())

    val added = mutableListOf<Holder>()
    val removed = mutableListOf<Holder>()

    for ((holder, freq) in afterFreq) {
        var amount = freq
        amount -= beforeFreq[holder] ?: 0
        if (amount < 1) {
            continue
        }

        for (i in 0 until amount) {
            added.add(holder)
        }
    }

    for ((holder, freq) in beforeFreq) {
        var amount = freq

        amount -= afterFreq[holder] ?: 0
        if (amount < 1) {
            continue
        }
        for (i in 0 until amount) {
            removed.add(holder)
        }
    }

    for (holder in added) {
        var areConditionsMet = true
        for ((condition, config) in holder.conditions) {
            if (!condition.isConditionMet(this, config)) {
                areConditionsMet = false
                break
            }
        }

        if (areConditionsMet) {
            for ((effect, config) in holder.effects) {
                effect.enableForPlayer(this, config)
            }
        }
    }

    for (holder in removed) {
        for ((effect, _) in holder.effects) {
            effect.disableForPlayer(this)
        }
    }

    for (holder in after) {
        var areConditionsMet = true
        for ((condition, config) in holder.conditions) {
            if (!condition.isConditionMet(this, config)) {
                areConditionsMet = false
                break
            }
        }
        if (!areConditionsMet) {
            for ((effect, _) in holder.effects) {
                effect.disableForPlayer(this)
            }
        }
    }
}

fun Entity.tryAsPlayer(): Player? {
    return when (this) {
        is Projectile -> this.shooter as? Player
        is Player -> this
        is Tameable -> this.owner as? Player
        else -> null
    }
}

val compiledConfigExpressions = mutableMapOf<Config, MutableMap<String, CompiledExpression>>()

fun Config.getInt(path: String, player: Player?): Int {
    return getDouble(path, player).toInt()
}

fun Config.getDouble(path: String, player: Player?): Double {
    return getDoubleOrNull(path, player) ?: 0.0
}

fun Config.getIntOrNull(path: String, player: Player?): Int? {
    return getDoubleOrNull(path, player)?.toInt()
}

fun Config.getDoubleOrNull(path: String, player: Player?): Double? {
    if (!this.has(path)) {
        return null
    }
    if (player == null) {
        return this.getDoubleOrNull(path)
    }

    val raw = this.getString(path)
    val placeholderValues = raw.getPlaceholders()
        .map { PlaceholderAPI.setPlaceholders(player, it).toDouble() }
    val expression = this.getExpression(path)
    return CrunchHelper.evaluate(expression, placeholderValues)
}

private fun String.getPlaceholders(): List<String> {
    val placeholders = mutableListOf<String>()
    val matcher = PlaceholderAPI.getPlaceholderPattern().matcher(this)
    while (matcher.find()) {
        placeholders.add(matcher.group())
    }

    return placeholders
}

private fun Config.getExpression(path: String): CompiledExpression {
    val cached = compiledConfigExpressions[this]?.get(path)

    if (cached != null) {
        return cached
    }

    val rawString = this.getString(path)

    val placeholders = rawString.getPlaceholders()

    val env = EvaluationEnvironment()
    env.setVariableNames(*placeholders.toTypedArray())

    val expression = Crunch.compileExpression(this.getString(path), env)
    val cache = compiledConfigExpressions[this] ?: mutableMapOf()
    cache[path] = expression
    compiledConfigExpressions[this] = cache
    return expression
}
