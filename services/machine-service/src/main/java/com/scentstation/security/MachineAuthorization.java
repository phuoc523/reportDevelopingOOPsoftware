package com.scentstation.security;

import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineAlert;
import com.scentstation.integration.identity.dto.IdentityPrincipal;
import com.scentstation.repository.MachineRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MachineAuthorization {
    public static final String MACHINE_READ = "MACHINE_READ";
    public static final String MACHINE_CONTROL = "MACHINE_CONTROL";
    public static final String MACHINE_CONFIGURE = "MACHINE_CONFIGURE";
    public static final String MAINTENANCE_READ = "MAINTENANCE_READ";
    public static final String MAINTENANCE_MANAGE = "MAINTENANCE_MANAGE";
    public static final String DIAGNOSTIC_SPRAY = "DIAGNOSTIC_SPRAY";
    public static final String ALERT_READ = "ALERT_READ";
    public static final String ALERT_MANAGE = "ALERT_MANAGE";
    public static final String DEVICE_ACCESS_REVOKE = "DEVICE_ACCESS_REVOKE";

    private final MachineRepository machines;

    public MachineAuthorization(MachineRepository machines) {
        this.machines = machines;
    }

    public IdentityPrincipal require(IdentityPrincipal actor, String permission) {
        if (!actor.grants().contains(permission)) {
            throw forbidden("Permission required: " + permission);
        }
        tenant(actor);
        return actor;
    }

    public String tenant(IdentityPrincipal actor) {
        if (actor.tenantId() == null) {
            throw forbidden("A tenant-scoped actor is required");
        }
        return MachineIdentifiers.actorTenant(actor.tenantId());
    }

    public void requireTenant(IdentityPrincipal actor, String tenantId) {
        long requestedTenant = MachineIdentifiers.requestTenant(tenantId);
        if (actor.tenantId() == null || actor.tenantId() != requestedTenant) {
            throw forbidden("Tenant access is not allowed");
        }
    }

    public void requireMachineCreation(IdentityPrincipal actor) {
        if (technician(actor)) {
            throw forbidden("Technicians may access only already assigned machines");
        }
    }

    public void requireLocation(IdentityPrincipal actor, String locationId) {
        if (actor.locationIds().isEmpty()) {
            if (technician(actor)) {
                throw forbidden("Technician location scope is empty");
            }
            return;
        }
        Long numericId = MachineIdentifiers.exactNumericScope(locationId);
        if (numericId == null || !actor.locationIds().contains(numericId)) {
            throw forbidden("Location access is not allowed");
        }
    }

    public Machine requireMachine(IdentityPrincipal actor, Long machineId, String permission) {
        MachineIdentifiers.requirePositive(machineId, "machineId");
        require(actor, permission);
        Machine machine = machines.findById(machineId)
            .orElseThrow(() -> forbidden("Machine access is not allowed"));
        requirePersistedTenant(actor, machine.getTenantId());
        requireMachineScope(actor, machine);
        return machine;
    }

    public Machine requireMachineForRevocation(IdentityPrincipal actor, Long machineId) {
        MachineIdentifiers.requirePositive(machineId, "machineId");
        if (!actor.grants().contains(DEVICE_ACCESS_REVOKE)) {
            throw forbidden("Permission required: " + DEVICE_ACCESS_REVOKE);
        }
        Machine machine = machines.findById(machineId)
            .orElseThrow(() -> forbidden("Machine access is not allowed"));
        MachineIdentifiers.storedTenant(machine.getTenantId());
        if (actor.tenantId() != null) {
            requirePersistedTenant(actor, machine.getTenantId());
        }
        requireMachineScope(actor, machine);
        return machine;
    }

    public List<Machine> listMachines(IdentityPrincipal actor, String permission) {
        require(actor, permission);
        return machines.findAllByTenantIdOrderByIdAsc(tenant(actor)).stream()
            .filter(machine -> allowed(actor, machine)).toList();
    }

    public void requireAlert(IdentityPrincipal actor, MachineAlert alert, String permission) {
        require(actor, permission);
        requirePersistedTenant(actor, alert.getTenantId());
        Machine machine = machines.findById(alert.getMachineId())
            .orElseThrow(() -> forbidden("Alert access is not allowed"));
        requirePersistedTenant(actor, machine.getTenantId());
        requireMachineScope(actor, machine);
    }

    public boolean allowed(IdentityPrincipal actor, Machine machine) {
        try {
            requireMachineScope(actor, machine);
            requirePersistedTenant(actor, machine.getTenantId());
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private void requireMachineScope(IdentityPrincipal actor, Machine machine) {
        if (technician(actor) && actor.machineIds().isEmpty()) {
            throw forbidden("Technician machine scope is empty");
        }
        if (!actor.machineIds().isEmpty() && !actor.machineIds().contains(machine.getId())) {
            throw forbidden("Machine access is not allowed");
        }
        if (!actor.locationIds().isEmpty()) {
            requireLocation(actor, machine.getLocationId());
        }
    }

    private boolean technician(IdentityPrincipal actor) {
        return actor.roles().contains("TECHNICIAN");
    }

    private void requirePersistedTenant(IdentityPrincipal actor, String tenantId) {
        long storedTenant = MachineIdentifiers.storedTenant(tenantId);
        if (actor.tenantId() == null || actor.tenantId() != storedTenant) {
            throw forbidden("Tenant access is not allowed");
        }
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}
