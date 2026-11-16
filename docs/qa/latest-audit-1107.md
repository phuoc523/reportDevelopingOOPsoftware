# ScentStation integration audit â€” 2026-11-07

- Branch: `test-2026-11-07-latest`
- Checked HEAD: `3d5d7445166511c95a398945933ca39b82a30a70`
- Run: `20261107-112058` (local machine; no claims of remote test execution)
- PASS: tool/build/test result as described; WARN: missing evidence or known gap; FAIL: test/tool failed.

| Component | Result | Detail |
|---|---|---|
| Git branch | PASS | test-2026-11-07-latest @ 3d5d744 |
| Git remote sync | PASS | Local == remote |
| Git working tree | PASS | Khong co sua doi tracked; mysql.e.yml duoc bo qua |
| Gateway order routing | WARN | Chua thay route Order/Payment trong application.yml |
| Order persistence | WARN | Dang in-memory; se mat du lieu khi restart |
| Machine MQTT listener | WARN | Khong co MqttDeviceMessageListener.java; can xac nhan MQTT duoc thay the |
| Full Docker Compose | WARN | services: {} (chua the docker compose up full system) |
| Default service ports | WARN | Operation va Reporting cung mac dinh 8085 |
| Merge origin/feature/p1-06-p1-11-platform | WARN | Con commit chua duoc merge |
| Merge origin/test-order-payment | WARN | Con commit chua duoc merge |
| Docker engine | WARN | Linux Engine khong ket noi duoc; KHONG dung audit, tiep tuc test backend/frontend |
| MySQL container | WARN | Khong kiem tra duoc khi Docker Engine offline |
| api-gateway | PASS | Maven test exit=0 |
| identity-service | PASS | Maven test exit=0 |
| catalog-service | PASS | Maven test exit=0 |
| order-service | PASS | Maven test exit=0 |
| machine-service | PASS | Maven test exit=0 |
| operation-service | FAIL | Maven exit=1 (xem log) |
| reporting-service | FAIL | Maven exit=1 (xem log) |
| Frontend builds | WARN | Skipped in this run; previous PASS logs remain separate |

## Notes
- Audit does not resolve Git conflicts, auto-merge branches, or assert E2E completion.
- Local logs may expose environment details; review before sharing.
- The report is separate from the repository until explicitly published.
