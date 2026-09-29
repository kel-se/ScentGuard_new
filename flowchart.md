# ScentGuard Vent System Operational Flowcharts

This document outlines the workflows for the two primary user roles in the ScentGuard system: the Manager and the Staff.

## 1. Manager Flowchart
The Manager role has full control over the system, including configuration and personnel management.

```mermaid
flowchart TD
    Start([Start]) --> Login[Login Screen]
    Login --> Auth{Authentication}
    Auth -- "Success" --> Dash[Manager Dashboard]
    
    Dash --> Monitor[View Real-Time Monitoring]
    
    %% Data Source Interaction
    ESP32([ESP32 Sensors]) -- "Raw Data" --> Firestore[(Firebase/Firestore)]
    Firestore -- "Real-time Telemetry" --> Monitor
    
    Monitor --> Check{Check Gas &<br/>Temp Levels}
    
    %% Decision Branches
    Check -- "Safe" --> Safe[Continue Normal Ventilation]
    Check -- "Warning" --> Warn[Adjust/Activate Ventilation<br/>& Update Status]
    Check -- "Danger" --> Danger[Activate Critical Alert<br/>& Ventilation]
    
    Safe --> UpdateDash[Update Dashboard]
    Warn --> UpdateDash
    Danger --> NotifyM[Notify Manager] --> UpdateDash
    
    UpdateDash --> Dash

    %% Management Operations
    Dash --> Ops{Manager Actions}
    Ops --> Profile[Manage Restaurant Profile]
    Ops --> MStaff[Manage Staff]
    Ops --> Config[Configure Thresholds]
    Ops --> Override[Manually Override Fan]
    Ops --> Logs[View System Logs]
    Ops --> Reports[View Analytics & Reports]
    
    %% Loops back to Dashboard
    Profile --> Dash
    MStaff --> Dash
    Config --> Dash
    Override --> Dash
    Logs --> Dash
    Reports --> Dash
```

---

## 2. Staff Flowchart
The Staff role focuses on monitoring and responding to immediate environmental conditions.

```mermaid
flowchart TD
    Start([Start]) --> Login[Login Screen]
    Login --> Auth{Authentication}
    Auth -- "Success" --> Dash[Staff Dashboard]
    
    Dash --> Monitor[View Real-Time Monitoring]
    
    %% Data Source Interaction
    ESP32([ESP32 Sensors]) -- "Raw Data" --> Firestore[(Firebase/Firestore)]
    Firestore -- "Real-time Telemetry" --> Monitor
    
    Monitor --> Check{Check Gas &<br/>Temp Levels}
    
    %% Decision Branches
    Check -- "Safe" --> Safe[Continue Normal Ventilation]
    Check -- "Warning" --> WarnUI[Display Warning UI]
    Check -- "Danger" --> DangerUI[Display Critical Alert UI<br/>& Notify Staff]
    
    Safe --> UpdateDash[Update Dashboard]
    WarnUI --> UpdateDash
    DangerUI --> UpdateDash
    
    UpdateDash --> Dash

    %% Staff Operations
    Dash --> Ops{Staff Actions}
    Ops --> Account[Manage/View Account]
    Ops --> MonitorInfo[Monitor Gas & Temp]
    Ops --> Status[View Fan & Device Status]
    Ops --> Control[Control Fan<br/>*If Permitted*]
    Ops --> Logs[View System Logs]
    Ops --> Alerts[Receive Notifications]
    
    %% Loops back to Dashboard
    Account --> Dash
    MonitorInfo --> Dash
    Status --> Dash
    Control --> Dash
    Logs --> Dash
    Alerts --> Dash
```
