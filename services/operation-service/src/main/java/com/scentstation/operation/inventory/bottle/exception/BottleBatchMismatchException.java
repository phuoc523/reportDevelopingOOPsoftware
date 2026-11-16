package com.scentstation.operation.inventory.bottle.exception;

public class BottleBatchMismatchException extends RuntimeException {

    public BottleBatchMismatchException(
            Long productId,
            Long batchProductId
    ) {
        super(
                "Bottle product "
                        + productId
                        + " does not match batch product "
                        + batchProductId
        );
    }
}