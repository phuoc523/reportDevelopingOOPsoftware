package com.scentstation.catalog.exception;

public class InvalidCampaignPeriodException extends RuntimeException {

    public InvalidCampaignPeriodException() {
        super("Campaign startAt must be before endAt");
    }
}