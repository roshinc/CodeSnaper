package gov.nystax.nimbus.codesnap.services.scanner.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Metrics collected during a project scan.
 * Provides timing information, counts, and performance data.
 */
public class ScanMetrics {

    private final Instant startTime;
    private Instant endTime;

    // Duration metrics
    private Duration cloneDuration;
    private Duration pomAnalysisDuration;
    private Duration codeAnalysisDuration;
    private Duration totalDuration;

    // Count metrics
    private int sourceFilesScanned;
    private int typesAnalyzed;
    private int methodsAnalyzed;
    private int invocationsAnalyzed;
    private int dependenciesFound;

    // Size metrics
    private long projectSizeBytes;
    private int directoryDepth;

    public ScanMetrics() {
        this.startTime = Instant.now();
    }

    /**
     * Marks the scan as completed and calculates total duration.
     */
    public void complete() {
        this.endTime = Instant.now();
        this.totalDuration = Duration.between(startTime, endTime);
    }

    // ============================================================================
    // Getters and Setters
    // ============================================================================

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Duration getCloneDuration() {
        return cloneDuration;
    }

    public void setCloneDuration(Duration cloneDuration) {
        this.cloneDuration = cloneDuration;
    }

    public Duration getPomAnalysisDuration() {
        return pomAnalysisDuration;
    }

    public void setPomAnalysisDuration(Duration pomAnalysisDuration) {
        this.pomAnalysisDuration = pomAnalysisDuration;
    }

    public Duration getCodeAnalysisDuration() {
        return codeAnalysisDuration;
    }

    public void setCodeAnalysisDuration(Duration codeAnalysisDuration) {
        this.codeAnalysisDuration = codeAnalysisDuration;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }

    public int getSourceFilesScanned() {
        return sourceFilesScanned;
    }

    public void setSourceFilesScanned(int sourceFilesScanned) {
        this.sourceFilesScanned = sourceFilesScanned;
    }

    public int getTypesAnalyzed() {
        return typesAnalyzed;
    }

    public void setTypesAnalyzed(int typesAnalyzed) {
        this.typesAnalyzed = typesAnalyzed;
    }

    public int getMethodsAnalyzed() {
        return methodsAnalyzed;
    }

    public void setMethodsAnalyzed(int methodsAnalyzed) {
        this.methodsAnalyzed = methodsAnalyzed;
    }

    public int getInvocationsAnalyzed() {
        return invocationsAnalyzed;
    }

    public void setInvocationsAnalyzed(int invocationsAnalyzed) {
        this.invocationsAnalyzed = invocationsAnalyzed;
    }

    public int getDependenciesFound() {
        return dependenciesFound;
    }

    public void setDependenciesFound(int dependenciesFound) {
        this.dependenciesFound = dependenciesFound;
    }

    public long getProjectSizeBytes() {
        return projectSizeBytes;
    }

    public void setProjectSizeBytes(long projectSizeBytes) {
        this.projectSizeBytes = projectSizeBytes;
    }

    public int getDirectoryDepth() {
        return directoryDepth;
    }

    public void setDirectoryDepth(int directoryDepth) {
        this.directoryDepth = directoryDepth;
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Gets the total duration in milliseconds.
     */
    public long getTotalDurationMillis() {
        return totalDuration != null ? totalDuration.toMillis() : 0;
    }

    /**
     * Gets a human-readable summary of the metrics.
     */
    public String getSummary() {
        return String.format(
                "Scan Metrics: total=%dms, clone=%dms, pom=%dms, code=%dms, " +
                        "files=%d, types=%d, methods=%d, invocations=%d, dependencies=%d",
                getTotalDurationMillis(),
                cloneDuration != null ? cloneDuration.toMillis() : 0,
                pomAnalysisDuration != null ? pomAnalysisDuration.toMillis() : 0,
                codeAnalysisDuration != null ? codeAnalysisDuration.toMillis() : 0,
                sourceFilesScanned,
                typesAnalyzed,
                methodsAnalyzed,
                invocationsAnalyzed,
                dependenciesFound
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanMetrics that = (ScanMetrics) o;
        return sourceFilesScanned == that.sourceFilesScanned &&
                typesAnalyzed == that.typesAnalyzed &&
                methodsAnalyzed == that.methodsAnalyzed &&
                invocationsAnalyzed == that.invocationsAnalyzed &&
                dependenciesFound == that.dependenciesFound &&
                projectSizeBytes == that.projectSizeBytes &&
                directoryDepth == that.directoryDepth &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime) &&
                Objects.equals(cloneDuration, that.cloneDuration) &&
                Objects.equals(pomAnalysisDuration, that.pomAnalysisDuration) &&
                Objects.equals(codeAnalysisDuration, that.codeAnalysisDuration) &&
                Objects.equals(totalDuration, that.totalDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime, cloneDuration, pomAnalysisDuration,
                codeAnalysisDuration, totalDuration, sourceFilesScanned,
                typesAnalyzed, methodsAnalyzed, invocationsAnalyzed,
                dependenciesFound, projectSizeBytes, directoryDepth);
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
