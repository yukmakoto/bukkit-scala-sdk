package dev.nailed.bukkit.scala.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/**
 * Generates bootstrap class for Scala Bukkit plugins.
 * 
 * Reads the main class from plugin.yml, generates a Java bootstrap class,
 * and updates plugin.yml to point to the bootstrap class.
 */
@Mojo(name = "generate-bootstrap", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateBootstrapMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/plugin.yml", required = true)
    private File pluginYml;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/scala-bootstrap", required = true)
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if (!pluginYml.exists()) {
            getLog().warn("plugin.yml not found: " + pluginYml.getAbsolutePath());
            return;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> pluginConfig;
            try (InputStream is = new FileInputStream(pluginYml)) {
                pluginConfig = yaml.load(is);
            }

            String mainClass = (String) pluginConfig.get("main");
            if (mainClass == null || mainClass.isEmpty()) {
                throw new MojoExecutionException("plugin.yml missing 'main' field");
            }

            String bootstrapClass = mainClass + "$$Bootstrap";
            String packageName = mainClass.substring(0, mainClass.lastIndexOf('.'));
            String simpleBootstrapClass = mainClass.substring(mainClass.lastIndexOf('.') + 1) + "$$Bootstrap";

            getLog().info("Generating bootstrap: " + bootstrapClass);

            generateBootstrapClass(packageName, simpleBootstrapClass, mainClass);
            updatePluginYml(pluginConfig, bootstrapClass);

            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate bootstrap", e);
        }
    }

    private void generateBootstrapClass(String packageName, String className, String scalaMainClass) throws IOException {
        Path packageDir = outputDirectory.toPath().resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);

        String sourceCode = 
            "package " + packageName + ";\n\n" +
            "import dev.nailed.bukkit.scala.ScalaBootstrap;\n\n" +
            "/** Auto-generated bootstrap for " + scalaMainClass + " */\n" +
            "public class " + className + " extends ScalaBootstrap {\n" +
            "    @Override\n" +
            "    protected String getScalaMainClass() {\n" +
            "        return \"" + scalaMainClass + "\";\n" +
            "    }\n" +
            "}\n";

        Path sourceFile = packageDir.resolve(className + ".java");
        Files.write(sourceFile, sourceCode.getBytes(StandardCharsets.UTF_8));
        getLog().info("Generated: " + sourceFile);
    }

    private void updatePluginYml(Map<String, Object> config, String bootstrapClass) throws IOException {
        config.put("main", bootstrapClass);

        Path targetPluginYml = Paths.get(project.getBuild().getOutputDirectory(), "plugin.yml");
        Files.createDirectories(targetPluginYml.getParent());

        Yaml yaml = new Yaml();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(targetPluginYml.toFile()), StandardCharsets.UTF_8)) {
            yaml.dump(config, writer);
        }
        getLog().info("Updated plugin.yml: main -> " + bootstrapClass);
    }
}
