package com.scentstation.catalog.exception;

public class MachineAvailabilityReferenceMismatchException extends RuntimeException {

    public MachineAvailabilityReferenceMismatchException() {
        super("Machine availability response does not match requested tenant, machine, or product");
    }

    
}