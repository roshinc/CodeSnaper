package gov.nystax.nimbus.codesnap.util;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    private FileUtils() {
    }

    /**
     * Checks if the provided Path is not empty. A Path is considered not empty if
     * it represents a directory containing at least one entry, or a file with a
     * size greater than zero.
     *
     * @param path the Path to check
     * @return true if the Path is not empty, false otherwise
     */
    public static boolean isPathNotEmpty(Path path) {

        Preconditions.checkNotNull(path, "Provided path is null.");
        Preconditions.checkArgument(Files.exists(path), "Cannot check a path that doesnt exist: %s", path);

        try {
            if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
                    if (directoryStream.iterator().hasNext()) {
                        logger.info("Directory is not empty: {}", path);
                        return true;
                    } else {
                        logger.info("Directory is empty: {}", path);
                        return false;
                    }
                }
            } else {
                long size = Files.size(path);
                if (size > 0) {
                    logger.info("File is not empty: {}", path);
                    return true;
                } else {
                    logger.info("File is empty: {}", path);
                    return false;
                }
            }
        } catch (IOException e) {
            logger.error("Error checking if path is not empty: {}", path, e);
            return false;
        }
    }

    /**
     * Deletes the directory.
     *
     * @throws IOException If an error occurs while deleting the directory.
     */
    public static void deleteFolder(Path localRepo) throws IOException {
        if (Files.exists(localRepo)) {
            deleteDirectory(localRepo.toFile());
            logger.info("Local repository deleted.");
        } else {
            logger.warn("Local repository not found. Skipping delete operation.");
        }
    }

    /**
     * Recursively deletes a directory and its contents.
     *
     * @param directory The directory to delete.
     * @throws IOException If an error occurs while deleting the directory.
     */
    private static void deleteDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file);
                    }
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete directory: " + directory);
        }
    }

}
