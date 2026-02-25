package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

/**
 * Constants used across analyzer classes.
 */
public final class AnalyzerConstants {

    // ============================================================================
    // Annotations
    // ============================================================================

    /**
     * Fully qualified name of the @SmartService annotation.
     */
    public static final String SERVICE_ANNOTATION = "gov.nystax.nimbus.smart.SmartService";

    /**
     * Fully qualified name of the @SmartImpl annotation.
     */
    public static final String SERVICE_IMPL_ANNOTATION = "gov.nystax.nimbus.smart.SmartImpl";

    /**
     * Fully qualified name of the @UIService annotation.
     */
    public static final String UI_SERVICE_ANNOTATION = "gov.nystax.nimbus.smart.UIService";

    /**
     * Fully qualified name of the @Function annotation.
     */
    public static final String FUNCTION_ANNOTATION = "gov.nystax.nimbus.smart.Function";

    /**
     * Fully qualified name of the @CTGClient annotation.
     */
    public static final String CTG_CLIENT_ANNOTATION = "gov.nystax.nimbus.ctg.CTGClient";

    // ============================================================================
    // Interfaces
    // ============================================================================

    /**
     * Fully qualified name of the IEventPublisher interface.
     */
    public static final String EVENT_PUBLISHER_INTERFACE = "gov.nystax.nimbus.eda.publisher.service.IEventPublisher";

    /**
     * Fully qualified name of the ICTGClient interface.
     */
    public static final String CTG_CLIENT_INTERFACE = "gov.nystax.nimbus.ctg.ICTGClient";

    // ============================================================================
    // Classes
    // ============================================================================

    /**
     * Fully qualified name of the IEventPublisher interface.
     */
    public static final String LGHC_CLASS = "gov.nystax.nimbus.lghc.LegacyGatewayHttpClient";

    // ============================================================================
    // Package prefixes
    // ============================================================================

    /**
     * Group ID for service dependencies.
     */
    public static final String SERVICE_GROUP_ID = "gov.nystax.services";

    /**
     * Group ID for function dependencies.
     */
    public static final String FUNCTION_GROUP_ID = "gov.nystax.functions";

    /**
     * Package prefix for function classes.
     */
    public static final String FUNCTION_PACKAGE_PREFIX = "gov.nystax.nimbus.function.client";
    /**
     * Package prefix for service classes.
     */
    public static final String SERVICE_PACKAGE_PREFIX = SERVICE_GROUP_ID;


    private AnalyzerConstants() {
    }
}
