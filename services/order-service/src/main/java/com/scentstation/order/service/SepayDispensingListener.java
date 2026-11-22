package com.scentstation.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SepayDispensingListener {
    private static final Logger LOG = LoggerFactory.getLogger(SepayDispensingListener.class);
    private final DispensingService dispensing;

    public SepayDispensingListener(DispensingService dispensing) {
        this.dispensing = dispensing;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void dispatch(SepaySettlement.ConfirmedTrial trial) {
        try {
            dispensing.dispatchPreparedTrial(trial.orderId());
        } catch (RuntimeException exception) {
            LOG.error("SePay payment committed but order {} needs dispensing review", trial.orderId());
        }
    }
}
