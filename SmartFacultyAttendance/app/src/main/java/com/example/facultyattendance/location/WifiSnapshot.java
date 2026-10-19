package com.example.facultyattendance.location;

/** What the device reports about its Wi-Fi connection at one moment. */
public final class WifiSnapshot {
    private final boolean wifiEnabled;
    private final boolean connected;
    private final String ssid;    // null when unreadable (permission / location services off)
    private final String bssid;   // null when unreadable

    public WifiSnapshot(boolean wifiEnabled, boolean connected, String ssid, String bssid) {
        this.wifiEnabled = wifiEnabled;
        this.connected = connected;
        this.ssid = ssid;
        this.bssid = bssid;
    }

    public boolean wifiEnabled() { return wifiEnabled; }
    public boolean connected() { return connected; }
    public String ssid() { return ssid; }
    public String bssid() { return bssid; }
}
