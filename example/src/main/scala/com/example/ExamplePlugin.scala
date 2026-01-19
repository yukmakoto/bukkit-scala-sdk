package com.example

import dev.nailed.bukkit.scala.BukkitPlugin
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Example Scala 3 Bukkit plugin.
 * 
 * Just extend BukkitPlugin trait - no ugly field declarations needed!
 */
class ExamplePlugin extends BukkitPlugin with Listener:
  
  override def onEnable(): Unit =
    info("Hello from Scala 3!")
    registerEvents()
    
    // Scala 3 features work!
    case class Player(name: String, level: Int)
    val player = Player("Steve", 42)
    info(s"Case class: $player")

  override def onDisable(): Unit =
    info("Goodbye from Scala 3!")

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit =
    event.getPlayer.sendMessage("§aWelcome! This message is from Scala 3!")
