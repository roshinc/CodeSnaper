package gov.nystax.nimbus.codesnap.exception;

public class ProcessingException extends CodeSnapException {

    public ProcessingException(String message) {
        super(CodeSnapErrorCategory.PROCESSING_ERROR, message);
    }

    public ProcessingException(String message, Throwable cause) {
        super(CodeSnapErrorCategory.PROCESSING_ERROR, message, cause);
    }
}
