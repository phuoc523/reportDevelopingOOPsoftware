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
}