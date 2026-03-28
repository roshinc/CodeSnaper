package gov.nystax.nimbus.codesnap.exception;

public class CloneException extends CodeSnapException {

    public CloneException(String message) {
        super(CodeSnapErrorCategory.CLONE_ERROR, message);
    }

    public CloneException(String message, Throwable cause) {
        super(CodeSnapErrorCategory.CLONE_ERROR, message, cause);
    }
}
