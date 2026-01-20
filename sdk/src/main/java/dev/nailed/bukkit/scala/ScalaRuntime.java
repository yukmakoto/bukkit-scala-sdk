package dev.nailed.bukkit.scala;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Scala runtime manager.
 * Downloads Scala libraries and creates a unified ClassLoader for Scala plugins.
 * 
 * Architecture:
 * - Creates a ScalaPluginClassLoader that combines: Scala libs + Plugin JAR + Bukkit API
 * - No injection needed - we control the ClassLoader entirely
 * - Works on all Java versions (8-21+) without JVM args
 */
public class ScalaRuntime {
    
    private static final String SCALA_VERSION = "3.3.1";
    private static final String SCALA2_VERSION = "2.13.12";
    private static final String SCALA3_JAR = "scala3-library_3-" + SCALA_VERSION + ".jar";
    private static final String SCALA2_JAR = "scala-library-" + SCALA2_VERSION + ".jar";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    
    private static volatile boolean initialized = false;
    private static volatile File scala3JarFile = null;
    private static volatile File scala2JarFile = null;
    private static final Object lock = new Object();
    
    // Per-plugin ClassLoader cache
    private static final java.util.Map<String, ScalaPluginClassLoader> pluginClassLoaders = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Initialize Scala runtime - downloads libraries if needed.
     * Does NOT inject into any ClassLoader.
     */
    public static void initialize(Logger logger) {
        if (initialized) return;
        
        synchronized (lock) {
            if (initialized) return;
            
            try {
                File serverRoot = new File(System.getProperty("user.dir"));
                File libDir = new File(serverRoot, "libraries/scala");
                if (!libDir.exists()) libDir.mkdirs();
                
                scala3JarFile = new File(libDir, SCALA3_JAR);
                scala2JarFile = new File(libDir, SCALA2_JAR);
                
                downloadIfNeeded(libDir, scala3JarFile, 
                    MAVEN_CENTRAL + "/org/scala-lang/scala3-library_3/" + SCALA_VERSION + "/" + SCALA3_JAR, logger);
                downloadIfNeeded(libDir, scala2JarFile,
                    MAVEN_CENTRAL + "/org/scala-lang/scala-library/" + SCALA2_VERSION + "/" + SCALA2_JAR, logger);
                
                initialized = true;
                logger.info("[Scala SDK] Scala " + SCALA_VERSION + " libraries ready");
                
            } catch (Exception e) {
                throw new RuntimeException("[Scala SDK] Failed to initialize Scala runtime", e);
            }
        }
    }
    
    /**
     * Create a ClassLoader for loading a Scala plugin.
     * 
     * @param pluginJar The plugin's JAR file
     * @param pluginClassLoader The Bukkit PluginClassLoader (used as parent for Bukkit API access)
     * @param logger Logger for messages
     * @return ClassLoader that can load Scala classes and the plugin
     */
    public static ClassLoader createPluginClassLoader(File pluginJar, ClassLoader pluginClassLoader, Logger logger) {
        if (!initialized) {
            throw new IllegalStateException("Scala runtime not initialized");
        }
        
        String pluginPath = pluginJar.getAbsolutePath();
        
        // Check cache
        ScalaPluginClassLoader cached = pluginClassLoaders.get(pluginPath);
        if (cached != null) {
            return cached;
        }
        
        try {
            URL[] urls = new URL[] {
                scala3JarFile.toURI().toURL(),
                scala2JarFile.toURI().toURL(),
                pluginJar.toURI().toURL()
            };
            
            ScalaPluginClassLoader loader = new ScalaPluginClassLoader(urls, pluginClassLoader);
            pluginClassLoaders.put(pluginPath, loader);
            
            logger.info("[Scala SDK] Created ClassLoader for " + pluginJar.getName());
            return loader;
            
        } catch (Exception e) {
            throw new RuntimeException("[Scala SDK] Failed to create plugin ClassLoader", e);
        }
    }
    
    /**
     * Get ClassLoader for a plugin (must be created first).
     */
    public static ClassLoader getPluginClassLoader(File pluginJar) {
        return pluginClassLoaders.get(pluginJar.getAbsolutePath());
    }
    
    /**
     * Custom ClassLoader for Scala plugins.
     * 
     * Loading order:
     * 1. Bootstrap classes (java.*, javax.*)
     * 2. Bukkit API classes (from parent PluginClassLoader)
     * 3. Scala library classes (from our URLs)
     * 4. Plugin classes (from our URLs)
     * 
     * This ensures:
     * - Bukkit API is shared with other plugins
     * - Scala runtime is isolated per-plugin (no conflicts)
     * - Plugin classes can use both Bukkit and Scala
     */
    public static class ScalaPluginClassLoader extends URLClassLoader {
        
        private final ClassLoader bukkitClassLoader;
        
        public ScalaPluginClassLoader(URL[] urls, ClassLoader bukkitClassLoader) {
            super(urls, null); // null parent - we handle delegation manually
            this.bukkitClassLoader = bukkitClassLoader;
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                // 1. Check if already loaded
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    if (resolve) resolveClass(c);
                    return c;
                }
                
                // 2. Bootstrap classes - delegate to system
                if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.") || name.startsWith("jdk.")) {
                    return getSystemClassLoader().loadClass(name);
                }
                
                // 3. Bukkit/Spigot/Paper API - delegate to Bukkit ClassLoader
                if (isBukkitClass(name)) {
                    try {
                        c = bukkitClassLoader.loadClass(name);
                        if (resolve) resolveClass(c);
                        return c;
                    } catch (ClassNotFoundException ignored) {
                        // Fall through to try our URLs
                    }
                }
                
                // 4. Scala classes - load from our URLs (child-first)
                if (name.startsWith("scala.") || name.startsWith("dotty.")) {
                    try {
                        c = findClass(name);
                        if (resolve) resolveClass(c);
                        return c;
                    } catch (ClassNotFoundException ignored) {
                        // Fall through
                    }
                }
                
                // 5. Plugin classes - try our URLs first
                try {
                    c = findClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException ignored) {
                    // Fall through
                }
                
                // 6. Last resort - try Bukkit ClassLoader (for cross-plugin dependencies)
                return bukkitClassLoader.loadClass(name);
            }
        }
        
        private boolean isBukkitClass(String name) {
            return name.startsWith("org.bukkit.") ||
                   name.startsWith("org.spigotmc.") ||
                   name.startsWith("io.papermc.") ||
                   name.startsWith("com.destroystokyo.paper.") ||
                   name.startsWith("net.minecraft.") ||
                   name.startsWith("com.mojang.") ||
                   name.startsWith("net.md_5.bungee.");
        }
        
        @Override
        public URL getResource(String name) {
            // Try our URLs first
            URL url = findResource(name);
            if (url != null) return url;
            
            // Then Bukkit
            return bukkitClassLoader.getResource(name);
        }
        
        @Override
        public InputStream getResourceAsStream(String name) {
            URL url = getResource(name);
            if (url != null) {
                try {
                    return url.openStream();
                } catch (IOException ignored) {}
            }
            return null;
        }
    }
    
    // ==================== Legacy compatibility ====================
    
    @Deprecated
    public static void ensureLoaded(Logger logger, JavaPlugin plugin) {
        initialize(logger);
    }
    
    @Deprecated
    public static ClassLoader getClassLoader() {
        throw new UnsupportedOperationException(
            "getClassLoader() is deprecated. Use createPluginClassLoader() instead.");
    }
    
    private static void downloadIfNeeded(File libDir, File target, String url, Logger logger) throws Exception {
        if (target.exists() && target.length() > 0) return;
        
        File lockFile = new File(libDir, ".download.lock");
        if (!lockFile.exists()) lockFile.createNewFile();
        
        try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.WRITE)) {
            FileLock fileLock = channel.lock();
            try {
                // Double-check after acquiring lock
                if (!target.exists() || target.length() == 0) {
                    logger.info("[Scala SDK] Downloading " + target.getName() + "...");
                    File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");
                    try {
                        downloadFile(url, tempFile, logger);
                        // Atomic move
                        if (!tempFile.renameTo(target)) {
                            // Fallback: copy and delete
                            copyFile(tempFile, target);
                            tempFile.delete();
                        }
                    } catch (Exception e) {
                        tempFile.delete();
                        throw e;
                    }
                }
            } finally {
                fileLock.release();
            }
        }
    }
    
    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }
    
    private static void downloadFile(String urlStr, File target, Logger logger) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Bukkit-Scala-SDK/1.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + " for " + urlStr);
            }
            
            long totalSize = conn.getContentLengthLong();
            in = conn.getInputStream();
            out = new FileOutputStream(target);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long downloaded = 0;
            int lastProgress = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                
                if (totalSize > 0) {
                    int progress = (int) ((downloaded * 100) / totalSize);
                    if (progress >= lastProgress + 20) {
                        logger.info("[Scala SDK] Progress: " + progress + "%");
                        lastProgress = progress;
                    }
                }
            }
            
            logger.info("[Scala SDK] Downloaded " + (downloaded / 1024) + " KB");
            
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }
}
