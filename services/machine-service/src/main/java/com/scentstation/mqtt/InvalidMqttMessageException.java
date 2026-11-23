package com.scentstation.mqtt;

public class InvalidMqttMessageException extends RuntimeException {
    public InvalidMqttMessageException(String message) {
        super(message);
    }

    public InvalidMqttMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
