package com.willfp.libreforge.triggers.triggers

import com.willfp.eco.core.integrations.mcmmo.McmmoManager
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerToggleFlightEvent

class TriggerToggleFlight : Trigger(
    "toggle_flight", listOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION
    )
) {
    @EventHandler(ignoreCancelled = true)
    fun handle(event: PlayerToggleFlightEvent) {
        if (McmmoManager.isFake(event)) {
            return
        }
        val player = event.player

        this.processTrigger(
            player,
            TriggerData(
                player = player,
                location = player.location
            )
        )
    }
}