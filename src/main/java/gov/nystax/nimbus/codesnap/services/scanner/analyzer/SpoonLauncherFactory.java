package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;

import java.nio.file.Path;
import java.util.List;

public final class SpoonLauncherFactory {

    private static final Logger logger = LoggerFactory.getLogger(SpoonLauncherFactory.class);

    private SpoonLauncherFactory() {
    }

    public static Launcher createLauncher(Path projectPath, Path srcPath,
                                          MavenClasspathConfig mavenConfig) {
        Preconditions.checkNotNull(projectPath, "projectPath cannot be null");
        Preconditions.checkNotNull(srcPath, "srcPath cannot be null");
        Preconditions.checkNotNull(mavenConfig, "mavenConfig cannot be null");

        Launcher launcher = new Launcher();
        launcher.addInputResource(srcPath.toString());
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);

        if (!mavenConfig.enabled()) {
            launcher.getEnvironment().setNoClasspath(true);
            return launcher;
        }

        List<Path> dependencyJars = new MavenDependencyResolver(
                mavenConfig.settingsXmlPath(),
                mavenConfig.mavenHomePath())
                .resolveAndDownload(projectPath);

        if (dependencyJars.isEmpty()) {
            logger.warn("No Maven dependencies were resolved for {}. Falling back to noClasspath mode.", projectPath);
            launcher.getEnvironment().setNoClasspath(true);
            return launcher;
        }

        String[] sourceClasspath = dependencyJars.stream()
                .map(path -> path.toAbsolutePath().toString())
                .toArray(String[]::new);

        launcher.getEnvironment().setSourceClasspath(sourceClasspath);
        launcher.getEnvironment().setNoClasspath(false);
        logger.info("Configured Spoon with {} Maven dependency entries", sourceClasspath.length);
        return launcher;
    }
}
