# Reporting Service diagrams

Scope: transaction synchronization/query, summaries, dashboard aggregation and Excel exports.

Reporting owns its local transaction projection and reads operational snapshots from upstream services. The transaction entity does not own tenant, machine, slot or product data.
