package dev.nailed.bukkit.scala;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Scala runtime manager.
 * Downloads and injects Scala libraries at runtime.
 */
public class ScalaRuntime {
    
    private static final String SCALA_VERSION = "3.3.1";
    private static final String SCALA2_VERSION = "2.13.12";
    private static final String SCALA3_JAR = "scala3-library_3-" + SCALA_VERSION + ".jar";
    private static final String SCALA2_JAR = "scala-library-" + SCALA2_VERSION + ".jar";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    
    private static volatile boolean loaded = false;
    private static final Object lock = new Object();
    
    /**
     * Ensure Scala runtime is loaded.
     */
    public static void ensureLoaded(Logger logger) {
        if (loaded) return;
        
        synchronized (lock) {
            if (loaded) return;
            
            try {
                File serverRoot = new File(System.getProperty("user.dir"));
                File libDir = new File(serverRoot, "libraries/scala");
                if (!libDir.exists()) libDir.mkdirs();
                
                File scala3Jar = new File(libDir, SCALA3_JAR);
                File scala2Jar = new File(libDir, SCALA2_JAR);
                
                downloadIfNeeded(libDir, scala3Jar, 
                    MAVEN_CENTRAL + "/org/scala-lang/scala3-library_3/" + SCALA_VERSION + "/" + SCALA3_JAR, logger);
                downloadIfNeeded(libDir, scala2Jar,
                    MAVEN_CENTRAL + "/org/scala-lang/scala-library/" + SCALA2_VERSION + "/" + SCALA2_JAR, logger);
                
                ClassLoader pluginClassLoader = ScalaRuntime.class.getClassLoader();
                injectToClassLoader(pluginClassLoader, scala3Jar, logger);
                injectToClassLoader(pluginClassLoader, scala2Jar, logger);
                
                loaded = true;
                logger.info("[Scala SDK] Scala " + SCALA_VERSION + " runtime loaded");
                
            } catch (Exception e) {
                throw new RuntimeException("[Scala SDK] Failed to load Scala runtime", e);
            }
        }
    }
    
    /**
     * Get the ClassLoader (PluginClassLoader with Scala injected).
     */
    public static ClassLoader getClassLoader() {
        if (!loaded) {
            throw new IllegalStateException("Scala runtime not loaded");
        }
        return ScalaRuntime.class.getClassLoader();
    }
    
    private static void injectToClassLoader(ClassLoader classLoader, File jarFile, Logger logger) throws Exception {
        // Try URLClassLoader.addURL (Java 8)
        if (classLoader instanceof URLClassLoader) {
            java.lang.reflect.Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(classLoader, jarFile.toURI().toURL());
            logger.info("[Scala SDK] Injected " + jarFile.getName());
            return;
        }
        
        // Paper/Spigot PluginClassLoader - try libraryLoader
        try {
            java.lang.reflect.Field field = classLoader.getClass().getDeclaredField("libraryLoader");
            field.setAccessible(true);
            Object libraryLoader = field.get(classLoader);
            if (libraryLoader instanceof URLClassLoader) {
                java.lang.reflect.Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(libraryLoader, jarFile.toURI().toURL());
                logger.info("[Scala SDK] Injected " + jarFile.getName());
                return;
            }
        } catch (NoSuchFieldException ignored) {}
        
        // Try parent ClassLoader
        ClassLoader parent = classLoader.getParent();
        while (parent != null) {
            if (parent instanceof URLClassLoader) {
                java.lang.reflect.Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(parent, jarFile.toURI().toURL());
                logger.info("[Scala SDK] Injected " + jarFile.getName());
                return;
            }
            parent = parent.getParent();
        }
        
        throw new RuntimeException("Cannot inject JAR to ClassLoader: " + classLoader.getClass().getName());
    }
    
    private static void downloadIfNeeded(File libDir, File target, String url, Logger logger) throws Exception {
        if (target.exists()) return;
        
        File lockFile = new File(libDir, ".download.lock");
        if (!lockFile.exists()) lockFile.createNewFile();
        
        try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.WRITE)) {
            FileLock fileLock = channel.lock();
            try {
                if (!target.exists()) {
                    logger.info("[Scala SDK] Downloading " + target.getName() + "...");
                    downloadFile(url, target, logger);
                }
            } finally {
                fileLock.release();
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
            
            if (conn.getResponseCode() != 200) {
                throw new IOException("HTTP " + conn.getResponseCode());
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
            if (in != null) in.close();
            if (out != null) out.close();
            if (conn != null) conn.disconnect();
        }
    }
}
