package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

/**
 * Configuration for how service interface/implementation pairs are resolved.
 *
 * @param lenientPairMatch if a valid SmartImpl→SmartService pair exists among multiple annotated types, use it
 * @param inferImpl        if only SmartService found, search all classes for an implementor
 * @param inferInterface   if only SmartImpl found, derive the interface from the impl's super-interfaces
 */
public record ServiceResolutionConfig(
        boolean lenientPairMatch,
        boolean inferImpl,
        boolean inferInterface
) {
    /**
     * Strict mode: requires exactly one @SmartService interface and one @SmartImpl class.
     */
    public static final ServiceResolutionConfig STRICT = new ServiceResolutionConfig(false, false, false);
}
