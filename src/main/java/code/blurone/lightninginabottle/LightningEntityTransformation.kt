package code.blurone.lightninginabottle

import org.bukkit.Bukkit
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityTransformEvent

class LightningEntityTransformation(configMap: Map<String, Any>) : Listener {
    private val map: Map<EntityType, EntityType>

    init {
        map = mutableMapOf()
        for ((key, value) in configMap) {
            if (value !is String) continue
            map[EntityType.valueOf(key.uppercase())] = EntityType.valueOf(value.uppercase())
        }
    }

    @EventHandler
    private fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.LIGHTNING) return

        val newEntityType = map[event.entityType] ?: return

        val newEntity = event.entity.world.spawnEntity(event.entity.location, newEntityType, true)
        val transformEvent = EntityTransformEvent(
            event.entity,
            listOf(newEntity),
            EntityTransformEvent.TransformReason.LIGHTNING
        )
        Bukkit.getPluginManager().callEvent(transformEvent)
        (if (event.isCancelled) newEntity else event.entity).remove()
    }
}