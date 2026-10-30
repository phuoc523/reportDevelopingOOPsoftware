package com.scentstation.service;

import com.scentstation.repository.MachineCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MachineCommandExpiryJob {
    private static final Logger logger = LoggerFactory.getLogger(MachineCommandExpiryJob.class);

    private final MachineCommandRepository commands;

    public MachineCommandExpiryJob(MachineCommandRepository commands) {
        this.commands = commands;
    }

    @Scheduled(fixedDelayString = "${machine.iot.command-expiry-interval-ms:60000}")
    public void expireOverdueCommands() {
        int expiredCount = commands.expireOverdueCommands(Instant.now());
        if (expiredCount > 0) {
            logger.info("Expired {} overdue machine commands", expiredCount);
        }
    }
}
