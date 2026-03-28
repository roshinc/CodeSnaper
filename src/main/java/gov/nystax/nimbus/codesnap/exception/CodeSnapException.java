package gov.nystax.nimbus.codesnap.exception;

public abstract class CodeSnapException extends RuntimeException {

    private final CodeSnapErrorCategory category;

    protected CodeSnapException(CodeSnapErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    protected CodeSnapException(CodeSnapErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public CodeSnapErrorCategory getCategory() {
        return category;
    }

    public boolean is(CodeSnapErrorCategory type) {
        return this.category == type;
    }
}
