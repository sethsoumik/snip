package in.soumikseth.links.exception;

/** Thrown when a requested short code does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
