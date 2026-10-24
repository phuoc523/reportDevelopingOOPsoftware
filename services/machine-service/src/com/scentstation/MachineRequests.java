package com.scentstation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public final class MachineRequests {
    private MachineRequests() {
    }

    public static class CreateMachineRequest {
        @NotBlank
        @Size(max = 100)
        private String serialNumber;

        @NotBlank
        @Size(max = 150)
        private String deviceIdentity;

        @NotBlank
        @Size(max = 100)
        private String tenantId;

        @NotBlank
        @Size(max = 100)
        private String locationId;

        @NotBlank
        @Size(max = 150)
        private String name;

        public CreateMachineRequest() {
        }

        public CreateMachineRequest(String serialNumber, String deviceIdentity, String tenantId,
                                    String locationId, String name) {
            this.serialNumber = serialNumber;
            this.deviceIdentity = deviceIdentity;
            this.tenantId = tenantId;
            this.locationId = locationId;
            this.name = name;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public void setSerialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
        }

        public String getDeviceIdentity() {
            return deviceIdentity;
        }

        public void setDeviceIdentity(String deviceIdentity) {
            this.deviceIdentity = deviceIdentity;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getLocationId() {
            return locationId;
        }

        public void setLocationId(String locationId) {
            this.locationId = locationId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class CreateSlotRequest {
        @NotNull
        @Min(1)
        private Integer slotNumber;

        @NotBlank
        @Size(max = 50)
        private String slotCode;

        @Size(max = 100)
        private String productId;

        @Size(max = 100)
        private String cartridgeId;

        @NotNull
        private Boolean enabled;

        @NotNull
        @Min(0)
        private Integer stockThreshold;

        @NotNull
        @Min(1)
        @Max(5000)
        private Integer calibratedSprayDurationMs;

        @Positive
        private Double doseMl;

        @NotNull
        @DecimalMin("0.00")
        private BigDecimal price;

        public CreateSlotRequest() {
        }

        public CreateSlotRequest(Integer slotNumber, String slotCode, String productId,
                                 String cartridgeId, Boolean enabled, Integer stockThreshold,
                                 Integer calibratedSprayDurationMs, Double doseMl, BigDecimal price) {
            this.slotNumber = slotNumber;
            this.slotCode = slotCode;
            this.productId = productId;
            this.cartridgeId = cartridgeId;
            this.enabled = enabled;
            this.stockThreshold = stockThreshold;
            this.calibratedSprayDurationMs = calibratedSprayDurationMs;
            this.doseMl = doseMl;
            this.price = price;
        }

        public Integer getSlotNumber() {
            return slotNumber;
        }

        public void setSlotNumber(Integer slotNumber) {
            this.slotNumber = slotNumber;
        }

        public String getSlotCode() {
            return slotCode;
        }

        public void setSlotCode(String slotCode) {
            this.slotCode = slotCode;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getCartridgeId() {
            return cartridgeId;
        }

        public void setCartridgeId(String cartridgeId) {
            this.cartridgeId = cartridgeId;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getStockThreshold() {
            return stockThreshold;
        }

        public void setStockThreshold(Integer stockThreshold) {
            this.stockThreshold = stockThreshold;
        }

        public Integer getCalibratedSprayDurationMs() {
            return calibratedSprayDurationMs;
        }

        public void setCalibratedSprayDurationMs(Integer calibratedSprayDurationMs) {
            this.calibratedSprayDurationMs = calibratedSprayDurationMs;
        }

        public Double getDoseMl() {
            return doseMl;
        }

        public void setDoseMl(Double doseMl) {
            this.doseMl = doseMl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }

    public static class MachineControlRequest {
        @NotNull
        private ControlAction action;

        public MachineControlRequest() {
        }

        public MachineControlRequest(ControlAction action) {
            this.action = action;
        }

        public ControlAction getAction() {
            return action;
        }

        public void setAction(ControlAction action) {
            this.action = action;
        }

        public enum ControlAction {
            ENABLE,
            DISABLE,
            ENTER_MAINTENANCE
        }
    }

    public static class UpdateDeviceRegistrationStatusRequest {
        @NotNull
        private DeviceRegistrationStatus status;

        public UpdateDeviceRegistrationStatusRequest() {
        }

        public UpdateDeviceRegistrationStatusRequest(DeviceRegistrationStatus status) {
            this.status = status;
        }

        public DeviceRegistrationStatus getStatus() {
            return status;
        }

        public void setStatus(DeviceRegistrationStatus status) {
            this.status = status;
        }
    }

    public static class UpdateMachineRequest {
        @NotBlank
        @Size(max = 100)
        private String tenantId;

        @NotBlank
        @Size(max = 100)
        private String locationId;

        @NotBlank
        @Size(max = 150)
        private String name;

        public UpdateMachineRequest() {
        }

        public UpdateMachineRequest(String tenantId, String locationId, String name) {
            this.tenantId = tenantId;
            this.locationId = locationId;
            this.name = name;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getLocationId() {
            return locationId;
        }

        public void setLocationId(String locationId) {
            this.locationId = locationId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class UpdateOperatingHoursRequest {
        private LocalTime startTime;

        private LocalTime endTime;

        public UpdateOperatingHoursRequest() {
        }

        public UpdateOperatingHoursRequest(LocalTime startTime, LocalTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalTime startTime) {
            this.startTime = startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalTime endTime) {
            this.endTime = endTime;
        }
    }

    public static class UpdateOperationalStatusRequest {
        @NotNull
        private MachineOperationalStatus status;

        public UpdateOperationalStatusRequest() {
        }

        public UpdateOperationalStatusRequest(MachineOperationalStatus status) {
            this.status = status;
        }

        public MachineOperationalStatus getStatus() {
            return status;
        }

        public void setStatus(MachineOperationalStatus status) {
            this.status = status;
        }
    }

    public static class UpdateSlotEnabledRequest {
        @NotNull
        private Boolean enabled;

        public UpdateSlotEnabledRequest() {
        }

        public UpdateSlotEnabledRequest(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class UpdateSlotRequest {
        @Size(max = 100)
        private String productId;

        @Size(max = 100)
        private String cartridgeId;

        @NotNull
        private Boolean enabled;

        @NotNull
        @Min(0)
        private Integer stockThreshold;

        @NotNull
        @Min(1)
        @Max(5000)
        private Integer calibratedSprayDurationMs;

        @Positive
        private Double doseMl;

        @NotNull
        @DecimalMin("0.00")
        private BigDecimal price;

        public UpdateSlotRequest() {
        }

        public UpdateSlotRequest(String productId, String cartridgeId, Boolean enabled,
                                 Integer stockThreshold, Integer calibratedSprayDurationMs,
                                 Double doseMl, BigDecimal price) {
            this.productId = productId;
            this.cartridgeId = cartridgeId;
            this.enabled = enabled;
            this.stockThreshold = stockThreshold;
            this.calibratedSprayDurationMs = calibratedSprayDurationMs;
            this.doseMl = doseMl;
            this.price = price;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getCartridgeId() {
            return cartridgeId;
        }

        public void setCartridgeId(String cartridgeId) {
            this.cartridgeId = cartridgeId;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getStockThreshold() {
            return stockThreshold;
        }

        public void setStockThreshold(Integer stockThreshold) {
            this.stockThreshold = stockThreshold;
        }

        public Integer getCalibratedSprayDurationMs() {
            return calibratedSprayDurationMs;
        }

        public void setCalibratedSprayDurationMs(Integer calibratedSprayDurationMs) {
            this.calibratedSprayDurationMs = calibratedSprayDurationMs;
        }

        public Double getDoseMl() {
            return doseMl;
        }

        public void setDoseMl(Double doseMl) {
            this.doseMl = doseMl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}
