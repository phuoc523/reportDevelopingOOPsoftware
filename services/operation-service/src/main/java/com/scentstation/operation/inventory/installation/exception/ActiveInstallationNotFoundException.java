package com.scentstation.operation.inventory.installation.exception;

public class ActiveInstallationNotFoundException extends RuntimeException {

    public ActiveInstallationNotFoundException(
            Long bottleId,
            Long tenantId
    ) {
        super(
                "No active installation found for bottle "
                        + bottleId
                        + " in tenant "
                        + tenantId
        );
    }

    public ActiveInstallationNotFoundException(Long tenantId, Long machineId, Long slotId) {
        super("No active bottle installation found for machine " + machineId
                + " slot " + slotId + " in tenant " + tenantId);
    }
}
