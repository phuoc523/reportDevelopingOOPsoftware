package com.scentstation.order.client;

public interface MachineServiceGateway {

    MachineCommandResponse submit(MachineCommandRequest request);

    MachineCommandResponse getResult(String commandId);
}
