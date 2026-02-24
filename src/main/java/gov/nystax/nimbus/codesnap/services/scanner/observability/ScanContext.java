package gov.nystax.nimbus.codesnap.services.scanner.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Context for a scan operation, including correlation ID and metrics.
 * Manages MDC (Mapped Diagnostic Context) for structured logging.
 */
public class ScanContext implements AutoCloseable {

    // MDC keys
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_SCAN_PHASE = "scanPhase";
    public static final String MDC_PROJECT_PATH = "projectPath";

    private final String correlationId;
    private final ScanMetrics metrics;
    private final ScanProgressListener progressListener;

    /**
     * Creates a new scan context with a generated correlation ID.
     *
     * @param progressListener The progress listener for this scan
     */
    public ScanContext(ScanProgressListener progressListener) {
        this(UUID.randomUUID().toString(), progressListener);
    }

    /**
     * Creates a new scan context with a specific correlation ID.
     *
     * @param correlationId    The correlation ID
     * @param progressListener The progress listener for this scan
     */
    public ScanContext(String correlationId, ScanProgressListener progressListener) {
        this.correlationId = correlationId;
        this.metrics = new ScanMetrics();
        this.progressListener = progressListener != null ? progressListener : ScanProgressListener.noOp();
        initializeMDC();
    }

    /**
     * Creates a new scan context with default settings.
     *
     * @return A new scan context
     */
    public static ScanContext create() {
        return new ScanContext(ScanProgressListener.noOp());
    }

    /**
     * Creates a new scan context with a logging progress listener.
     *
     * @return A new scan context with logging
     */
    public static ScanContext withLogging() {
        return new ScanContext(ScanProgressListener.loggingListener());
    }

    /**
     * Initializes the MDC with the correlation ID.
     */
    private void initializeMDC() {
        MDC.put(MDC_CORRELATION_ID, correlationId);
    }

    /**
     * Sets the current scan phase in MDC.
     *
     * @param phase The phase name
     */
    public void setPhase(String phase) {
        if (phase != null) {
            MDC.put(MDC_SCAN_PHASE, phase);
        } else {
            MDC.remove(MDC_SCAN_PHASE);
        }
    }

    /**
     * Sets the project path in MDC.
     *
     * @param projectPath The project path
     */
    public void setProjectPath(String projectPath) {
        if (projectPath != null) {
            MDC.put(MDC_PROJECT_PATH, projectPath);
        } else {
            MDC.remove(MDC_PROJECT_PATH);
        }
    }

    /**
     * Gets the correlation ID for this scan.
     *
     * @return The correlation ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the metrics for this scan.
     *
     * @return The scan metrics
     */
    public ScanMetrics getMetrics() {
        return metrics;
    }

    /**
     * Gets the progress listener for this scan.
     *
     * @return The progress listener
     */
    public ScanProgressListener getProgressListener() {
        return progressListener;
    }

    /**
     * Reports phase start to the progress listener and updates MDC.
     *
     * @param phase       The phase name
     * @param description Phase description
     */
    public void phaseStart(String phase, String description) {
        setPhase(phase);
        progressListener.onPhaseStart(phase, description);
    }

    /**
     * Reports phase completion to the progress listener and clears phase from MDC.
     *
     * @param phase   The phase name
     * @param success Whether the phase succeeded
     */
    public void phaseComplete(String phase, boolean success) {
        progressListener.onPhaseComplete(phase, success);
        setPhase(null);
    }

    /**
     * Reports progress to the progress listener.
     *
     * @param phase   The current phase
     * @param current Current progress
     * @param total   Total items (0 if unknown)
     * @param message Progress message
     */
    public void progress(String phase, int current, int total, String message) {
        progressListener.onProgress(phase, current, total, message);
    }

    /**
     * Reports an error to the progress listener.
     *
     * @param phase The phase where error occurred
     * @param error The error
     */
    public void error(String phase, Throwable error) {
        progressListener.onError(phase, error);
    }

    /**
     * Marks the scan as complete and reports to progress listener.
     *
     * @param success Whether the scan succeeded
     */
    public void complete(boolean success) {
        metrics.complete();
        progressListener.onScanComplete(metrics, success);
    }

    /**
     * Clears all MDC values for this scan context.
     * Called automatically when used with try-with-resources.
     */
    @Override
    public void close() {
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_SCAN_PHASE);
        MDC.remove(MDC_PROJECT_PATH);
    }
}
