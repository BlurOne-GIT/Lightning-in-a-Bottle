package code.blurone.lightninginabottle

import org.bukkit.plugin.java.JavaPlugin

class LightningBottle : JavaPlugin() {
    override fun onEnable() {
        config.getConfigurationSection("lightning-in-a-bottle")?.let {
            if (it.getBoolean("enabled", true))
                server.pluginManager.registerEvents(LightningBrewer(it, this), this)
        }

    }
}
