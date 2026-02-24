package gov.nystax.nimbus.codesnap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the CodeSnaper library.
 * Provides utilities for capturing and analyzing Java source code snapshots.
 */
public class CodeSnaper {

    private static final Logger logger = LoggerFactory.getLogger(CodeSnaper.class);

    private CodeSnaper() {
    }

    /**
     * Returns the version of the CodeSnaper library.
     *
     * @return the library version string
     */
    public static String getVersion() {
        String version = CodeSnaper.class.getPackage().getImplementationVersion();
        return version != null ? version : "unknown";
    }
}
