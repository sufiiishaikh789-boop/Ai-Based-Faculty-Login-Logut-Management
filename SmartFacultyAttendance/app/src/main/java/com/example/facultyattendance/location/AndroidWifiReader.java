package com.example.facultyattendance.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import android.location.LocationManager;

/**
 * Reads Wi-Fi state. Android only reveals the SSID/BSSID when the app holds precise location
 * permission AND device Location is switched on; otherwise the snapshot reports them as null and
 * the policy treats the network as unverifiable (it is never assumed to be the campus network).
 *
 * WifiManager.getConnectionInfo() is deprecated since API 31 but remains the simplest synchronous
 * call that still works; the replacement (ConnectivityManager.NetworkCallback with
 * FLAG_INCLUDE_LOCATION_INFO) is asynchronous. Documented as a known trade-off.
 */
public final class AndroidWifiReader {
    private final Context context;

    public AndroidWifiReader(Context context) { this.context = context.getApplicationContext(); }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    public WifiSnapshot read() {
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return new WifiSnapshot(false, false, null, null);
        boolean enabled = wm.isWifiEnabled();
        if (!enabled) return new WifiSnapshot(false, false, null, null);

        WifiInfo info = wm.getConnectionInfo();
        boolean connected = info != null && info.getNetworkId() != -1
                && info.getSupplicantState() == SupplicantState.COMPLETED;
        if (!connected) return new WifiSnapshot(true, false, null, null);

        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean locationOn = lm != null && LocationManagerCompat.isLocationEnabled(lm);
        if (!fine || !locationOn) return new WifiSnapshot(true, true, null, null);
        return new WifiSnapshot(true, true, info.getSSID(), info.getBSSID());
    }
}
