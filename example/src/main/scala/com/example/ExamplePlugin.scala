package com.example

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Example Scala 3 Bukkit plugin.
 * 
 * No need to extend JavaPlugin - just define onEnable/onDisable methods
 * and a `plugin` field for SDK injection.
 */
class ExamplePlugin extends Listener:
  
  // Injected by SDK
  var plugin: JavaPlugin = _
  
  def onEnable(): Unit =
    plugin.getLogger.info("Hello from Scala 3!")
    plugin.getServer.getPluginManager.registerEvents(this, plugin)
    
    // Scala 3 features work!
    case class Player(name: String, level: Int)
    val player = Player("Steve", 42)
    plugin.getLogger.info(s"Case class: $player")

  def onDisable(): Unit =
    plugin.getLogger.info("Goodbye from Scala 3!")

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit =
    event.getPlayer.sendMessage("§aWelcome! This message is from Scala 3!")
