package code.blurone.lightninginabottle

import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.block.BrewingStand
import org.bukkit.block.data.Directional
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.LightningStrike
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.AreaEffectCloudApplyEvent
import org.bukkit.event.entity.LingeringPotionSplashEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.inventory.BrewEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.random.Random

class LightningBrewer(config: ConfigurationSection, private val plugin: Plugin) : Listener {
    companion object {
        fun isLightningBottle(item: ItemStack?): Boolean {
            return item?.itemMeta?.persistentDataContainer?.has(potionNamespacedKey) ?: false
        }

        fun createPotion(type: Material): ItemStack {
            return makePotion(ItemStack(type))
        }

        fun makePotion(itemStack: ItemStack): ItemStack {
            val potionMeta = itemStack.itemMeta as PotionMeta
            potionMeta.persistentDataContainer.set(potionNamespacedKey, PersistentDataType.STRING, itemStack.type.name)
            potionMeta.basePotionType = PotionType.UNCRAFTABLE
            if (itemStack.type == Material.POTION)
                potionMeta.addCustomEffect(PotionEffect(PotionEffectType.SPEED, 40, 3, false, true, true), true)
            // TODO: set default display name
            // TODO: set default lore
            potionMeta.color = potionColor
            if (shallGlint)
                potionMeta.addEnchant(Enchantment.CHANNELING, 1, false)
            potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
            potionMeta.setCustomModelData(customModelData)
            itemStack.itemMeta = potionMeta
            return itemStack
        }

        private lateinit var potionNamespacedKey: NamespacedKey
        private var customModelData: Int? = null
        private val potionColor = Color.fromRGB(1985402)
        private lateinit var lingeringLightningMode: LingeringLightningMode
        private var shallGlint = false
    }

    enum class LingeringLightningMode {
        GM4,
        VANILLA,
        CONSTANT
    }

    init {
        potionNamespacedKey = NamespacedKey(plugin, "lightning-potion")
        shallGlint = config.getBoolean("")
        customModelData = config.get("custom-model-data") as? Int?
        lingeringLightningMode = LingeringLightningMode.valueOf(config.getString("lingering-lightning-mode", "GM4")!!.uppercase())
        if (lingeringLightningMode != LingeringLightningMode.GM4)
            Bukkit.getPluginManager().registerEvents(LingeringListener(), plugin)
    }

    @EventHandler
    private fun onLightningStrike(event: LightningStrikeEvent) {
        val lightningRod = event.lightning.location.block.getRelative(BlockFace.DOWN)
        if (lightningRod.type != Material.LIGHTNING_ROD) return
        val brewingStand = lightningRod.getRelative((lightningRod.blockData as Directional).facing.oppositeFace)
        if (brewingStand.type != Material.BREWING_STAND) return
        val state = brewingStand.state as BrewingStand
        if (state.fuelLevel == 0) return
        var actuallyDidSomething = false
        for (item in state.inventory) {
            val potionMeta = item.itemMeta as? PotionMeta ?: continue
            if (potionMeta.basePotionType != PotionType.THICK) continue
            actuallyDidSomething = true
            makePotion(item)
        }
        if (!actuallyDidSomething) return
        --state.fuelLevel
        state.brewingTime = 0
    }

    @EventHandler
    private fun onPotionDrink(event: PlayerItemConsumeEvent) {
        if (isLightningBottle(event.item))
            event.player.world.strikeLightning(event.player.location)
    }

    @EventHandler
    private fun onPotionSplash(event: PotionSplashEvent) {
        if (isLightningBottle(event.potion.item))
            event.entity.world.strikeLightning(event.entity.location)
    }

    @EventHandler
    private fun onLingeringPotionSplash(event: LingeringPotionSplashEvent) {
        if (!isLightningBottle(event.entity.item)) return

        event.areaEffectCloud.persistentDataContainer.set(potionNamespacedKey, PersistentDataType.STRING, event.entity.item.type.name)
        event.areaEffectCloud.addCustomEffect(PotionEffect(PotionEffectType.SPEED, 0, 1), true)
        event.areaEffectCloud.particle = Particle.ELECTRIC_SPARK
        event.areaEffectCloud.color = potionColor

        if (lingeringLightningMode == LingeringLightningMode.GM4) {
            event.entity.world.strikeLightning(event.entity.location)
            LingeringRunnable(event.areaEffectCloud).runTaskTimer(plugin, 80L, 80L)
        }
        else if (lingeringLightningMode == LingeringLightningMode.CONSTANT) {
            val lightning = event.areaEffectCloud.world.strikeLightning(event.areaEffectCloud.location)
            lightning.lifeTicks = event.areaEffectCloud.duration
            lightning.causingPlayer = event.entity.shooter as? Player
            event.areaEffectCloud.persistentDataContainer.set(potionNamespacedKey, PersistentDataType.STRING, lightning.uniqueId.toString())
        }
    }

    @EventHandler
    private fun brewSniffingPotionUpgradeEvent(event: BrewEvent)
    {
        val preMaterial = when (event.contents.ingredient?.type) {
            Material.GUNPOWDER -> Material.POTION
            Material.DRAGON_BREATH -> Material.SPLASH_POTION
            else -> return
        }
        val postMaterial = when (event.contents.ingredient?.type) {
            Material.GUNPOWDER -> Material.SPLASH_POTION
            Material.DRAGON_BREATH -> Material.LINGERING_POTION
            else -> return
        }

        for (slot in 0..event.contents.size)
            if (event.contents.contents[slot].type == preMaterial && isLightningBottle(event.contents.contents[slot]))
                event.results[slot] = createPotion(postMaterial)
    }

    class LingeringListener : Listener {
        @EventHandler
        private fun onAreaEffectCloud(event: AreaEffectCloudApplyEvent) {
            if (!event.entity.persistentDataContainer.has(potionNamespacedKey)) return
            if (lingeringLightningMode == LingeringLightningMode.CONSTANT) {
                val uuid = UUID.fromString(event.entity.persistentDataContainer.get(potionNamespacedKey, PersistentDataType.STRING))
                val lightning = event.entity.world.getEntitiesByClass(LightningStrike::class.java).firstOrNull { it.uniqueId == uuid }
                lightning?.lifeTicks = event.entity.duration
            } else { // VANILLA
                for (entity in event.affectedEntities)
                    entity.world.strikeLightning(entity.location)
            }
        }
    }

    class LingeringRunnable(private val areaEffectCloud: AreaEffectCloud) : BukkitRunnable() {
        override fun run() {
            if (!areaEffectCloud.isValid)
                return this.cancel()

            if (Random.nextBoolean())
                areaEffectCloud.world.strikeLightning(areaEffectCloud.location)
        }
    }
}