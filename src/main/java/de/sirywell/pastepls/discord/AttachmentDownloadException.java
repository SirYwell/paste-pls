package de.sirywell.pastepls.discord;

public final class AttachmentDownloadException extends RuntimeException {
    public AttachmentDownloadException(String message) {
        super(message);
    }

    public AttachmentDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
