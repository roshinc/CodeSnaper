package gov.nystax.nimbus.codesnap.services.scanner.observability;

/**
 * Listener interface for receiving progress updates during project scanning.
 */
public interface ScanProgressListener {

    /**
     * Creates a no-op progress listener.
     */
    static ScanProgressListener noOp() {
        return new ScanProgressListener() {
        };
    }

    /**
     * Creates a simple logging progress listener.
     */
    static ScanProgressListener loggingListener() {
        return new LoggingProgressListener();
    }

    /**
     * Called when a scan phase begins.
     *
     * @param phase       The name of the phase (e.g., "Cloning Repository", "Analyzing POM", "Analyzing Code")
     * @param description Additional context about the phase
     */
    default void onPhaseStart(String phase, String description) {
        // Default: no-op
    }

    /**
     * Called when a scan phase completes.
     *
     * @param phase   The name of the phase
     * @param success Whether the phase completed successfully
     */
    default void onPhaseComplete(String phase, boolean success) {
        // Default: no-op
    }

    /**
     * Called to report progress within a phase.
     *
     * @param phase   The current phase
     * @param current Current progress (e.g., files processed)
     * @param total   Total items to process (0 if unknown)
     * @param message Optional progress message
     */
    default void onProgress(String phase, int current, int total, String message) {
        // Default: no-op
    }

    /**
     * Called when the scan completes.
     *
     * @param metrics The final metrics from the scan
     * @param success Whether the scan completed successfully
     */
    default void onScanComplete(ScanMetrics metrics, boolean success) {
        // Default: no-op
    }

    /**
     * Called when an error occurs during scanning.
     *
     * @param phase The phase where the error occurred
     * @param error The error that occurred
     */
    default void onError(String phase, Throwable error) {
        // Default: no-op
    }
}
