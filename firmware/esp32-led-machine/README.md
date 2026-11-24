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
arduino-cli lib install ArduinoMqttClient@0.1.8
arduino-cli lib install ArduinoJson
```

## Build và upload

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32-led-machine
arduino-cli upload --port COM7 --fqbn esp32:esp32:esp32 firmware/esp32-led-machine
```

Firmware dùng MQTT QoS 1 cho response, heartbeat và telemetry. Nó lưu tám `commandId` gần nhất cùng kết quả vào NVS, bật đúng LED theo `durationMs`, rồi gửi `ACKNOWLEDGED` và `DISPENSED`. Nếu ESP32 khởi động lại giữa lúc LED đang bật, command được lưu là `UNKNOWN` và không được thực hiện lại.

## Demo mức nước hoa và cảnh báo thiết bị

Firmware gửi telemetry của bốn slot mỗi 30 giây và tự gửi `DEVICE_EVENT` (`DEVICE_REBOOTED`) một lần sau khi khởi động và kết nối MQTT. Khi chưa có cảm biến, mở Serial Monitor ở 115200 baud và nhập:

```text
LEVEL 91001 5
LEVEL 91001 80
ALERT LEAK_DETECTED CRITICAL 91001
ALERT DOOR_OPEN HIGH
```

Lệnh `LEVEL` đặt mức demo của slot và gửi telemetry ngay; dùng giá trị lớn hơn `stockThreshold` để kiểm tra tự resolve cảnh báo `LOW_STOCK`. Lệnh `ALERT` gửi `DEVICE_ALERT` QoS 1 lên `scentstation/{machineId}/alert`.
