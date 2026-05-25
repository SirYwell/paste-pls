package de.sirywell.pastepls.paste;

public final class PasteServiceException extends RuntimeException {
    public PasteServiceException(String message) {
        super(message);
    }

    public PasteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
