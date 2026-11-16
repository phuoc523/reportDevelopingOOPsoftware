package com.scentstation.catalog.exception;

public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(Long campaignId, Long tenantId) {
        super(
                "Campaign not found with id "
                        + campaignId
                        + " for tenant "
                        + tenantId
        );
    }
}