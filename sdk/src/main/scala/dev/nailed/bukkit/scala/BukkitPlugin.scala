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
 * The `_plugin` field is injected by the SDK bootstrap via setPlugin() method.
 * 
 * Supports Bukkit/Spigot/Paper 1.8-1.21+ with Scala 3.
 */
trait BukkitPlugin:
  
  // Internal field - injected by SDK bootstrap
  @volatile private var _plugin: JavaPlugin = _
  
  /**
   * Called by SDK bootstrap to inject the JavaPlugin instance.
   * This method is public to ensure reflection can find it across all Java versions.
   */
  def setPlugin(plugin: JavaPlugin): Unit =
    this._plugin = plugin
  
  // Lifecycle methods - override these
  def onEnable(): Unit = ()
  def onDisable(): Unit = ()
  def onLoad(): Unit = ()
  
  // Convenient accessors
  final def plugin: JavaPlugin = 
    if _plugin == null then throw new IllegalStateException("Plugin not initialized")
    else _plugin
    
  final def logger: Logger = plugin.getLogger
  final def server: Server = plugin.getServer
  final def dataFolder: File = plugin.getDataFolder
  final def config: FileConfiguration = plugin.getConfig
  final def pluginManager: PluginManager = server.getPluginManager
  
  // Helper methods
  final def saveConfig(): Unit = plugin.saveConfig()
  final def saveDefaultConfig(): Unit = plugin.saveDefaultConfig()
  final def reloadConfig(): Unit = plugin.reloadConfig()
  final def getCommand(name: String): PluginCommand = plugin.getCommand(name)
  
  // Logging shortcuts
  final def info(msg: String): Unit = logger.info(msg)
  final def warn(msg: String): Unit = logger.warning(msg)
  final def severe(msg: String): Unit = logger.severe(msg)
  
  // Register this as listener if it implements Listener
  final def registerEvents(): Unit =
    this match
      case listener: org.bukkit.event.Listener => 
        pluginManager.registerEvents(listener, plugin)
      case _ => ()
  
  // Check if plugin is initialized
  final def isInitialized: Boolean = _plugin != null
