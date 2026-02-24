package gov.nystax.nimbus.codesnap.services.scanner.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A progress listener that logs all progress updates using SLF4J.
 */
public class LoggingProgressListener implements ScanProgressListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingProgressListener.class);

    @Override
    public void onPhaseStart(String phase, String description) {
        logger.info("=== Phase Started: {} ===", phase);
        if (description != null && !description.isEmpty()) {
            logger.info("  {}", description);
        }
    }

    @Override
    public void onPhaseComplete(String phase, boolean success) {
        if (success) {
            logger.info("=== Phase Completed: {} ===", phase);
        } else {
            logger.warn("=== Phase Failed: {} ===", phase);
        }
    }

    @Override
    public void onProgress(String phase, int current, int total, String message) {
        if (total > 0) {
            int percentage = (int) ((current * 100.0) / total);
            logger.info("[{}] Progress: {}/{} ({}%) - {}", phase, current, total, percentage,
                    message != null ? message : "");
        } else {
            logger.info("[{}] Progress: {} - {}", phase, current,
                    message != null ? message : "");
        }
    }

    @Override
    public void onScanComplete(ScanMetrics metrics, boolean success) {
        if (success) {
            logger.info("=== Scan Completed Successfully ===");
            logger.info("{}", metrics.getSummary());
        } else {
            logger.error("=== Scan Failed ===");
        }
    }

    @Override
    public void onError(String phase, Throwable error) {
        logger.error("Error in phase '{}': {}", phase, error.getMessage(), error);
    }
}
