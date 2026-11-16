package com.scentstation.notification.exception;

public class DuplicateNotificationEventException extends RuntimeException {

    public DuplicateNotificationEventException() {
        super("A notification already exists for this source event");
    }
}
