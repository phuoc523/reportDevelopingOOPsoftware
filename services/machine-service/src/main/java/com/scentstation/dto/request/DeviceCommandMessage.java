package com.scentstation.dto.request;

import java.time.Instant;
import java.util.Map;

public class DeviceCommandMessage {
    private final String commandId;
    private final String deviceIdentity;
    private final String tenantId;
    private final String commandType;
    private final Map<String, Object> parameters;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final String source;

    public DeviceCommandMessage(String commandId, String deviceIdentity, String tenantId,
                                String commandType, Map<String, Object> parameters,
                                Instant issuedAt, Instant expiresAt) {
        this.commandId = commandId;
        this.deviceIdentity = deviceIdentity;
        this.tenantId = tenantId;
        this.commandType = commandType;
        this.parameters = parameters;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.source = "machine-service";
    }

    public String getCommandId() { return commandId; }
    public String getDeviceIdentity() { return deviceIdentity; }
    public String getTenantId() { return tenantId; }
    public String getCommandType() { return commandType; }
    public Map<String, Object> getParameters() { return parameters; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getSource() { return source; }
}
