package com.scentstation.operation.inventory.installation.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.response.BottleInstallationResponse;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.exception.BottleAlreadyInstalledException;
import com.scentstation.operation.inventory.installation.exception.BottleNotInstallableException;
import com.scentstation.operation.inventory.installation.exception.SlotAlreadyOccupiedException;
import com.scentstation.operation.inventory.installation.mapper.BottleInstallationMapper;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.scentstation.operation.inventory.installation.dto.request.RemoveBottleRequest;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.dto.request.ReplaceBottleRequest;

import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
public class BottleInstallationService {

    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository bottleInstallationRepository;
    private final BottleMovementRepository bottleMovementRepository;
    private final BottleInstallationMapper bottleInstallationMapper;

    @Transactional
    public BottleInstallationResponse installBottle(
            InstallBottleRequest request
    ) {
        Bottle bottle = getBottle(
                request.getTenantId(),
                request.getBottleId()
        );

        validateBottleStatus(bottle);

        validateBottleNotAlreadyInstalled(
                request.getTenantId(),
                request.getBottleId()
        );

        validateSlotAvailable(
                request.getTenantId(),
                request.getMachineId(),
                request.getSlotId()
        );

        BottleInstallation installation =
                bottleInstallationMapper.toEntity(request);

        BottleInstallation savedInstallation =
                bottleInstallationRepository.save(installation);

        bottle.setStatus(BottleStatus.INSTALLED);
        bottleRepository.save(bottle);

        saveMovement(
                request,
                bottle
        );

        return bottleInstallationMapper.toResponse(
                savedInstallation
        );
    }

    @Transactional
    public BottleInstallationResponse removeBottle(
            RemoveBottleRequest request
    ) {
        Bottle bottle = getBottle(
                request.getTenantId(),
                request.getBottleId()
        );

        BottleInstallation installation =
                bottleInstallationRepository
                        .findByTenantIdAndBottleIdAndActiveTrue(
                                request.getTenantId(),
                                request.getBottleId()
                        )
                        .orElseThrow(() ->
                                new ActiveInstallationNotFoundException(
                                        request.getBottleId(),
                                        request.getTenantId()
                                )
                        );

        installation.setActive(false);
        installation.setRemovedBy(request.getActorId());
        installation.setRemovedAt(LocalDateTime.now());

        BottleInstallation savedInstallation =
                bottleInstallationRepository.save(installation);

        updateBottleStatusAfterRemoval(bottle);

        bottleRepository.save(bottle);

        saveRemoveMovement(
                request,
                bottle,
                installation
        );

        return bottleInstallationMapper.toResponse(
                savedInstallation
        );
    }

    @Transactional
    public BottleInstallationResponse replaceBottle(
            ReplaceBottleRequest request
    ) {
        Bottle oldBottle = getBottle(
                request.getTenantId(),
                request.getOldBottleId()
        );

        BottleInstallation oldInstallation =
                bottleInstallationRepository
                        .findByTenantIdAndBottleIdAndActiveTrue(
                                request.getTenantId(),
                                request.getOldBottleId()
                        )
                        .orElseThrow(() ->
                                new ActiveInstallationNotFoundException(
                                        request.getOldBottleId(),
                                        request.getTenantId()
                                )
                        );

        Bottle newBottle = getBottle(
                request.getTenantId(),
                request.getNewBottleId()
        );

        validateBottleStatus(newBottle);

        validateBottleNotAlreadyInstalled(
                request.getTenantId(),
                request.getNewBottleId()
        );

        oldInstallation.setActive(false);
        oldInstallation.setRemovedBy(request.getActorId());
        oldInstallation.setRemovedAt(LocalDateTime.now());

        bottleInstallationRepository.save(oldInstallation);

        updateBottleStatusAfterRemoval(oldBottle);
        bottleRepository.save(oldBottle);

        BottleInstallation newInstallation =
                BottleInstallation.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(newBottle.getId())
                        .machineId(oldInstallation.getMachineId())
                        .slotId(oldInstallation.getSlotId())
                        .installedBy(request.getActorId())
                        .active(true)
                        .build();

        BottleInstallation savedNewInstallation =
                bottleInstallationRepository.save(newInstallation);

        newBottle.setStatus(BottleStatus.INSTALLED);
        bottleRepository.save(newBottle);

        saveReplaceMovement(
                request,
                oldBottle,
                newBottle,
                oldInstallation
        );

        return bottleInstallationMapper.toResponse(
                savedNewInstallation
        );
    }

    private Bottle getBottle(
            Long tenantId,
            Long bottleId
    ) {
        return bottleRepository
                .findByIdAndTenantId(
                        bottleId,
                        tenantId
                )
                .orElseThrow(() ->
                        new BottleNotFoundException(
                                bottleId,
                                tenantId
                        )
                );
    }

    private void validateBottleStatus(
            Bottle bottle
    ) {
        if (bottle.getStatus() != BottleStatus.AVAILABLE) {
            throw new BottleNotInstallableException(
                    bottle.getId(),
                    bottle.getStatus().name()
            );
        }
    }

    private void validateBottleNotAlreadyInstalled(
            Long tenantId,
            Long bottleId
    ) {
        boolean alreadyInstalled =
                bottleInstallationRepository
                        .existsByTenantIdAndBottleIdAndActiveTrue(
                                tenantId,
                                bottleId
                        );

        if (alreadyInstalled) {
            throw new BottleAlreadyInstalledException(
                    bottleId
            );
        }
    }

    private void validateSlotAvailable(
            Long tenantId,
            Long machineId,
            Long slotId
    ) {
        boolean occupied =
                bottleInstallationRepository
                        .existsByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
                                tenantId,
                                machineId,
                                slotId
                        );

        if (occupied) {
            throw new SlotAlreadyOccupiedException(
                    machineId,
                    slotId
            );
        }
    }

    private void saveMovement(
            InstallBottleRequest request,
            Bottle bottle
    ) {
        BottleMovement movement =
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(bottle.getId())
                        .movementType(BottleMovementType.INSTALL)
                        .machineId(request.getMachineId())
                        .slotId(request.getSlotId())
                        .quantityBefore(bottle.getCurrentQuantity())
                        .quantityAfter(bottle.getCurrentQuantity())
                        .actorId(request.getActorId())
                        .reason("Bottle installed into machine slot")
                        .build();

        bottleMovementRepository.save(movement);
    }
    private void updateBottleStatusAfterRemoval(
            Bottle bottle
    ) {
        if (bottle.getStatus() == BottleStatus.INSTALLED) {
            bottle.setStatus(BottleStatus.AVAILABLE);
        }
    }

    private void saveRemoveMovement(
            RemoveBottleRequest request,
            Bottle bottle,
            BottleInstallation installation
    ) {
        BottleMovement movement =
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(bottle.getId())
                        .movementType(BottleMovementType.REMOVE)
                        .machineId(installation.getMachineId())
                        .slotId(installation.getSlotId())
                        .quantityBefore(bottle.getCurrentQuantity())
                        .quantityAfter(bottle.getCurrentQuantity())
                        .actorId(request.getActorId())
                        .reason(request.getReason())
                        .build();

        bottleMovementRepository.save(movement);
    }

    private void saveReplaceMovement(
            ReplaceBottleRequest request,
            Bottle oldBottle,
            Bottle newBottle,
            BottleInstallation oldInstallation
    ) {
        BottleMovement oldBottleMovement =
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(oldBottle.getId())
                        .movementType(BottleMovementType.REPLACE)
                        .machineId(oldInstallation.getMachineId())
                        .slotId(oldInstallation.getSlotId())
                        .quantityBefore(oldBottle.getCurrentQuantity())
                        .quantityAfter(oldBottle.getCurrentQuantity())
                        .actorId(request.getActorId())
                        .reason(request.getReason())
                        .build();

        BottleMovement newBottleMovement =
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(newBottle.getId())
                        .movementType(BottleMovementType.REPLACE)
                        .machineId(oldInstallation.getMachineId())
                        .slotId(oldInstallation.getSlotId())
                        .quantityBefore(newBottle.getCurrentQuantity())
                        .quantityAfter(newBottle.getCurrentQuantity())
                        .actorId(request.getActorId())
                        .reason(request.getReason())
                        .build();

        bottleMovementRepository.save(oldBottleMovement);
        bottleMovementRepository.save(newBottleMovement);
    }
}