package com.scentstation.reporting.client;

public class UpstreamUnavailableException extends RuntimeException {

    private final String serviceName;

    public UpstreamUnavailableException(String serviceName, Throwable cause) {
        super("Cannot communicate with " + serviceName, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
