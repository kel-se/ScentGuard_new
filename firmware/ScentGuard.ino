#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <Preferences.h>
#include "time.h"

#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

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

// =====================================================
// 2. TIMING
// =====================================================

#define SERIAL_INTERVAL       1500UL
#define TELEMETRY_INTERVAL    3000UL
#define CONFIG_INTERVAL        3000UL
#define HISTORY_INTERVAL      60000UL
#define WIFI_RECOVERY_TIMEOUT 60000UL

#define PUMP_ON_TIME          2000UL
#define PUMP_OFF_TIME         1000UL
#define PUMP_CYCLES           3

// =====================================================
// 3. PINS
// =====================================================

#define RELAY_CH1_PIN 23
#define RELAY_CH2_PIN 25
#define MQ135_PIN      34

#define GREEN_LED      18
#define RED_LED        19
#define BOOT_BUTTON     0

// Active-low relay
#define RELAY_ON  LOW
#define RELAY_OFF HIGH

// =====================================================
// 4. GLOBALS
// =====================================================

Preferences preferences;

bool isProvisioning = false;

String receivedSSID = "";
String receivedPASS = "";
String receivedRID = "";

String activeRestaurantId = "";

int thresholdWarn = 1000;
int thresholdDanger = 1500;

FirebaseData fbdo_telem;
FirebaseData fbdo_config;
FirebaseData fbdo_hist;

FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastSerial = 0;
unsigned long lastTelem = 0;
unsigned long lastConfig = 0;
unsigned long lastHist = 0;

unsigned long wifiLostTime = 0;

bool wifiLossHandled = false;

String currentFanMode = "AUTO";   // Manager Control: "ON", "OFF", "AUTO"
String currentPumpMode = "AUTO";  // Manager Control: "ON", "OFF", "AUTO"

bool fanActive = false;
bool previousFanActive = false;

bool pumpActive = false;
bool pumpSequenceActive = false;

unsigned long pumpTimer = 0;
int pumpCycleCount = 0;

String lastInternalStatus = "SAFE";

float smoothedGasPpm = -1.0;

// =====================================================
// 5. FAN CONTROL
// =====================================================

void setFan(bool enabled) {

    if (fanActive == enabled) {
        return;
    }

    fanActive = enabled;

    digitalWrite(
        RELAY_CH1_PIN,
        enabled ? RELAY_ON : RELAY_OFF
    );

    Serial.print("Relay CH1 (Fan): ");
    Serial.println(enabled ? "ON" : "OFF");
}

// =====================================================
// 6. PUMP CONTROL
// =====================================================

void setPump(bool enabled) {

    if (pumpActive == enabled) {
        return;
    }

    pumpActive = enabled;

    digitalWrite(
        RELAY_CH2_PIN,
        enabled ? RELAY_ON : RELAY_OFF
    );

    Serial.print("Relay CH2 (Pump): ");
    Serial.println(enabled ? "ON" : "OFF");
}

// =====================================================
// 7. START PUMP SEQUENCE
// =====================================================

void startPumpSequence() {

    if (pumpSequenceActive) {
        return;
    }

    if (fanActive) {
        return;
    }

    pumpSequenceActive = true;
    pumpCycleCount = 1;
    pumpTimer = millis();

    setPump(true);

    Serial.println();
    Serial.println("======================================");
    Serial.println("PUMP SEQUENCE STARTED");
    Serial.println("Pump ON  : 2 seconds");
    Serial.println("Pump OFF : 1 second");
    Serial.println("Cycles   : 3");
    Serial.println("======================================");
    Serial.println("Pump Cycle #1 STARTED");
}

// =====================================================
// 8. UPDATE PUMP SEQUENCE
// =====================================================

void updatePumpSequence() {

    if (!pumpSequenceActive) {
        return;
    }

    unsigned long now = millis();

    if (pumpActive) {

        if (now - pumpTimer >= PUMP_ON_TIME) {

            setPump(false);

            pumpTimer = now;

            Serial.print("Pump Cycle #");
            Serial.print(pumpCycleCount);
            Serial.println(" ON completed - 1 sec OFF");
        }
    }

    else {

        if (now - pumpTimer >= PUMP_OFF_TIME) {

            if (pumpCycleCount >= PUMP_CYCLES) {

                pumpSequenceActive = false;
                pumpCycleCount = 0;

                setPump(false);

                Serial.println("======================================");
                Serial.println("PUMP SEQUENCE COMPLETED");
                Serial.println("Pump OFF");
                Serial.println("======================================");

                return;
            }

            pumpCycleCount++;

            setPump(true);

            pumpTimer = now;

            Serial.print("Pump Cycle #");
            Serial.print(pumpCycleCount);
            Serial.println(" STARTED - 2 sec ON");
        }
    }
}

// =====================================================
// 9. BLE PROVISIONING
// =====================================================

class MyCallbacks : public BLECharacteristicCallbacks {

    void onWrite(BLECharacteristic *pCharacteristic) {

        String value =
            String(
                pCharacteristic->getValue().c_str()
            );

        String uuid =
            pCharacteristic->getUUID().toString();

        if (uuid.equalsIgnoreCase(SSID_CHAR_UUID)) {

            receivedSSID = value;
            receivedSSID.trim();

            Serial.print("BLE: SSID Received: ");
            Serial.println(receivedSSID);
        }

        else if (uuid.equalsIgnoreCase(PASS_CHAR_UUID)) {

            receivedPASS = value;
            receivedPASS.trim();

            Serial.println("BLE: Password Received (Hidden)");
        }

        else if (uuid.equalsIgnoreCase(RID_CHAR_UUID)) {

            receivedRID = value;
            receivedRID.trim();

            Serial.print("BLE: Restaurant ID Received: ");
            Serial.println(receivedRID);
        }
    }
};

// =====================================================
// 10. START BLE PROVISIONING
// =====================================================

void startProvisioning() {

    isProvisioning = true;

    Serial.println();
    Serial.println("Starting BLE Setup Mode (ScentGuard-ESP32)");

    BLEDevice::init("ScentGuard-ESP32");

    BLEServer *pServer =
        BLEDevice::createServer();

    BLEService *pService =
        pServer->createService(SERVICE_UUID);

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

    pSSID->setCallbacks(new MyCallbacks());
    pPASS->setCallbacks(new MyCallbacks());
    pRID->setCallbacks(new MyCallbacks());

    pService->start();

    BLEAdvertising *pAdvertising =
        BLEDevice::getAdvertising();

    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->start();

    Serial.println("Waiting for App connection...");
}

// =====================================================
// 11. LOAD SAVED WIFI/RID
// =====================================================

bool loadCredentials() {

    preferences.begin("scentguard", true);

    String ssid =
        preferences.getString("ssid", "");

    String pass =
        preferences.getString("pass", "");

    activeRestaurantId =
        preferences.getString("rid", "");

    preferences.end();

    if (ssid == "" || activeRestaurantId == "") {
        return false;
    }

    Serial.println();
    Serial.println(
        "Credentials loaded. Connecting to: " + ssid
    );

    WiFi.begin(
        ssid.c_str(),
        pass.c_str()
    );

    unsigned long start = millis();

    while (
        WiFi.status() != WL_CONNECTED &&
        millis() - start < 15000
    ) {

        delay(500);
        Serial.print(".");
    }

    Serial.println();

    return WiFi.status() == WL_CONNECTED;
}

// =====================================================
// 12. TIME
// =====================================================

String getTimestamp() {

    time_t now = time(nullptr);

    if (now < 100000) {

        configTime(
            0,
            0,
            "pool.ntp.org",
            "time.nist.gov"
        );

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

    return String(timestamp);
}

// =====================================================
// 13. HISTORY SLOT
// =====================================================

String getSlotID() {

    struct tm timeinfo;

    if (!getLocalTime(&timeinfo)) {

        return "snap_" + String(millis());
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

    String id = String(buf);

    if (slotMin < 10) {
        id += "0";
    }

    id += String(slotMin);

    return id;
}

// =====================================================
// 14. TIME SYNC
// =====================================================

void syncTime() {

    configTime(
        0,
        0,
        "pool.ntp.org",
        "time.nist.gov"
    );

    Serial.print("Syncing Time");

    time_t now = time(nullptr);

    int retries = 0;

    while (
        now < 8 * 3600 * 2 &&
        retries < 15
    ) {

        delay(300);

        Serial.print(".");

        now = time(nullptr);

        retries++;
    }

    if (now >= 8 * 3600 * 2) {

        Serial.println("\nTime OK!");
    }

    else {

        Serial.println(
            "\nTime sync continuing in background..."
        );
    }
}

// =====================================================
// 15. FIREBASE USER CONFIGURATION
// =====================================================

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
            "fanMode,pumpMode,thresholdWarn,thresholdDanger"
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
                "fields/pumpMode/stringValue"
            )
        ) {

            currentPumpMode =
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
            ">> Config [RID:%s] FanMode=%s PumpMode=%s Warn=%d Danger=%d\n",
            activeRestaurantId.c_str(),
            currentFanMode.c_str(),
            currentPumpMode.c_str(),
            thresholdWarn,
            thresholdDanger
        );
    }
}

// =====================================================
// 16. TELEMETRY
// =====================================================

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
        fanActive ? "ON" : "OFF"
    );

    content.set(
        "fields/pumpStatus/stringValue",
        pumpActive ? "ON" : "OFF"
    );

    String ts = getTimestamp();

    if (ts != "") {

        content.set(
            "fields/lastSeen/timestampValue",
            ts
        );
    }

    String path =
        "restaurants/" +
        activeRestaurantId;

    Firebase.Firestore.patchDocument(
        &fbdo_telem,
        PROJECT_ID,
        "",
        path.c_str(),
        content.raw(),
        "currentGasPpm,airStatus,fanStatus,pumpStatus,lastSeen"
    );
}

// =====================================================
// 17. HISTORY
// =====================================================

void uploadHistorySnapshot(
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
        fanActive ? "ON" : "OFF"
    );

    content.set(
        "fields/pumpStatus/stringValue",
        pumpActive ? "ON" : "OFF"
    );

    content.set(
        "fields/fanMode/stringValue",
        currentFanMode
    );

    String ts = getTimestamp();

    if (ts != "") {

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

    Firebase.Firestore.patchDocument(
        &fbdo_hist,
        PROJECT_ID,
        "",
        path.c_str(),
        content.raw(),
        "currentGasPpm,airStatus,fanStatus,pumpStatus,fanMode,timestamp"
    );
}

// =====================================================
// 18. SETUP
// =====================================================

void setup() {

    WRITE_PERI_REG(
        RTC_CNTL_BROWN_OUT_REG,
        0
    );

    Serial.begin(115200);

    pinMode(
        RELAY_CH1_PIN,
        OUTPUT
    );

    digitalWrite(
        RELAY_CH1_PIN,
        RELAY_OFF
    );

    pinMode(
        RELAY_CH2_PIN,
        OUTPUT
    );

    digitalWrite(
        RELAY_CH2_PIN,
        RELAY_OFF
    );

    pinMode(
        MQ135_PIN,
        INPUT
    );

    pinMode(
        GREEN_LED,
        OUTPUT
    );

    pinMode(
        RED_LED,
        OUTPUT
    );

    pinMode(
        BOOT_BUTTON,
        INPUT_PULLUP
    );

    if (digitalRead(BOOT_BUTTON) == LOW) {

        Serial.println(
            "Reset Mode... Hold 5s"
        );

        delay(5000);

        if (digitalRead(BOOT_BUTTON) == LOW) {

            preferences.begin(
                "scentguard",
                false
            );

            preferences.clear();

            preferences.end();

            Serial.println(
                "NVS Cleared. Restarting..."
            );

            ESP.restart();
        }
    }

    if (!loadCredentials()) {

        startProvisioning();
    }

    else {

        syncTime();

        config.api_key = API_KEY;
        config.database_url = DATABASE_URL;
        config.token_status_callback =
            tokenStatusCallback;

        Firebase.signUp(
            &config,
            &auth,
            "",
            ""
        );

        Firebase.begin(
            &config,
            &auth
        );

        Firebase.reconnectWiFi(true);

        Serial.println(
            "Firebase Ready."
        );
    }
}

// =====================================================
// 19. MAIN LOOP
// =====================================================

void loop() {

    unsigned long now = millis();

    // =================================================
    // BOOT BUTTON RESET
    // =================================================

    static unsigned long bootPressStart = 0;

    if (digitalRead(BOOT_BUTTON) == LOW) {

        if (bootPressStart == 0) {

            bootPressStart = now;

            Serial.println(
                "\n[RESET] Hold BOOT for 5 seconds..."
            );
        }

        else if (
            now - bootPressStart >= 5000UL
        ) {

            Serial.println(
                "\n[RESET] Clearing WiFi/RID..."
            );

            preferences.begin(
                "scentguard",
                false
            );

            preferences.clear();

            preferences.end();

            delay(1000);

            ESP.restart();
        }
    }

    else {

        bootPressStart = 0;
    }

    // =================================================
    // BLE PROVISIONING
    // =================================================

    if (isProvisioning) {

        digitalWrite(
            RED_LED,
            (now / 500) % 2 == 0
        );

        if (
            receivedSSID != "" &&
            receivedPASS != "" &&
            receivedRID != ""
        ) {

            Serial.println(
                "\nTesting connection..."
            );

            WiFi.disconnect(true);

            delay(1000);

            WiFi.begin(
                receivedSSID.c_str(),
                receivedPASS.c_str()
            );

            unsigned long start =
                millis();

            while (
                WiFi.status() != WL_CONNECTED &&
                millis() - start < 10000
            ) {

                delay(500);

                Serial.print(".");
            }

            if (
                WiFi.status() ==
                WL_CONNECTED
            ) {

                preferences.begin(
                    "scentguard",
                    false
                );

                preferences.putString(
                    "ssid",
                    receivedSSID
                );

                preferences.putString(
                    "pass",
                    receivedPASS
                );

                preferences.putString(
                    "rid",
                    receivedRID
                );

                preferences.end();

                Serial.println(
                    "\nSuccess! Restarting..."
                );

                delay(2000);

                ESP.restart();
            }

            else {

                Serial.println(
                    "\nFailed. Waiting for new credentials..."
                );

                receivedSSID = "";
                receivedPASS = "";
                receivedRID = "";
            }
        }

        return;
    }

    // =================================================
    // MQ135 READING
    // =================================================

    int rawGas =
        analogRead(MQ135_PIN);

    if (smoothedGasPpm < 0) {

        smoothedGasPpm =
            (float)rawGas;
    }

    else {

        smoothedGasPpm =
            (smoothedGasPpm * 0.80f) +
            ((float)rawGas * 0.20f);
    }

    int gasValue =
        (int)smoothedGasPpm;

    // =================================================
    // AIR STATUS
    // Firebase thresholds are used
    // =================================================

    String airStatus =
        lastInternalStatus;

    if (lastInternalStatus == "SAFE") {

        if (gasValue >= thresholdDanger) {

            airStatus = "DANGER";
        }

        else if (
            gasValue >= thresholdWarn
        ) {

            airStatus = "WARN";
        }
    }

    else if (lastInternalStatus == "WARN") {

        if (
            gasValue >= thresholdDanger
        ) {

            airStatus = "DANGER";
        }

        else if (
            gasValue < thresholdWarn
        ) {

            airStatus = "SAFE";
        }
    }

    else if (lastInternalStatus == "DANGER") {

        if (
            gasValue < thresholdDanger
        ) {

            airStatus =
                (gasValue >= thresholdWarn)
                ? "WARN"
                : "SAFE";
        }
    }

    lastInternalStatus =
        airStatus;

    // =================================================
    // FAN CONTROL (Manager Role Manual Override + Auto)
    // =================================================
    // Manager Role Controls:
    // - ON   : Forces Relay 1 (Fan) ON manually
    // - OFF  : Forces Relay 1 (Fan) OFF manually
    // - AUTO : Fan operates automatically based on MQ135 air status (WARN or DANGER)
    // =================================================

    bool fanShouldBeOn = false;

    if (currentFanMode == "ON") {
        fanShouldBeOn = true;
    } else if (currentFanMode == "OFF") {
        fanShouldBeOn = false;
    } else { // "AUTO"
        fanShouldBeOn = (airStatus == "WARN" || airStatus == "DANGER");
    }

    setFan(fanShouldBeOn);

    // =================================================
    // DETECT FAN STOP (For Auto-Pump Trigger)
    // =================================================

    if (
        previousFanActive &&
        !fanActive
    ) {

        Serial.println();
        Serial.println(
            "Fan stopped."
        );

        if (currentPumpMode == "AUTO" || currentPumpMode == "ON") {
            startPumpSequence();
        }
    }

    previousFanActive =
        fanActive;

    // =================================================
    // PUMP CONTROL (Manager Role Manual Override)
    // =================================================

    if (currentPumpMode == "ON" && !pumpSequenceActive && !fanActive) {
        startPumpSequence();
    } else if (currentPumpMode == "OFF" && pumpSequenceActive) {
        pumpSequenceActive = false;
        pumpCycleCount = 0;
        setPump(false);
        Serial.println("Pump sequence stopped by Manager manual control.");
    }

    updatePumpSequence();

    // =================================================
    // LEDs
    // =================================================

    digitalWrite(
        GREEN_LED,
        airStatus == "SAFE"
    );

    digitalWrite(
        RED_LED,
        airStatus != "SAFE"
    );

    // =================================================
    // WIFI RECOVERY
    // =================================================

    if (
        WiFi.status() != WL_CONNECTED
    ) {

        if (wifiLostTime == 0) {

            wifiLostTime = now;
        }

        else if (
            now - wifiLostTime >=
            WIFI_RECOVERY_TIMEOUT &&
            !wifiLossHandled
        ) {

            Serial.println(
                "\n[WATCHDOG] WiFi lost for 60s. Entering BLE recovery..."
            );

            wifiLossHandled = true;

            startProvisioning();

            return;
        }
    }

    else {

        wifiLostTime = 0;
        wifiLossHandled = false;
    }

    // =================================================
    // SERIAL MONITOR
    // =================================================

    if (
        now - lastSerial >=
        SERIAL_INTERVAL
    ) {

        lastSerial = now;

        Serial.println(
            "--------------------------------------"
        );

        Serial.printf(
            "[MQ135] Gas: %d | Raw: %d | Status: %s\n",
            gasValue,
            rawGas,
            airStatus.c_str()
        );

        Serial.printf(
            "[FAN] Relay 1: %s | Manager Mode: %s\n",
            fanActive ? "ON" : "OFF",
            currentFanMode.c_str()
        );

        Serial.printf(
            "[PUMP] Relay 2: %s | Manager Mode: %s | Cycle: %d/%d\n",
            pumpActive ? "ON" : "OFF",
            currentPumpMode.c_str(),
            pumpCycleCount,
            PUMP_CYCLES
        );

        Serial.printf(
            "[CONFIG] Warn: %d | Danger: %d\n",
            thresholdWarn,
            thresholdDanger
        );

        Serial.printf(
            "[RID] %s\n",
            activeRestaurantId.c_str()
        );

        Serial.printf(
            "[WIFI] %s\n",
            WiFi.status() == WL_CONNECTED
            ? "Connected"
            : "Disconnected"
        );

        Serial.printf(
            "[FIREBASE] %s\n",
            Firebase.ready()
            ? "Connected"
            : "Offline"
        );
    }

    // =================================================
    // FIREBASE SYNC
    // =================================================

    if (Firebase.ready()) {

        if (
            now - lastConfig >=
            CONFIG_INTERVAL
        ) {

            lastConfig = now;

            readRemoteConfig();
        }

        if (
            now - lastTelem >=
            TELEMETRY_INTERVAL
        ) {

            lastTelem = now;

            uploadTelemetry(
                gasValue,
                airStatus
            );
        }

        if (
            now - lastHist >=
            HISTORY_INTERVAL
        ) {

            lastHist = now;

            uploadHistorySnapshot(
                gasValue,
                airStatus
            );
        }
    }

    delay(10);
}
