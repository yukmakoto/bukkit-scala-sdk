package com.example

import dev.nailed.bukkit.scala.BukkitPlugin
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import java.util.concurrent.Executors

/**
 * Example Scala 3 Bukkit plugin demonstrating Scala-specific features.
 */
class ExamplePlugin extends BukkitPlugin with Listener:
  
  // Scala 3: Top-level given for ExecutionContext
  given ExecutionContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(2)
  )

  // ============ Scala 3 Enums ============
  enum Rank:
    case Member, VIP, Admin
    
    def prefix: String = this match
      case Member => "§7[Member]"
      case VIP    => "§6[VIP]"
      case Admin  => "§c[Admin]"

  // ============ Case Classes & Pattern Matching ============
  case class PlayerData(name: String, rank: Rank, coins: Int)
  
  // ============ Extension Methods ============
  extension (p: Player)
    def sendColored(msg: String): Unit = 
      p.sendMessage(msg.replace("&", "§"))
    
    def isVIP: Boolean = p.hasPermission("example.vip")

  // ============ Opaque Types ============
  opaque type Coins = Int
  object Coins:
    def apply(value: Int): Coins = value
    extension (c: Coins)
      def value: Int = c
      def +(other: Coins): Coins = c + other
      def display: String = s"§e$c coins"
  
  import Coins.*

  // ============ Union Types ============
  type CommandResult = String | Int | Unit
  
  def processResult(result: CommandResult): String = result match
    case s: String => s"Message: $s"
    case i: Int    => s"Code: $i"
    case _         => "Success"

  // ============ Plugin Lifecycle ============
  override def onEnable(): Unit =
    info("§a========== Scala 3 Features Demo ==========")
    
    // Case class
    val data = PlayerData("Steve", Rank.VIP, 100)
    info(s"§bCase class: $data")
    
    // Pattern matching with guards
    val message = data match
      case PlayerData(name, Rank.Admin, _) => s"$name is an admin!"
      case PlayerData(name, _, coins) if coins > 50 => s"$name is rich!"
      case PlayerData(name, rank, _) => s"$name is a ${rank.prefix}"
    info(s"§bPattern match: $message")
    
    // Opaque types
    val wallet = Coins(500)
    val bonus = Coins(100)
    info(s"§bOpaque type: ${(wallet + bonus).display}")
    
    // Union types
    val results: List[CommandResult] = List("Hello", 42, ())
    results.foreach(r => info(s"§bUnion type: ${processResult(r)}"))
    
    // For comprehension with Option
    val result = for
      a <- Some(10)
      b <- Some(20)
      if a + b > 25
    yield a + b
    info(s"§bFor comprehension: $result")
    
    // Higher-order functions
    val numbers = (1 to 5).toList
    val doubled = numbers.map(_ * 2)
    val sum = numbers.foldLeft(0)(_ + _)
    info(s"§bHOF: doubled=$doubled, sum=$sum")
    
    // Async with Future
    Future {
      Thread.sleep(100)
      "Async result"
    }.foreach(r => server.getScheduler.runTask(plugin, (() => 
      info(s"§bFuture completed: $r")
    ): Runnable))
    
    // Register command
    getCommand("scala").setExecutor(ScalaCommand())
    
    registerEvents()
    info("§a============================================")

  override def onDisable(): Unit =
    info("§cGoodbye from Scala 3!")

  // ============ Event Handler with Extension Methods ============
  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit =
    val p = event.getPlayer
    
    // Using extension method
    p.sendColored("&aWelcome! &7This server uses &bScala 3&7!")
    
    // Using enum
    val rank = if p.isOp then Rank.Admin 
               else if p.isVIP then Rank.VIP 
               else Rank.Member
    
    p.sendColored(s"&7Your rank: ${rank.prefix}")
    
    // Using inline if (Scala 3)
    val greeting = if p.isOp then "Welcome back, Admin!" else "Hello!"
    info(s"§e${p.getName} joined - $greeting")

  // ============ Command with Pattern Matching ============
  class ScalaCommand extends CommandExecutor:
    override def onCommand(sender: CommandSender, cmd: Command, 
                           label: String, args: Array[String]): Boolean =
      // Scala 3: match on array patterns
      args.toList match
        case Nil => 
          sender.sendMessage("§aScala 3 plugin is running!")
        case "info" :: Nil =>
          sender.sendMessage("§bScala version: 3.3.1")
          sender.sendMessage("§bFeatures: enums, extensions, opaque types, union types")
        case "coins" :: amount :: Nil =>
          Try(amount.toInt) match
            case Success(n) => 
              val coins = Coins(n)
              sender.sendMessage(s"§aYou have ${coins.display}")
            case Failure(_) => 
              sender.sendMessage("§cInvalid amount!")
        case "rank" :: name :: Nil =>
          // Using Scala 3 enum valueOf
          Try(Rank.valueOf(name.capitalize)) match
            case Success(rank) => sender.sendMessage(s"§aRank: ${rank.prefix}")
            case Failure(_) => sender.sendMessage("§cUnknown rank!")
        case _ =>
          sender.sendMessage("§cUsage: /scala [info|coins <n>|rank <name>]")
      true
