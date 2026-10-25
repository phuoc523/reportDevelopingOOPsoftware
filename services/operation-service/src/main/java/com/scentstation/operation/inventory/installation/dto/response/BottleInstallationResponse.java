package com.scentstation.operation.inventory.installation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BottleInstallationResponse {

    private Long id;

    private Long tenantId;

    private Long bottleId;

    private Long machineId;

    private Long slotId;

    private Long installedBy;

    private LocalDateTime installedAt;

    private Long removedBy;

    private LocalDateTime removedAt;

    private Boolean active;
}