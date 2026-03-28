package gov.nystax.nimbus.codesnap.exception;

public class ScanException extends CodeSnapException {

    public ScanException(String message) {
        super(CodeSnapErrorCategory.SCAN_ERROR, message);
    }

    public ScanException(String message, Throwable cause) {
        super(CodeSnapErrorCategory.SCAN_ERROR, message, cause);
    }
}
