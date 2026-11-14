# ScentStation UML architecture

Baseline: commit `2a7b130`, reviewed on 2026-11-14.

This folder documents the current backend microservices. The frontend and physical-device transport are not present in this repository, so they are shown only as API actors or `<<future extension>>` ports.

## Reading rules

- Solid arrows from the Gateway represent public routing.
- Dotted service-to-service arrows represent synchronous integration.
- Cross-service objects are logical ID references, never JPA relationships.
- `<<future extension>>` is an intentionally undefined port, not implemented behavior.
- `<<local/test stub>>` identifies an adapter that is not production integration.
- Class diagrams show behaviorally important fields and public methods only.
- Sequence and activity diagrams are `as-is`; they stop where the implementation stops.

## Diagram set

- `system-overview/`: component view, system use cases and core flow.
- `api-gateway/`: route map and trust-boundary sequence.
- Each service: use case, class, sequence and activity diagrams.
- Stateful workflows additionally have state diagrams in `order-service/`, `machine-service/` and `operation-service/`.

## Known extension points

1. Frontend/Kiosk: integrate through public Gateway routes; no frontend-specific contract is assumed.
2. Machine device transport: implement an inbound adapter around command acknowledge/complete transitions.
3. Catalog machine availability: implement `MachineAvailabilityClient` without changing catalog domain logic.
4. Operation machine slot lookup: implement `MachineSlotClient`; slot mutation should use a separate explicit contract if later required.
5. Asynchronous integration may be added behind application ports; current diagrams do not require a broker.

The previous audit remains in `ARCHITECTURE_REVIEW.md`. `review-proposal/` is retained as the pre-redesign comparison baseline; the diagrams in the service folders are authoritative for review.
