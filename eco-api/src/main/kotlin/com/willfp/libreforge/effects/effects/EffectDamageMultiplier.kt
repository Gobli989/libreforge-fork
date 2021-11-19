package com.willfp.libreforge.effects.effects

import com.willfp.eco.core.config.interfaces.JSONConfig
import com.willfp.libreforge.ConfigViolation
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.Triggers
import com.willfp.libreforge.triggers.wrappers.WrappedDamageEvent

class EffectDamageMultiplier : Effect(
    "damage_multiplier",
    supportsFilters = true,
    applicableTriggers = listOf(
        Triggers.TRIDENT_ATTACK,
        Triggers.BOW_ATTACK,
        Triggers.MELEE_ATTACK,
        Triggers.TAKE_DAMAGE,
        Triggers.FALL_DAMAGE
    )
) {
    override fun handle(data: TriggerData, config: JSONConfig) {
        val event = data.event as? WrappedDamageEvent ?: return

        event.damage *= config.getDouble("multiplier")
    }

    override fun validateConfig(config: JSONConfig): List<ConfigViolation> {
        val violations = mutableListOf<ConfigViolation>()

        config.getDoubleOrNull("multiplier")
            ?: violations.add(
                ConfigViolation(
                    "multiplier",
                    "You must specify the damage multiplier!"
                )
            )

        return violations
    }
}