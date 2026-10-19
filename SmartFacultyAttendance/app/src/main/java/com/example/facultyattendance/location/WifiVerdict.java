package com.example.facultyattendance.location;

public enum WifiVerdict {
    TRUSTED, NOT_CONFIGURED, WIFI_OFF, NOT_CONNECTED, SSID_UNREADABLE, SSID_MISMATCH, BSSID_MISMATCH;

    public boolean isTrusted() { return this == TRUSTED; }

    public String userMessage() {
        switch (this) {
            case TRUSTED: return "Connected to the campus network.";
            case NOT_CONFIGURED: return "No campus Wi-Fi is configured.";
            case WIFI_OFF: return "Wi-Fi is turned off.";
            case NOT_CONNECTED: return "Not connected to a Wi-Fi network.";
            case SSID_UNREADABLE: return "The Wi-Fi name cannot be read. Grant precise location permission and turn on Location.";
            case SSID_MISMATCH: return "Connected to a network that is not the campus network.";
            default: return "Connected access point is not on the approved list.";
        }
    }
}
