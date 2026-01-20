# Bukkit Scala SDK

Write Bukkit/Spigot/Paper plugins in Scala 3 with minimal boilerplate.

## Features

- **Tiny JAR size**: ~25KB per plugin (Scala runtime shared across all plugins)
- **Auto runtime download**: Scala libraries downloaded on first server start
- **Zero boilerplate**: Just extend `BukkitPlugin` trait
- **All Bukkit versions**: Works with Bukkit, Spigot, Paper (1.8-1.21+)
- **Java 8-21 support**: Compatible with all modern Java versions

## Quick Start

### 1. Add repository and dependencies to pom.xml

```xml
<repositories>
    <repository>
        <id>nailed-repo</id>
        <url>https://repo.nailed.dev/repository/maven-releases/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>dev.nailed</groupId>
        <artifactId>bukkit-scala-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.scala-lang</groupId>
        <artifactId>scala3-library_3</artifactId>
        <version>3.3.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 2. Add plugin repository and build plugins

```xml
<build>
    <sourceDirectory>src/main/scala</sourceDirectory>
    <pluginRepositories>
        <pluginRepository>
            <id>nailed-repo</id>
            <url>https://repo.nailed.dev/repository/maven-releases/</url>
        </pluginRepository>
    </pluginRepositories>
    <plugins>
        <!-- Generate bootstrap -->
        <plugin>
            <groupId>dev.nailed</groupId>
            <artifactId>bukkit-scala-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <goals><goal>generate-bootstrap</goal></goals>
                </execution>
            </executions>
        </plugin>

        <!-- Compile Scala -->
        <plugin>
            <groupId>net.alchim31.maven</groupId>
            <artifactId>scala-maven-plugin</artifactId>
            <version>4.8.1</version>
            <executions>
                <execution>
                    <goals><goal>compile</goal></goals>
                </execution>
            </executions>
        </plugin>

        <!-- Shade SDK into JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <artifactSet>
                            <includes>
                                <include>dev.nailed:bukkit-scala-sdk</include>
                            </includes>
                        </artifactSet>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 3. Write your plugin

```scala
package com.example

import dev.nailed.bukkit.scala.BukkitPlugin
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent

class MyPlugin extends BukkitPlugin with Listener:
  
  override def onEnable(): Unit =
    info("Hello from Scala 3!")
    registerEvents()

  override def onDisable(): Unit =
    info("Goodbye!")

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit =
    event.getPlayer.sendMessage("§aWelcome!")
```

### 4. Create plugin.yml

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
api-version: 1.16
```

That's it! The Maven plugin automatically generates a bootstrap class.

## BukkitPlugin Trait

The `BukkitPlugin` trait provides convenient accessors:

```scala
trait BukkitPlugin:
  // Lifecycle methods - override these
  def onEnable(): Unit
  def onDisable(): Unit
  def onLoad(): Unit
  
  // Convenient accessors
  def plugin: JavaPlugin      // The underlying JavaPlugin
  def logger: Logger          // Plugin logger
  def server: Server          // Bukkit server
  def dataFolder: File        // Plugin data folder
  def config: FileConfiguration
  def pluginManager: PluginManager
  
  // Helper methods
  def saveConfig(): Unit
  def saveDefaultConfig(): Unit
  def reloadConfig(): Unit
  def getCommand(name: String): PluginCommand
  
  // Logging shortcuts
  def info(msg: String): Unit
  def warn(msg: String): Unit
  def severe(msg: String): Unit
  
  // Auto-register as Listener if implemented
  def registerEvents(): Unit
```

## How It Works

1. Maven plugin reads `main` from plugin.yml
2. Generates `MyPlugin$$Bootstrap.java` that extends JavaPlugin
3. Updates plugin.yml to point to the bootstrap class
4. At runtime, bootstrap downloads Scala libraries to `libraries/scala/`
5. Creates a custom `ScalaPluginClassLoader` for each plugin
6. Your Scala class is loaded and `_plugin` field is injected

The custom ClassLoader ensures:
- Scala classes are loaded from our JARs (not parent ClassLoader)
- Bukkit API is shared with other plugins
- No JVM flags needed on any Java version

## Runtime Structure

```
server/
├── plugins/
│   └── MyPlugin.jar          # ~25KB
└── libraries/
    └── scala/
        ├── scala3-library_3-3.3.1.jar    # ~1.2MB (shared)
        └── scala-library-2.13.12.jar     # ~5.8MB (shared)
```

## Java Version Compatibility

The SDK supports Java 8-21 without any special JVM flags. It uses a custom ClassLoader architecture that works on all Java versions:

- **Java 8-15**: Standard URLClassLoader injection
- **Java 16-21**: Custom ScalaPluginClassLoader (no `--add-opens` needed)

No configuration required - just drop your plugin JAR and it works.

## Supported Server Versions

| Server | Versions | Notes |
|--------|----------|-------|
| Bukkit/CraftBukkit | 1.8+ | Full support |
| Spigot | 1.8+ | Full support |
| Paper | 1.8-1.21+ | Uses native `addToClasspath` on 1.19.3+ |
| Purpur | 1.16+ | Full support |
| Folia | 1.19.4+ | Full support |

## License

MIT
