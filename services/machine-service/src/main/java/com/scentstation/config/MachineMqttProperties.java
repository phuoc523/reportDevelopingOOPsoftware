package com.scentstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "machine.mqtt")
public class MachineMqttProperties {
    private boolean enabled;
    private String brokerUrl = "tcp://localhost:1883";
    private String username = "";
    private String password = "";
    private String clientId = "machine-service";
    @Min(1)
    private int connectionTimeoutSeconds = 10;
    @Min(1)
    private int keepAliveIntervalSeconds = 30;
    @Min(1)
    private long reconnectIntervalMs = 5000;
    @Min(1)
    private int maxPayloadBytes = 65536;
    @Min(0)
    private long allowedClockSkewSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }
    public int getKeepAliveIntervalSeconds() { return keepAliveIntervalSeconds; }
    public void setKeepAliveIntervalSeconds(int keepAliveIntervalSeconds) {
        this.keepAliveIntervalSeconds = keepAliveIntervalSeconds;
    }
    public long getReconnectIntervalMs() { return reconnectIntervalMs; }
    public void setReconnectIntervalMs(long reconnectIntervalMs) {
        this.reconnectIntervalMs = reconnectIntervalMs;
    }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public void setMaxPayloadBytes(int maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }
    public long getAllowedClockSkewSeconds() { return allowedClockSkewSeconds; }
    public void setAllowedClockSkewSeconds(long allowedClockSkewSeconds) {
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
    }
}
