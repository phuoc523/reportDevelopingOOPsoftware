package com.scentstation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LowStockAlertRequest {
    @NotBlank
    @Size(max = 100)
    private String eventId;

    @NotBlank
    @Size(max = 100)
    private String tenantId;

    @NotNull
    private Long machineId;

    @NotNull
    private Long slotId;

    @Size(max = 500)
    private String summary;

    public LowStockAlertRequest() {
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
