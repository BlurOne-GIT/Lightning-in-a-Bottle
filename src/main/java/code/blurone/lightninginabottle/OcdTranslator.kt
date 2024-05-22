package code.blurone.lightninginabottle

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.BrewEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerLocaleChangeEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

class OcdTranslator(
    private val potionTranslations: ConfigurationSection,
    private val splashTranslations: ConfigurationSection,
    private val lingeringTranslations: ConfigurationSection,
    private val plugin: Plugin
) : Listener {
    companion object {
        internal const val DEFAULT_POTION_NAME = "Bottle of Lightning"
        internal const val DEFAULT_SPLASH_NAME = "Splash Bottle of Lightning"
        internal const val DEFAULT_LINGERING_NAME = "Lingering Bottle of Lightning"
    }

    @EventHandler
    private fun pickupTranslator(event: EntityPickupItemEvent) {
        if (event.entity.type != EntityType.PLAYER ||
            !LightningBrewer.isLightningBottle(event.item.itemStack))
            return

        ocdTranslator(event.item.itemStack, (event.entity as Player).locale)
    }

    @EventHandler
    private fun openInventoryTranslator(event: InventoryOpenEvent) {
        event.inventory.contents.forEach {
            if (it != null && LightningBrewer.isLightningBottle(it))
                ocdTranslator(it, (event.player as Player).locale)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private fun brewTranslator(event: BrewEvent) {
        if (event.contents.viewers.isEmpty())
            return

        event.contents.forEach {
            if (LightningBrewer.isLightningBottle(it))
                ocdTranslator(it, (event.contents.viewers[0] as Player).locale)
        }
    }

    @EventHandler
    private fun changeLocaleTranslator(event: PlayerLocaleChangeEvent) {
        event.player.inventory.contents.forEach {
            if (it != null && LightningBrewer.isLightningBottle(it))
                ocdTranslator(it, event.locale)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private fun playerInteractEntity(event: PlayerInteractEntityEvent) {
        val item = event.player.inventory.getItem(event.hand) ?: return
        if (event.rightClicked !is ItemFrame || (event.rightClicked as ItemFrame).item.type != Material.AIR
            || !LightningBrewer.isLightningBottle(item))
            return

        val meta = item.itemMeta!!
        meta.setDisplayName(null)
        item.itemMeta = meta

        object : BukkitRunnable() {
            override fun run() {
                if (LightningBrewer.isLightningBottle(item))
                    ocdTranslator(item, event.player.locale)
            }
        }.runTaskLater(plugin, 0)
    }

    private fun ocdTranslator(item: ItemStack, locale: String) {
        val strippedLocale = locale.split("_")[0]
        val meta = item.itemMeta!!
        val itemTranslations = when (item.type) {
            Material.POTION -> potionTranslations
            Material.SPLASH_POTION -> splashTranslations
            Material.LINGERING_POTION -> lingeringTranslations
            else -> throw IllegalStateException("Unhandled potion form: ${item.type}")
        }
        val defaultTranslation = when (item.type) {
            Material.POTION -> DEFAULT_POTION_NAME
            Material.SPLASH_POTION -> DEFAULT_SPLASH_NAME
            Material.LINGERING_POTION -> DEFAULT_LINGERING_NAME
            else -> throw SecurityException("An IllegalStateException should have been fired before; something is modifying the memory.")
        }
        itemTranslations.getString(
            locale,
            itemTranslations.getString(strippedLocale, defaultTranslation)
        )!!.let { meta.setDisplayName("§r$it") }
        /*infoTranslations.getString(
            locale,
            infoTranslations.getString(strippedLocale, DEFAULT_INFO)
        )!!.let { meta.lore = listOf("§9$it") }*/
        item.itemMeta = meta
    }
}