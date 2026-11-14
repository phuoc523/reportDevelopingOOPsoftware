# Order Service diagrams

Scope: authoritative order creation, payments, idempotent callback processing, dispensing orchestration and manual review.

Order calls Catalog and Machine through application ports. A command may remain non-terminal and is polled; the diagrams do not assume immediate physical completion.
