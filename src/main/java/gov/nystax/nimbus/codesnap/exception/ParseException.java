package gov.nystax.nimbus.codesnap.exception;

public class ParseException extends CodeSnapException {

    public ParseException(String message) {
        super(CodeSnapErrorCategory.PARSE_ERROR, message);
    }

    public ParseException(String message, Throwable cause) {
        super(CodeSnapErrorCategory.PARSE_ERROR, message, cause);
    }
}
