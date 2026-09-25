#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <Preferences.h>
#include "time.h"
#include "DHT.h"

// =====================================================
// 1. CONFIGURATION
// =====================================================

#define API_KEY "AIzaSyC4J1wPaPlqn5i2UI46CcaaDnwmSu3BKUs"
#define PROJECT_ID "scentguard-4thyear"
#define DATABASE_URL "https://scentguard-4thyear-default-rtdb.asia-southeast1.firebasedatabase.app/"

// BLE UUIDs
#define SERVICE_UUID        "0000FF01-0000-1000-8000-00805F9B34FB"
#define SSID_CHAR_UUID      "0000FF02-0000-1000-8000-00805F9B34FB"
#define PASS_CHAR_UUID      "0000FF03-0000-1000-8000-00805F9B34FB"
#define RID_CHAR_UUID       "0000FF04-0000-1000-8000-00805F9B34FB"

// Timing (Non-blocking)
#define SERIAL_INTERVAL 1500UL
#define TELEMETRY_INTERVAL 5000UL
#define CONFIG_INTERVAL 5000UL
#define HISTORY_INTERVAL 60000UL
#define WIFI_RECOVERY_TIMEOUT 60000UL

#define SPRAY_ON_TIME 3000UL
#define SPRAY_OFF_TIME 2000UL
#define SANITATION_CYCLES 10
#define SANITATION_DURATION 50000UL

#define HOT_TEMPERATURE 33.0

// Pins
#define RELAY_CH1_PIN 23
#define RELAY_CH2_PIN 25
#define MQ135_PIN 34
#define DHT_PIN 22
#define DHT_TYPE DHT11
#define GREEN_LED 18
#define RED_LED 19
#define BOOT_BUTTON 0

DHT dht(DHT_PIN, DHT_TYPE);

// =====================================================
// 3. GLOBALS
// =====================================================

Preferences preferences;
bool isProvisioning = false;
String receivedSSID = "";
String receivedPASS = "";
String receivedRID = "";

String activeRestaurantId = "";
int thresholdWarn = 1000;
int thresholdDanger = 1500;

FirebaseData fbdo_telem, fbdo_config, fbdo_hist;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastSerial = 0, lastTelem = 0, lastConfig = 0, lastHist = 0;
unsigned long wifiLostTime = 0;
bool wifiLossHandled = false;

String currentFanMode = "AUTO";
bool isFanPhysicallyActive = false;
bool isSanitationActive = false;
bool pumpCurrentlyOn = false;
unsigned long sanitationStartTime = 0;
unsigned long lastSprayChangeTime = 0;
int sanitationCycleCount = 0;
bool wasInDanger = false;
String lastInternalStatus = "SAFE";

float temperature = 0.0;
String temperatureStatus = "NORMAL";
float currentHum = 0.0;

// =====================================================
// 4. FUNCTIONS
// =====================================================

float readTemperature() {
    float t = dht.readTemperature();
    if (!isnan(t)) {
        temperature = t;
    }
    if (temperature >= HOT_TEMPERATURE) {
        temperatureStatus = "HOT";
    } else {
        temperatureStatus = "NORMAL";
    }
    return temperature;
}

void setFan(bool enabled) {
    digitalWrite(
            RELAY_CH1_PIN,
            enabled ? LOW : HIGH
    );
    if (isFanPhysicallyActive != enabled) {
        isFanPhysicallyActive = enabled;
        Serial.print("Relay CH1 (Fan): ");
        Serial.println(
                enabled ? "ON" : "OFF"
        );
    }
}

void setPump(bool enabled) {
    digitalWrite(
            RELAY_CH2_PIN,
            enabled ? LOW : HIGH
    );
    if (pumpCurrentlyOn != enabled) {
        pumpCurrentlyOn = enabled;
        Serial.print(
                "Relay CH2 (Sanitation Pump): "
        );
        Serial.println(
                enabled
                ? "ON - SPRAYING"
                : "OFF - PAUSE"
        );
    }
}

void startSanitation() {
    if (isSanitationActive) {
        return;
    }
    Serial.println();
    Serial.println("======================================");
    Serial.println("SANITATION CYCLE STARTED");
    Serial.println("======================================");
    Serial.println("Pump ON  : 3 seconds");
    Serial.println("Pump OFF : 2 seconds");
    Serial.println("Cycles   : 10");
    Serial.println("Total    : 50 seconds");
    Serial.println("======================================");
    isSanitationActive = true;
    sanitationStartTime = millis();
    lastSprayChangeTime = millis();
    sanitationCycleCount = 1;
    setPump(true);
    Serial.println(
            "Sanitation Spray #1 STARTED"
    );
}

void stopSanitation() {
    isSanitationActive = false;
    sanitationCycleCount = 0;
    setPump(false);
    Serial.println();
    Serial.println("======================================");
    Serial.println("SANITATION COMPLETED");
    Serial.println("Pump OFF");
    Serial.println("======================================");
}

void updateSanitation() {
    if (!isSanitationActive) {
        return;
    }
    unsigned long now = millis();
    if (
            now - sanitationStartTime >= SANITATION_DURATION
            ) {
        stopSanitation();
        return;
    }
    if (pumpCurrentlyOn) {
        if (
                now - lastSprayChangeTime >= SPRAY_ON_TIME
                ) {
            setPump(false);
            lastSprayChangeTime = now;
            Serial.print(
                    "Spray #"
            );
            Serial.print(
                    sanitationCycleCount
            );
            Serial.println(
                    " completed - 2 sec pause"
            );
        }
    }
    else {
        if (
                now - lastSprayChangeTime >= SPRAY_OFF_TIME
                ) {
            if (
                    sanitationCycleCount >= SANITATION_CYCLES
                    ) {
                stopSanitation();
                return;
            }
            sanitationCycleCount++;
            setPump(true);
            lastSprayChangeTime = now;
            Serial.print(
                    "Sanitation Spray #"
            );
            Serial.print(
                    sanitationCycleCount
            );
            Serial.println(
                    " STARTED - 3 sec"
            );
        }
    }
}

class MyCallbacks : public BLECharacteristicCallbacks {
    void onWrite(
            BLECharacteristic *pCharacteristic
    ) {
        String value =
                String(
                        pCharacteristic
                                ->getValue()
                                .c_str()
                );
        String uuid =
                pCharacteristic
                        ->getUUID()
                        .toString();
        if (
                uuid.equalsIgnoreCase(
                        SSID_CHAR_UUID
                )
                ) {
            receivedSSID = value;
            receivedSSID.trim();
            Serial.print(
                    "BLE: SSID Received: "
            );
            Serial.println(
                    receivedSSID
            );
        }
        else if (
                uuid.equalsIgnoreCase(
                        PASS_CHAR_UUID
                )
                ) {
            receivedPASS = value;
            receivedPASS.trim();
            Serial.println(
                    "BLE: Password Received (Hidden)"
            );
        }
        else if (
                uuid.equalsIgnoreCase(
                        RID_CHAR_UUID
                )
                ) {
            receivedRID = value;
            receivedRID.trim();
            Serial.print(
                    "BLE: Restaurant ID Received: "
            );
            Serial.println(
                    receivedRID
            );
        }
    }
};

void startProvisioning() {
    isProvisioning = true;
    Serial.println(
            "\nStarting BLE Setup Mode (ScentGuard-ESP32)"
    );
    BLEDevice::init(
            "ScentGuard-ESP32"
    );
    BLEServer *pServer =
            BLEDevice::createServer();
    BLEService *pService =
            pServer->createService(
                    SERVICE_UUID
            );
    BLECharacteristic *pSSID =
            pService->createCharacteristic(
                    SSID_CHAR_UUID,
                    BLECharacteristic::PROPERTY_WRITE
            );
    BLECharacteristic *pPASS =
            pService->createCharacteristic(
                    PASS_CHAR_UUID,
                    BLECharacteristic::PROPERTY_WRITE
            );
    BLECharacteristic *pRID =
            pService->createCharacteristic(
                    RID_CHAR_UUID,
                    BLECharacteristic::PROPERTY_WRITE
            );
    pSSID->setCallbacks(
            new MyCallbacks()
    );
    pPASS->setCallbacks(
            new MyCallbacks()
    );
    pRID->setCallbacks(
            new MyCallbacks()
    );
    pService->start();
    BLEAdvertising *pAdvertising =
            BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(
            SERVICE_UUID
    );
    pAdvertising->setScanResponse(
            true
    );
    pAdvertising->start();
    Serial.println(
            "Waiting for App connection..."
    );
}

bool loadCredentials() {
    preferences.begin(
            "scentguard",
            true
    );
    String ssid =
            preferences.getString(
                    "ssid",
                    ""
            );
    String pass =
            preferences.getString(
                    "pass",
                    ""
            );
    activeRestaurantId =
            preferences.getString(
                    "rid",
                    ""
            );
    preferences.end();
    if (
            ssid == "" ||
            activeRestaurantId == ""
            ) {
        return false;
    }
    Serial.println(
            "\nCredentials loaded. Connecting to: "
            + ssid
    );
    WiFi.begin(
            ssid.c_str(),
            pass.c_str()
    );
    unsigned long start =
            millis();
    while (
            WiFi.status() != WL_CONNECTED &&
            millis() - start < 15000
            ) {
        delay(500);
        Serial.print(".");
    }
    return (
            WiFi.status() ==
            WL_CONNECTED
    );
}

String getTimestamp() {
    time_t now =
            time(nullptr);
    if (
            now < 100000
            ) {
        return "";
    }
    struct tm timeinfo;
    gmtime_r(
            &now,
            &timeinfo
    );
    char timestamp[32];
    strftime(
            timestamp,
            sizeof(timestamp),
            "%Y-%m-%dT%H:%M:%SZ",
            &timeinfo
    );
    return String(
            timestamp
    );
}

String getSlotID() {
    struct tm timeinfo;
    if (
            !getLocalTime(
                    &timeinfo
            )
            ) {
        return (
                "snap_" +
                String(millis())
        );
    }
    int slotMin =
            (timeinfo.tm_min / 15) * 15;
    char buf[32];
    strftime(
            buf,
            sizeof(buf),
            "snap_%Y%m%d_%H",
            &timeinfo
    );
    String id =
            String(buf);
    if (
            slotMin < 10
            ) {
        id += "0";
    }
    id += String(
            slotMin
    );
    return id;
}

void syncTime() {
    configTime(
            0,
            0,
            "pool.ntp.org",
            "time.nist.gov"
    );
    Serial.print(
            "Syncing Time"
    );
    time_t now =
            time(nullptr);
    while (
            now < 8 * 3600 * 2
            ) {
        delay(500);
        Serial.print(".");
        now =
                time(nullptr);
    }
    Serial.println(
            "\nTime OK!"
    );
}

void readRemoteConfig() {
    String path =
            "restaurants/" +
            activeRestaurantId;
    if (
            Firebase.Firestore.getDocument(
                    &fbdo_config,
                    PROJECT_ID,
                    "",
                    path.c_str(),
                    "fanMode,thresholdWarn,thresholdDanger"
            )
            ) {
        FirebaseJson json;
        FirebaseJsonData res;
        json.setJsonData(
                fbdo_config.payload()
        );
        if (
                json.get(
                        res,
                        "fields/fanMode/stringValue"
                )
                ) {
            currentFanMode =
                    res.stringValue;
        }
        if (
                json.get(
                        res,
                        "fields/thresholdWarn/integerValue"
                )
                ) {
            thresholdWarn =
                    (int)res.intValue;
        }
        if (
                json.get(
                        res,
                        "fields/thresholdDanger/integerValue"
                )
                ) {
            thresholdDanger =
                    (int)res.intValue;
        }
        Serial.printf(
                ">> Sync [RID:%s]: Mode=%s, Warn=%d, Danger=%d\n",
                activeRestaurantId.c_str(),
                currentFanMode.c_str(),
                thresholdWarn,
                thresholdDanger
        );
    }
}

void uploadTelemetry(
        int gasValue,
        String airStatus
) {
    FirebaseJson content;
    content.set(
            "fields/currentGasPpm/integerValue",
            gasValue
    );
    content.set(
            "fields/airStatus/stringValue",
            airStatus
    );
    content.set(
            "fields/fanStatus/stringValue",
            isFanPhysicallyActive
            ? "ON"
            : "OFF"
    );
    content.set(
            "fields/temperature/doubleValue",
            temperature
    );
    String ts =
            getTimestamp();
    if (
            ts != ""
            ) {
        content.set(
                "fields/lastSeen/timestampValue",
                ts
        );
    }
    String path =
            "restaurants/" +
            activeRestaurantId;
    if (
            Firebase.Firestore.patchDocument(
                    &fbdo_telem,
                    PROJECT_ID,
                    "",
                    path.c_str(),
                    content.raw(),
                    "currentGasPpm,airStatus,fanStatus,lastSeen,temperature"
            )
            ) {
        Serial.println(
                ">> Telemetry OK"
        );
    }
}

void uploadHistorySnapshot(
        int gasValue,
        String airStatus
) {
    Serial.println(
            ">> UPLOADING HISTORY SNAPSHOT..."
    );
    FirebaseJson content;
    content.set(
            "fields/currentGasPpm/integerValue",
            gasValue
    );
    content.set(
            "fields/airStatus/stringValue",
            airStatus
    );
    content.set(
            "fields/fanStatus/stringValue",
            isFanPhysicallyActive
            ? "ON"
            : "OFF"
    );
    content.set(
            "fields/fanMode/stringValue",
            currentFanMode
    );
    content.set(
            "fields/temperature/doubleValue",
            temperature
    );
    String ts =
            getTimestamp();
    if (
            ts != ""
            ) {
        content.set(
                "fields/timestamp/timestampValue",
                ts
        );
    }
    String slotId =
            getSlotID();
    String path =
            "restaurants/" +
            activeRestaurantId +
            "/sensor_history/" +
            slotId;
    if (
            Firebase.Firestore.patchDocument(
                    &fbdo_hist,
                    PROJECT_ID,
                    "",
                    path.c_str(),
                    content.raw(),
                    "currentGasPpm,airStatus,fanStatus,fanMode,timestamp,temperature"
            )
            ) {
        Serial.println(
                ">> History Snapshot OK: " +
                slotId
        );
    }
    else {
        Serial.println(
                ">> History Snapshot FAILED: " +
                fbdo_hist.errorReason()
        );
    }
}

void setup() {
    Serial.begin(115200);

    // Initialize DHT11
    dht.begin();

    pinMode(RELAY_CH1_PIN, OUTPUT);
    digitalWrite(RELAY_CH1_PIN, HIGH);
    pinMode(RELAY_CH2_PIN, OUTPUT);
    digitalWrite(RELAY_CH2_PIN, HIGH);
    pinMode(GREEN_LED, OUTPUT);
    pinMode(RED_LED, OUTPUT);
    pinMode(BOOT_BUTTON, INPUT_PULLUP);

    if (digitalRead(BOOT_BUTTON) == LOW) {
        Serial.println("Reset Mode... Hold 5s");
        delay(5000);
        if (digitalRead(BOOT_BUTTON) == LOW) {
            preferences.begin("scentguard", false);
            preferences.clear();
            preferences.end();
            Serial.println("NVS Cleared. Restarting...");
            ESP.restart();
        }
    }

    if (!loadCredentials()) {
        startProvisioning();
    } else {
        syncTime();
        config.api_key = API_KEY;
        config.database_url = DATABASE_URL;
        config.token_status_callback = tokenStatusCallback;
        Firebase.signUp(&config, &auth, "", "");
        Firebase.begin(&config, &auth);
        Firebase.reconnectWiFi(true);
        Serial.println("Firebase Ready.");
    }
}

void loop() {
    unsigned long now = millis();

    if (isProvisioning) {
        digitalWrite(RED_LED, (now / 500) % 2 == 0);
        if (receivedSSID != "" && receivedPASS != "" && receivedRID != "") {
            Serial.println("\nTesting connection...");
            WiFi.disconnect(true);
            delay(1000);
            WiFi.begin(receivedSSID.c_str(), receivedPASS.c_str());
            unsigned long start = millis();
            while (WiFi.status() != WL_CONNECTED && millis() - start < 10000) {
                delay(500);
                Serial.print(".");
            }
            if (WiFi.status() == WL_CONNECTED) {
                preferences.begin("scentguard", false);
                preferences.putString("ssid", receivedSSID);
                preferences.putString("pass", receivedPASS);
                preferences.putString("rid", receivedRID);
                preferences.end();
                Serial.println("\nSuccess! Restarting...");
                delay(2000);
                ESP.restart();
            } else {
                Serial.println("\nFailed. Waiting for new credentials...");
                receivedSSID = "";
                receivedPASS = "";
                receivedRID = "";
            }
        }
        return;
    }

    // 1. FAST SENSOR READING (Non-blocking)
    int gasValue = analogRead(MQ135_PIN);

    // Read DHT11 Temperature
    readTemperature();

    String airStatus = (gasValue >= thresholdDanger) ? "DANGER" : (gasValue >= thresholdWarn ? "WARN" : "SAFE");

    // Interruption check: If air status is not SAFE, active sanitation must be interrupted immediately
    if (airStatus != "SAFE" && isSanitationActive) {
        Serial.println("Sanitation interrupted: Air quality increased");
        stopSanitation();
    }

    // 2. STATUS CHANGE LOGIC
    if (airStatus != lastInternalStatus) {
        if (airStatus == "DANGER") {
            wasInDanger = true;
        }
        if (wasInDanger && airStatus == "SAFE" && !isSanitationActive) {
            wasInDanger = false;
            if (currentFanMode != "ON") {
                startSanitation();
            }
        }
        lastInternalStatus = airStatus;
    }

    updateSanitation();

    // 3. FAN CONTROL
    bool fanShouldBeOn = (currentFanMode == "ON") || (currentFanMode == "AUTO" && airStatus != "SAFE");
    if (isSanitationActive && currentFanMode == "AUTO") {
        fanShouldBeOn = false;
    }
    setFan(fanShouldBeOn);

    digitalWrite(GREEN_LED, airStatus == "SAFE");
    digitalWrite(RED_LED, airStatus != "SAFE");

    // 4. WI-FI RECOVERY WATCHDOG
    if (WiFi.status() != WL_CONNECTED) {
        if (wifiLostTime == 0) {
            wifiLostTime = now;
        } else if (now - wifiLostTime >= WIFI_RECOVERY_TIMEOUT && !wifiLossHandled) {
            Serial.println("\n[WATCHDOG] Wi-Fi lost for 60s. Entering Recovery (BLE)...");
            wifiLossHandled = true;
            startProvisioning();
            return;
        }
    } else {
        wifiLostTime = 0;
        wifiLossHandled = false;
    }

    // 5. SERIAL MONITOR OUTPUT (1.5s Interval)
    if (now - lastSerial >= SERIAL_INTERVAL) {
        lastSerial = now;
        Serial.println("--------------------------------------");
        Serial.printf("[SENSOR] Gas: %d | Temp: %.1f C - %s | Status: %s\n", gasValue, temperature, temperatureStatus.c_str(), airStatus.c_str());
        Serial.printf("[FAN] Status: %s | Mode: %s\n", isFanPhysicallyActive ? "ON" : "OFF", currentFanMode.c_str());
        Serial.printf("[PUMP] Status: %s\n", isSanitationActive ? "ACTIVE" : "READY");
        Serial.printf("[WIFI] %s\n", (WiFi.status() == WL_CONNECTED) ? "Connected" : "Disconnected");
        Serial.printf("[FIREBASE] %s\n", Firebase.ready() ? "Connected" : "Offline");
    }

    // 6. FIREBASE SYNC (Intervals)
    if (Firebase.ready()) {
        if (now - lastConfig >= CONFIG_INTERVAL) {
            lastConfig = now;
            readRemoteConfig();
        }
        if (now - lastTelem >= TELEMETRY_INTERVAL) {
            lastTelem = now;
            uploadTelemetry(gasValue, airStatus);
        }
        if (now - lastHist >= HISTORY_INTERVAL) {
            lastHist = now;
            uploadHistorySnapshot(gasValue, airStatus);
        }
    }

    // Small yield to avoid watchdog issues
    delay(10);
}
