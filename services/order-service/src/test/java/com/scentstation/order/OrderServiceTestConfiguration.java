package com.scentstation.order;

import com.scentstation.order.client.MachineCommandRequest;
import com.scentstation.order.client.MachineCommandResponse;
import com.scentstation.order.client.MachineServiceGateway;
import com.scentstation.order.client.MachineAuthorityGateway;
import com.scentstation.order.client.MachineSlotSnapshot;
import com.scentstation.order.client.CatalogServiceGateway;
import com.scentstation.order.client.CatalogProductSnapshot;
import java.math.BigDecimal;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.integration.identity.client.IdentityIntrospectionClient;
import com.scentstation.order.integration.identity.dto.IdentityPrincipal;
import java.util.Set;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@TestConfiguration
public class OrderServiceTestConfiguration {

    @Bean
    MockMvcBuilderCustomizer authenticatedRequestsByDefault() {
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").header("Authorization", "Bearer test-all")
        );
    }

    @Bean
    @Primary
    IdentityIntrospectionClient testIdentityIntrospectionClient(RestClient.Builder builder) {
        return new IdentityIntrospectionClient(builder, "http://127.0.0.1:1", "test-key") {
            @Override
            public IdentityPrincipal introspect(String token) {
                return switch (token) {
                    case "test-all" -> principal(7L, 1L,
                            Set.of("KIOSK_ORDER", "ORDER_READ", "ORDER_REVIEW"));
                    case "customer" -> principal(7L, 1L, Set.of("KIOSK_ORDER"));
                    case "customer-8" -> principal(8L, 1L, Set.of("KIOSK_ORDER"));
                    case "staff-read" -> principal(20L, 1L, Set.of("ORDER_READ"));
                    case "staff-review" -> principal(21L, 1L, Set.of("ORDER_REVIEW"));
                    case "tenant-2" -> principal(30L, 2L,
                            Set.of("KIOSK_ORDER", "ORDER_READ", "ORDER_REVIEW"));
                    case "no-permissions" -> principal(40L, 1L, Set.of());
                    case "unavailable" -> throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable");
                    case "malformed" -> throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Invalid Identity response");
                    default -> throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Invalid or revoked bearer token");
                };
            }
        };
    }

    private IdentityPrincipal principal(Long id, Long tenantId, Set<String> grants) {
        return new IdentityPrincipal(id, "test-user-" + id, tenantId, Set.of("TEST"), grants,
                Set.of(), Set.of());
    }

    @Bean
    @Primary
    MachineServiceGateway testMachineServiceGateway(DispensingRepository dispensingRepository) {
        return new MachineServiceGateway() {
            @Override
            public MachineCommandResponse submit(MachineCommandRequest request) {
                return new MachineCommandResponse(
                        request.orderId(), request.commandId(), "ACCEPTED", "Accepted in test"
                );
            }

            @Override
            public MachineCommandResponse getResult(String commandId) {
                var command = dispensingRepository.findByCommandId(commandId).orElseThrow();
                return new MachineCommandResponse(
                        command.getOrderId(), commandId, "DISPENSED", "Dispensed in test"
                );
            }
        };
    }

    @Bean
    @Primary
    CatalogServiceGateway testCatalogServiceGateway() {
        return (tenantId, productId) -> switch ((int) productId) {
            case 404 -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            case 4 -> new CatalogProductSnapshot(productId, tenantId, "Inactive", false,
                    new BigDecimal("10.00"), null);
            case 5 -> new CatalogProductSnapshot(productId, tenantId + 1, "Foreign", true,
                    new BigDecimal("10.00"), null);
            case 6 -> new CatalogProductSnapshot(productId, tenantId, "Promoted", true,
                    new BigDecimal("9.00"), 99L);
            default -> new CatalogProductSnapshot(productId, tenantId, "Scent Sample", true,
                    new BigDecimal("12.75"), null);
        };
    }

    @Bean
    @Primary
    MachineAuthorityGateway testMachineAuthorityGateway() {
        return (tenantId, machineId, slotId) -> {
            if (machineId == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found");
            if (slotId == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");
            return new MachineSlotSnapshot(machineId, Long.toString(tenantId), slotId,
                    machineId == 16 ? "6" : slotId == 22 ? "999" : "3",
                    machineId != 11, slotId != 21, "Unavailable");
        };
    }
}
