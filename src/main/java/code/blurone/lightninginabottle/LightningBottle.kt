package code.blurone.lightninginabottle

import org.bukkit.plugin.java.JavaPlugin

class LightningBottle : JavaPlugin() {
    override fun onEnable() {
        config.getConfigurationSection("lightning-in-a-bottle")?.let {
            if (it.getBoolean("enabled", true))
                server.pluginManager.registerEvents(LightningBrewer(it, this), this)
        }
        config.getConfigurationSection("lightning-transformations")?.let {
            if (it.getBoolean("enabled", false)) return

            it.getConfigurationSection("entities")?.let { config ->
                val map = config.getValues(false)
                if (map.isNotEmpty())
                    server.pluginManager.registerEvents(LightningEntityTransformation(map), this)
            }

            it.getConfigurationSection("blocks")?.let { config ->
                val map = config.getValues(false)
                if (map.isNotEmpty())
                    server.pluginManager.registerEvents(LightningBlockTransformation(map), this)
            }
        }
    }
}
