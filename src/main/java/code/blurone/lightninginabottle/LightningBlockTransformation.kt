package code.blurone.lightninginabottle

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.entity.Slime
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.weather.LightningStrikeEvent

class LightningBlockTransformation(configMap: Map<String, Any>) : Listener {
    private val map: MutableMap<Material, EntityType> = mutableMapOf()

    init {
        for ((key, value) in configMap) {
            if (value !is String) continue
            map[Material.valueOf(key.uppercase())] = EntityType.valueOf(value)
        }
    }

    @EventHandler
    private fun onLightningStrike(event: LightningStrikeEvent) {
        val blockToCheck = event.lightning.location.block.getRelative(BlockFace.DOWN)

        val entityType = map[blockToCheck.type] ?: return

        val isSized = entityType == EntityType.SLIME || entityType == EntityType.MAGMA_CUBE
        val entity = blockToCheck.world.spawnEntity(blockToCheck.location, entityType, !isSized)
        if (!entity.isValid) return
        blockToCheck.type = Material.AIR

        if (isSized)
            (entity as Slime).size = 2
    }
}