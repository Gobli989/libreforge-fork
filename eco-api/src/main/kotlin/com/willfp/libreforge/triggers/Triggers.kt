package com.willfp.libreforge.triggers

import com.willfp.libreforge.triggers.triggers.*

object Triggers {
    private val BY_ID = mutableMapOf<String, Trigger>()

    val MELEE_ATTACK: Trigger = TriggerMeleeAttack()
    val BOW_ATTACK: Trigger = TriggerBowAttack()
    val TRIDENT_ATTACK: Trigger = TriggerTridentAttack()
    val MINE_BLOCK: Trigger = TriggerMineBlock()
    val JUMP: Trigger = TriggerJump()
    val KILL: Trigger = TriggerKill()
    val PROJECTILE_LAUNCH: Trigger = TriggerProjectileLaunch()
    val TAKE_DAMAGE: Trigger = TriggerTakeDamage()
    val PROJECTILE_HIT: Trigger = TriggerProjectileHit()
    val FALL_DAMAGE: Trigger = TriggerFallDamage()

    fun values(): Set<Trigger> {
        return BY_ID.values.toSet()
    }

    fun getById(id: String): Trigger? {
        return BY_ID[id.lowercase()]
    }

    /**
     * Add new trigger.
     *
     * @param trigger The trigger to add.
     */
    fun addNewTrigger(trigger: Trigger) {
        BY_ID.remove(trigger.id)
        BY_ID[trigger.id] = trigger
    }
}