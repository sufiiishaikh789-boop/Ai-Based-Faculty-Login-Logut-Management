package com.example.facultyattendance.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;
import androidx.core.util.Consumer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Requests ONE fresh fix from the GPS provider with a timeout. Uses AndroidX's
 * LocationManagerCompat.getCurrentLocation (works on all supported API levels) so the app needs
 * no Google Play services. Blocking: call from a background thread.
 */
public final class AndroidLocationSource implements LocationSource {
    private final Context context;
    private final long waitMillis;

    public AndroidLocationSource(Context context, long waitMillis) {
        this.context = context.getApplicationContext();
        this.waitMillis = waitMillis;
    }

    @SuppressLint("MissingPermission")
    @Override
    public LocationFetchResult fetch() {
        boolean fine = granted(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!fine && !coarse) return LocationFetchResult.failure(LocationFetchResult.Status.PERMISSION_DENIED);
        if (!fine) return LocationFetchResult.failure(LocationFetchResult.Status.APPROXIMATE_ONLY);

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return LocationFetchResult.failure(LocationFetchResult.Status.ERROR);
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            return LocationFetchResult.failure(LocationFetchResult.Status.LOCATION_DISABLED);
        }
        if (!lm.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
            return LocationFetchResult.failure(LocationFetchResult.Status.ERROR);
        }

        final AtomicReference<Location> holder = new AtomicReference<Location>();
        final CountDownLatch latch = new CountDownLatch(1);
        CancellationSignal cancel = new CancellationSignal();
        try {
            LocationManagerCompat.getCurrentLocation(lm, LocationManager.GPS_PROVIDER, cancel,
                    new Executor() {
                        @Override public void execute(Runnable r) { r.run(); }
                    },
                    new Consumer<Location>() {
                        @Override public void accept(Location l) { holder.set(l); latch.countDown(); }
                    });
            if (!latch.await(waitMillis, TimeUnit.MILLISECONDS)) {
                cancel.cancel();
                return LocationFetchResult.failure(LocationFetchResult.Status.TIMEOUT);
            }
        } catch (InterruptedException e) {
            cancel.cancel();
            Thread.currentThread().interrupt();
            return LocationFetchResult.failure(LocationFetchResult.Status.ERROR);
        } catch (SecurityException e) {
            return LocationFetchResult.failure(LocationFetchResult.Status.PERMISSION_DENIED);
        }

        Location l = holder.get();
        if (l == null) return LocationFetchResult.failure(LocationFetchResult.Status.TIMEOUT);
        long ageMs = (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000L;
        double acc = l.hasAccuracy() ? l.getAccuracy() : Double.NaN;
        return LocationFetchResult.ok(new LocationSnapshot(l.getLatitude(), l.getLongitude(), acc, ageMs, isMock(l)));
    }

    /** Mock detection is a best-effort signal only: rooted devices and some spoofing tools evade it. */
    @SuppressWarnings("deprecation")
    private static boolean isMock(Location l) {
        return Build.VERSION.SDK_INT >= 31 ? l.isMock() : l.isFromMockProvider();
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
