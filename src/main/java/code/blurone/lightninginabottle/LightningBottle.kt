package code.blurone.lightninginabottle

import org.bukkit.plugin.java.JavaPlugin

class LightningBottle : JavaPlugin() {
    override fun onEnable() {
        saveDefaultConfig()
        config.getConfigurationSection("lightning-in-a-bottle")?.let {
            if (!it.getBoolean("enabled", true)) return@let

            server.pluginManager.registerEvents(LightningBrewer(it, this), this)

            config.getConfigurationSection("translations")?.let letSequel@{ jt ->
                if (!jt.getBoolean("enabled", false))
                    return@letSequel

                val pt = jt.getConfigurationSection("potion-translations")
                val st = jt.getConfigurationSection("splash-translations")
                val lt = jt.getConfigurationSection("lingering-translations")
                //val nt = jt.getConfigurationSection("info_translations")

                if (pt != null && st != null && lt != null) // && nt != null)
                    server.pluginManager.registerEvents(OcdTranslator(pt, st, lt, this), this)
                else
                    logger.warning("One or more translation sections are missing. Disabling translation feature.")
            }
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
