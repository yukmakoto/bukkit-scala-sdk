package dev.nailed.bukkit.scala

import org.bukkit.Server
import org.bukkit.command.{CommandSender, PluginCommand}
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.{Plugin, PluginManager}
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Logger

/**
 * Trait for Scala Bukkit plugins.
 * 
 * Provides convenient access to plugin APIs without ugly field declarations.
 * The `_plugin` field is injected by the SDK bootstrap.
 */
trait BukkitPlugin:
  
  // Internal field - injected by SDK, don't touch
  private[scala] var _plugin: JavaPlugin = _
  
  // Lifecycle methods - override these
  def onEnable(): Unit = ()
  def onDisable(): Unit = ()
  def onLoad(): Unit = ()
  
  // Convenient accessors
  final def plugin: JavaPlugin = _plugin
  final def logger: Logger = _plugin.getLogger
  final def server: Server = _plugin.getServer
  final def dataFolder: File = _plugin.getDataFolder
  final def config: FileConfiguration = _plugin.getConfig
  final def pluginManager: PluginManager = server.getPluginManager
  
  // Helper methods
  final def saveConfig(): Unit = _plugin.saveConfig()
  final def saveDefaultConfig(): Unit = _plugin.saveDefaultConfig()
  final def reloadConfig(): Unit = _plugin.reloadConfig()
  final def getCommand(name: String): PluginCommand = _plugin.getCommand(name)
  
  // Logging shortcuts
  final def info(msg: String): Unit = logger.info(msg)
  final def warn(msg: String): Unit = logger.warning(msg)
  final def severe(msg: String): Unit = logger.severe(msg)
  
  // Register this as listener if it implements Listener
  final def registerEvents(): Unit =
    this match
      case listener: org.bukkit.event.Listener => 
        pluginManager.registerEvents(listener, _plugin)
      case _ => ()
