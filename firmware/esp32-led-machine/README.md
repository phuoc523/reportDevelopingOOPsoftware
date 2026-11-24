# ESP32 LED Machine Firmware

Firmware demo cho ESP32 DevKit V1. Bốn LED đại diện cho bốn vòi phun:

| Slot ID | GPIO |
|---|---:|
| `91001` | 5 |
| `91006` | 18 |
| `91007` | 19 |
| `91008` | 21 |

Mỗi LED phải nối tiếp điện trở 220–330Ω và dùng GND chung với ESP32.

## Cấu hình local

Sao chép `secrets.example.h` thành `secrets.h`, sau đó điền Wi-Fi, địa chỉ IPv4 của laptop và mật khẩu MQTT của `device-ss-demo-9101`. `secrets.h` đã bị Git bỏ qua.

ESP32 và laptop phải ở cùng mạng. `MQTT_HOST` không được dùng `localhost`; nó phải là địa chỉ LAN của laptop chạy Mosquitto.

## Thư viện Arduino

```powershell
arduino-cli lib install PubSubClient
arduino-cli lib install ArduinoJson
```

## Build và upload

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32-led-machine
arduino-cli upload --port COM7 --fqbn esp32:esp32:esp32 firmware/esp32-led-machine
```

Firmware gửi heartbeat mỗi 5 giây, nhận `DISPENSE_COMMAND`, lưu `commandId` gần nhất vào NVS, bật đúng LED theo `durationMs`, rồi gửi `ACKNOWLEDGED` và `DISPENSED`. Nếu ESP32 khởi động lại giữa lúc LED đang bật, command được lưu là `UNKNOWN` và không được thực hiện lại.
