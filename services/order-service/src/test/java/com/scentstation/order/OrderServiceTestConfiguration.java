package com.scentstation.order;

import com.scentstation.order.client.MachineCommandRequest;
import com.scentstation.order.client.MachineCommandResponse;
import com.scentstation.order.client.MachineServiceGateway;
import com.scentstation.order.repository.DispensingRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class OrderServiceTestConfiguration {

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
}
