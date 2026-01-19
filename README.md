# Bukkit Scala SDK

Write Bukkit/Spigot/Paper plugins in Scala 3 with minimal boilerplate.

## Features

- **Tiny JAR size**: ~25KB per plugin (Scala runtime shared across all plugins)
- **Auto runtime download**: Scala libraries downloaded on first server start
- **Zero boilerplate**: No need to extend JavaPlugin, just write Scala code
- **All Bukkit versions**: Works with Bukkit, Spigot, Paper (1.8+)

## Quick Start

### 1. Add dependencies to pom.xml

```xml
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

### 2. Add build plugins

```xml
<build>
    <sourceDirectory>src/main/scala</sourceDirectory>
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

import org.bukkit.plugin.java.JavaPlugin

class MyPlugin:
  var plugin: JavaPlugin = _  // Injected by SDK
  
  def onEnable(): Unit =
    plugin.getLogger.info("Hello from Scala 3!")

  def onDisable(): Unit =
    plugin.getLogger.info("Goodbye!")
```

### 4. Create plugin.yml

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
api-version: 1.16
```

That's it! The Maven plugin automatically generates a bootstrap class.

## How It Works

1. Maven plugin reads `main` from plugin.yml
2. Generates `MyPlugin$$Bootstrap.java` that extends JavaPlugin
3. Updates plugin.yml to point to the bootstrap class
4. At runtime, bootstrap downloads Scala libraries to `libraries/scala/`
5. Scala libraries are injected into ClassLoader
6. Your Scala class is instantiated and `plugin` field is injected

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

## License

MIT
