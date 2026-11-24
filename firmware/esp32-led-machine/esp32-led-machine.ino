#include <Arduino.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <time.h>

#if __has_include("secrets.h")
#include "secrets.h"
#else
#include "secrets.example.h"
#endif

namespace {
constexpr char FIRMWARE_VERSION[] = "esp32-led-1.1.0";
constexpr unsigned long HEARTBEAT_INTERVAL_MS = 5000;
constexpr unsigned long TELEMETRY_INTERVAL_MS = 30000;
constexpr unsigned long MQTT_RECONNECT_INTERVAL_MS = 3000;
constexpr int MIN_DURATION_MS = 50;
constexpr int MAX_DURATION_MS = 10000;

struct SlotPin {
  const char* slotId;
  uint8_t pin;
};

const SlotPin SLOT_PINS[] = {
    {"91001", 5},
    {"91006", 18},
    {"91007", 19},
    {"91008", 21},
};
constexpr size_t SLOT_COUNT = sizeof(SLOT_PINS) / sizeof(SLOT_PINS[0]);

WiFiClient networkClient;
PubSubClient mqtt(networkClient);
Preferences preferences;
unsigned long lastHeartbeatAt = 0;
unsigned long lastTelemetryAt = 0;
unsigned long lastReconnectAttemptAt = 0;
float remainingPercent[] = {100.0F, 100.0F, 100.0F, 100.0F};

String topic(const char* channel) {
  return "scentstation/" + String(MACHINE_ID) + "/" + channel;
}

String newMessageId() {
  uint8_t bytes[16];
  for (uint8_t& value : bytes) value = static_cast<uint8_t>(esp_random());
  bytes[6] = (bytes[6] & 0x0F) | 0x40;
  bytes[8] = (bytes[8] & 0x3F) | 0x80;
  char result[37];
  snprintf(result, sizeof(result),
           "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
           bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
           bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]);
  return result;
}

String timestampNow() {
  time_t now = time(nullptr);
  struct tm utc {};
  gmtime_r(&now, &utc);
  char value[25];
  strftime(value, sizeof(value), "%Y-%m-%dT%H:%M:%SZ", &utc);
  return value;
}

bool clockReady() {
  return time(nullptr) > 1700000000;
}

time_t parseTimestamp(const char* value) {
  struct tm parsed {};
  if (value == nullptr || strptime(value, "%Y-%m-%dT%H:%M:%SZ", &parsed) == nullptr) return 0;
  return mktime(&parsed);
}

int pinForSlot(const String& slotId) {
  for (const SlotPin& slot : SLOT_PINS) {
    if (slotId == slot.slotId) return slot.pin;
  }
  return -1;
}

int indexForSlot(const String& slotId) {
  for (size_t index = 0; index < SLOT_COUNT; index++) {
    if (slotId == SLOT_PINS[index].slotId) return static_cast<int>(index);
  }
  return -1;
}

void publishResponse(const String& commandId, const char* status, const String& details) {
  JsonDocument document;
  document["schemaVersion"] = 1;
  document["messageId"] = newMessageId();
  document["machineId"] = MACHINE_ID;
  document["type"] = "COMMAND_RESPONSE";
  document["timestamp"] = timestampNow();
  JsonObject payload = document["payload"].to<JsonObject>();
  payload["commandId"] = commandId;
  payload["status"] = status;
  payload["details"] = details;

  char json[768];
  size_t length = serializeJson(document, json, sizeof(json));
  mqtt.publish(topic("response").c_str(), reinterpret_cast<const uint8_t*>(json), length, false);
}

void rememberResult(const String& commandId, const char* status, const String& details) {
  preferences.putString("commandId", commandId);
  preferences.putString("status", status);
  preferences.putString("details", details.substring(0, 200));
}

void replayIfDuplicate(const String& commandId, bool& duplicate) {
  duplicate = preferences.getString("commandId", "") == commandId;
  if (!duplicate) return;
  String status = preferences.getString("status", "UNKNOWN");
  String details = preferences.getString("details", "Stored command result replayed");
  publishResponse(commandId, status.c_str(), details);
}

void handleCommand(byte* bytes, unsigned int length) {
  JsonDocument document;
  if (deserializeJson(document, bytes, length) != DeserializationError::Ok) return;
  if (document["schemaVersion"].as<int>() != 1
      || document["machineId"].as<String>() != MACHINE_ID
      || document["type"].as<String>() != "DISPENSE_COMMAND") return;

  JsonObject payload = document["payload"];
  String commandId = payload["commandId"].as<String>();
  String slotId = payload["slotId"].as<String>();
  String action = payload["action"].as<String>();
  int durationMs = payload["durationMs"].as<int>();
  time_t expiresAt = parseTimestamp(payload["expiresAt"].as<const char*>());
  if (commandId.isEmpty()) return;

  bool duplicate = false;
  replayIfDuplicate(commandId, duplicate);
  if (duplicate) return;

  int pin = pinForSlot(slotId);
  if (action != "DISPENSE" || pin < 0 || durationMs < MIN_DURATION_MS
      || durationMs > MAX_DURATION_MS) {
    rememberResult(commandId, "FAILED", "Invalid slot, action, or duration");
    publishResponse(commandId, "FAILED", "Invalid slot, action, or duration");
    return;
  }
  if (!clockReady() || expiresAt == 0 || time(nullptr) > expiresAt) {
    rememberResult(commandId, "EXPIRED", "Command expired before execution");
    publishResponse(commandId, "EXPIRED", "Command expired before execution");
    return;
  }

  publishResponse(commandId, "ACKNOWLEDGED", "ESP32 accepted LED command");
  rememberResult(commandId, "UNKNOWN", "ESP32 restarted during LED command");

  digitalWrite(pin, HIGH);
  delay(durationMs);
  digitalWrite(pin, LOW);

  rememberResult(commandId, "DISPENSED", "LED cycle completed");
  publishResponse(commandId, "DISPENSED", "LED cycle completed");
}

void mqttCallback(char* incomingTopic, byte* payload, unsigned int length) {
  if (String(incomingTopic) == topic("command")) handleCommand(payload, length);
}

void publishHeartbeat() {
  if (!mqtt.connected() || !clockReady()) return;
  JsonDocument document;
  document["schemaVersion"] = 1;
  document["messageId"] = newMessageId();
  document["machineId"] = MACHINE_ID;
  document["type"] = "HEARTBEAT";
  document["timestamp"] = timestampNow();
  JsonObject payload = document["payload"].to<JsonObject>();
  payload["status"] = "ONLINE";
  payload["uptimeSeconds"] = millis() / 1000;
  payload["firmwareVersion"] = FIRMWARE_VERSION;

  char json[512];
  size_t length = serializeJson(document, json, sizeof(json));
  mqtt.publish(topic("heartbeat").c_str(), reinterpret_cast<const uint8_t*>(json), length, false);
}

void publishTelemetry() {
  if (!mqtt.connected() || !clockReady()) return;
  JsonDocument document;
  document["schemaVersion"] = 1;
  document["messageId"] = newMessageId();
  document["machineId"] = MACHINE_ID;
  document["type"] = "TELEMETRY";
  document["timestamp"] = timestampNow();
  JsonArray readings = document["payload"]["slots"].to<JsonArray>();
  for (size_t index = 0; index < SLOT_COUNT; index++) {
    JsonObject reading = readings.add<JsonObject>();
    reading["slotId"] = SLOT_PINS[index].slotId;
    reading["remainingPercent"] = remainingPercent[index];
    reading["sensorStatus"] = "NORMAL";
  }

  char json[1024];
  size_t length = serializeJson(document, json, sizeof(json));
  mqtt.publish(topic("telemetry").c_str(), reinterpret_cast<const uint8_t*>(json), length, false);
}

void handleSerialDemo() {
  if (!Serial.available()) return;
  String line = Serial.readStringUntil('\n');
  line.trim();
  if (!line.startsWith("LEVEL ")) {
    Serial.println("Use: LEVEL <slotId> <0-100>");
    return;
  }
  int separator = line.indexOf(' ', 6);
  if (separator < 0) return;
  String slotId = line.substring(6, separator);
  float value = line.substring(separator + 1).toFloat();
  int index = indexForSlot(slotId);
  if (index < 0 || value < 0 || value > 100) {
    Serial.println("Invalid slotId or level");
    return;
  }
  remainingPercent[index] = value;
  publishTelemetry();
  Serial.printf("Telemetry sent: slot=%s remaining=%.2f%%\n", slotId.c_str(), value);
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print('.');
  }
  Serial.printf("\nWiFi connected, IP=%s\n", WiFi.localIP().toString().c_str());
  configTime(0, 0, "pool.ntp.org", "time.google.com");
}

void connectMqtt() {
  if (mqtt.connected() || millis() - lastReconnectAttemptAt < MQTT_RECONNECT_INTERVAL_MS) return;
  lastReconnectAttemptAt = millis();
  String clientId = "esp32-" + String(MACHINE_ID) + "-" + String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
  if (mqtt.connect(clientId.c_str(), MQTT_USERNAME, MQTT_PASSWORD)) {
    mqtt.subscribe(topic("command").c_str(), 1);
    Serial.println("MQTT connected");
    publishHeartbeat();
    publishTelemetry();
  }
}
}  // namespace

void setup() {
  Serial.begin(115200);
  setenv("TZ", "UTC0", 1);
  tzset();
  for (const SlotPin& slot : SLOT_PINS) {
    pinMode(slot.pin, OUTPUT);
    digitalWrite(slot.pin, LOW);
  }
  preferences.begin("scentstation", false);
  connectWifi();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(1024);
  mqtt.setCallback(mqttCallback);
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWifi();
  connectMqtt();
  mqtt.loop();
  handleSerialDemo();
  if (mqtt.connected() && millis() - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeatAt = millis();
    publishHeartbeat();
  }
  if (mqtt.connected() && millis() - lastTelemetryAt >= TELEMETRY_INTERVAL_MS) {
    lastTelemetryAt = millis();
    publishTelemetry();
  }
  delay(10);
}
