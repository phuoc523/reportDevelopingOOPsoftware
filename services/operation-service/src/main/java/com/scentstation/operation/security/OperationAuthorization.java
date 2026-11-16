package com.scentstation.operation.security;

import com.scentstation.operation.integration.identity.dto.IdentityPrincipal;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OperationAuthorization {
    public static final String PRINCIPAL_ATTRIBUTE = OperationAuthorization.class.getName() + ".principal";
    public static final String INVENTORY_READ = "INVENTORY_READ";
    public static final String INVENTORY_MANAGE = "INVENTORY_MANAGE";
    public static final String INVENTORY_ADJUST = "INVENTORY_ADJUST";
    public static final String MAINTENANCE_READ = "MAINTENANCE_READ";
    public static final String MAINTENANCE_MANAGE = "MAINTENANCE_MANAGE";

    private final MaintenanceTicketRepository tickets;

    public OperationAuthorization(MaintenanceTicketRepository tickets) {
        this.tickets = tickets;
    }

    public void requirePermission(IdentityPrincipal actor, String permission) {
        if (!actor.grants().contains(permission)) {
            throw forbidden("Permission required: " + permission);
        }
    }

    public void requireTenant(IdentityPrincipal actor, Long tenantId) {
        if (tenantId == null || !tenantId.equals(actor.tenantId())) {
            throw forbidden("Tenant access is not allowed");
        }
    }

    public MaintenanceTicket requireTicket(IdentityPrincipal actor, Long ticketId) {
        MaintenanceTicket ticket = tickets.findByIdAndTenantId(ticketId, actor.tenantId())
                .orElseThrow(() -> forbidden("Maintenance ticket access is not allowed"));
        requireMachineScope(actor, ticket.getMachineId(), ticket.getLocationId());
        return ticket;
    }

    public void requireMachineScope(IdentityPrincipal actor, Long machineId, Long locationId) {
        if (technician(actor) && actor.machineIds().isEmpty()) {
            throw forbidden("Technician machine scope is empty");
        }
        if (!actor.machineIds().isEmpty() && !actor.machineIds().contains(machineId)) {
            throw forbidden("Machine access is not allowed");
        }
        if (!actor.locationIds().isEmpty()
                && (locationId == null || !actor.locationIds().contains(locationId))) {
            throw forbidden("Location access is not allowed");
        }
    }

    public List<MaintenanceTicketResponse> filterTickets(
            IdentityPrincipal actor, List<MaintenanceTicketResponse> values
    ) {
        return values.stream().filter(ticket -> allowed(actor, ticket.getMachineId(), ticket.getLocationId())).toList();
    }

    private boolean allowed(IdentityPrincipal actor, Long machineId, Long locationId) {
        try {
            requireMachineScope(actor, machineId, locationId);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private boolean technician(IdentityPrincipal actor) {
        return actor.roles().contains("TECHNICIAN");
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}
