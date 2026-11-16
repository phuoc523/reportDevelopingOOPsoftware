package com.scentstation.mqtt;

import com.scentstation.entity.Machine;

public record ValidatedDeviceMessage(
    String topic,
    DeviceMessageEnvelope envelope,
    Machine machine,
    String tenantId
) {
}
