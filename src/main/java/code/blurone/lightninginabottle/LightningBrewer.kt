package code.blurone.lightninginabottle

import code.blurone.lightninginabottle.OcdTranslator.Companion.DEFAULT_LINGERING_NAME
import code.blurone.lightninginabottle.OcdTranslator.Companion.DEFAULT_POTION_NAME
import code.blurone.lightninginabottle.OcdTranslator.Companion.DEFAULT_SPLASH_NAME
import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.block.BrewingStand
import org.bukkit.block.data.Directional
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AreaEffectCloud
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
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.random.Random

class LightningBrewer(config: ConfigurationSection, private val plugin: Plugin) : Listener {
    companion object {
        fun isLightningBottle(item: ItemStack?): Boolean {
            return item?.itemMeta?.persistentDataContainer?.has(potionNamespacedKey) ?: false
        }

        fun createPotion(type: Material): ItemStack {
            return makePotion(ItemStack(type))
        }

        private var loreField: Field? = null
        private var safelyAddMethod: Method? = null

        private fun setLore(potionMeta: PotionMeta, lore: MutableList<String>) {
            if (loreField == null)
                loreField = potionMeta.javaClass.superclass.getDeclaredField("lore").apply { isAccessible = true }

            loreField!!.set(potionMeta, lore)
        }

        private fun safelyAdd(potionMeta: PotionMeta, value: List<String>, lore: MutableList<String>) {
            if (safelyAddMethod == null)
                safelyAddMethod = potionMeta.javaClass.superclass.getDeclaredMethod("safelyAdd",
                    java.lang.Iterable::class.java,
                    java.util.Collection::class.java,
                    true.javaClass
                ).apply { isAccessible = true }

            safelyAddMethod!!.invoke(potionMeta, value, lore, true)
        }

        fun makePotion(itemStack: ItemStack): ItemStack {
            val potionMeta = itemStack.itemMeta as PotionMeta
            potionMeta.persistentDataContainer.set(potionNamespacedKey, PersistentDataType.STRING, itemStack.type.name)
            potionMeta.basePotionType = PotionType.UNCRAFTABLE
            if (itemStack.type == Material.POTION)
                potionMeta.addCustomEffect(PotionEffect(PotionEffectType.SPEED, 40, 3, false, true, true), true)
            potionMeta.setDisplayName("§r${when (itemStack.type) {
                Material.POTION -> DEFAULT_POTION_NAME
                Material.SPLASH_POTION -> DEFAULT_SPLASH_NAME
                Material.LINGERING_POTION -> DEFAULT_LINGERING_NAME
                else -> throw IllegalStateException("Unhandled potion form: ${itemStack.type}")
            }}")
            // INJECTION MODE ACTIVATED
            val loreList = mutableListOf<String>()
            val loreValue = listOf("""
            {
                "translate": "entity.minecraft.lightning_bolt",
                "color": "red"
            }""".trimIndent())
            safelyAdd(potionMeta, loreValue, loreList)
            setLore(potionMeta, loreList)
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
        private var shallGlint = false
    }

    enum class LingeringLightningMode {
        GM4,
        VANILLA,
        CONSTANT
    }

    private val lingeringLightningMode: LingeringLightningMode

    init {
        potionNamespacedKey = NamespacedKey(plugin, "lightning-potion")
        shallGlint = config.getBoolean("has-enchantment-glint", false)
        customModelData = config.get("custom-model-data") as? Int?
        lingeringLightningMode = LingeringLightningMode.valueOf(config.getString("lingering-lightning-mode", "GM4")!!.uppercase())
        if (lingeringLightningMode == LingeringLightningMode.VANILLA)
            Bukkit.getPluginManager().registerEvents(VanillaLingeringListener(), plugin)
    }

    @EventHandler
    private fun onLightningStrike(event: LightningStrikeEvent) {
        if (event.cause == LightningStrikeEvent.Cause.CUSTOM) return // Don't duplicate potions
        val lightningRod = event.lightning.location.block.getRelative(BlockFace.DOWN)
        if (lightningRod.type != Material.LIGHTNING_ROD) return
        val brewingStand = lightningRod.getRelative((lightningRod.blockData as Directional).facing.oppositeFace)
        if (brewingStand.type != Material.BREWING_STAND) return
        val state = brewingStand.state as BrewingStand
        if (state.fuelLevel <= 0) return
        var actuallyDidSomething = false
        val inv = state.inventory.contents.clone()
        for (item in inv) {
            val potionMeta = item?.itemMeta as? PotionMeta ?: continue
            if (potionMeta.basePotionType != PotionType.THICK) continue
            actuallyDidSomething = true
            makePotion(item)
        }
        if (!actuallyDidSomething) return
        --state.fuelLevel
        state.brewingTime = 0
        state.update()
        state.inventory.contents = inv
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

        if (lingeringLightningMode == LingeringLightningMode.VANILLA) return

        event.areaEffectCloud.durationOnUse = 0
        event.areaEffectCloud.radiusOnUse = 0f

        if (lingeringLightningMode == LingeringLightningMode.CONSTANT)
            ConstantRunnable(event.areaEffectCloud).runTaskTimer(plugin, 0L, 1L)
        else // GM4
            GM4Runnable(event.areaEffectCloud).runTaskTimer(plugin, 80L, 80L)
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

    class VanillaLingeringListener : Listener {
        @EventHandler
        private fun onAreaEffectCloud(event: AreaEffectCloudApplyEvent) {
            if (event.entity.persistentDataContainer.has(potionNamespacedKey))
                for (entity in event.affectedEntities)
                    entity.world.strikeLightning(entity.location)
        }
    }

    class GM4Runnable(private val areaEffectCloud: AreaEffectCloud) : BukkitRunnable() {
        init {
            areaEffectCloud.world.strikeLightning(areaEffectCloud.location)
        }

        override fun run() {
            if (!areaEffectCloud.isValid)
                return cancel()

            if (Random.nextBoolean())
                areaEffectCloud.world.strikeLightning(areaEffectCloud.location)
        }
    }

    class ConstantRunnable(private val areaEffectCloud: AreaEffectCloud) : BukkitRunnable() {
        private var lightning = areaEffectCloud.world.strikeLightning(areaEffectCloud.location)

        init {
            areaEffectCloud.duration /= 2
        }

        override fun run() {
            if (!areaEffectCloud.isValid)
                return cancel()

            if (!lightning.isValid)
                lightning = areaEffectCloud.world.strikeLightning(areaEffectCloud.location)
        }
    }
}