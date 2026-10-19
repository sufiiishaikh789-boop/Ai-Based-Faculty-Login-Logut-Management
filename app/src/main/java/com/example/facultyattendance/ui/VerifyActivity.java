package com.example.facultyattendance.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.facultyattendance.R;
import com.example.facultyattendance.camera.FaceAnalyzer;
import com.example.facultyattendance.camera.FaceEmbedder;
import com.example.facultyattendance.camera.FaceMatcher;
import com.example.facultyattendance.camera.FaceScanState;
import com.example.facultyattendance.config.RecognitionPolicy;
import com.example.facultyattendance.data.repository.OpResult;
import com.example.facultyattendance.databinding.ActivityVerifyBinding;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.EligibilityResult;
import com.example.facultyattendance.domain.IdentityResult;
import com.example.facultyattendance.domain.ScanResult;
import com.example.facultyattendance.util.ModeResolver;
import com.example.facultyattendance.util.TimeFormat;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The faculty-facing screen. Flow (VERIFY mode):
 *   camera permission -> front camera preview -> mode/readiness check -> campus eligibility check
 *   -> face analysis -> ScanResult from AttendanceService -> result card -> dashboard.
 * The same screen in ENROLL mode (opened by an administrator) captures a face template instead.
 *
 * Safety properties:
 *  - Attendance is written in exactly one place (handleVerified), once per verified analysis run;
 *    FaceAnalyzer latches after its single callback, so repeated camera frames cannot repeat it.
 *  - runId invalidates callbacks that arrive after a retry, stop, or screen change.
 *  - Camera, analyzer and executor are released in onStop/onDestroy.
 */
public class VerifyActivity extends BaseActivity implements FaceAnalyzer.Listener {
    public static final String EXTRA_ENROLL_UID = "enroll_uid";

    private ActivityVerifyBinding b;
    private String enrollUid;                         // non-null => ENROLL mode
    private ExecutorService analysisExecutor;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private FaceAnalyzer analyzer;
    private ModeResolver.Decision decision;
    private FaceEmbedder embedder;
    private EligibilityResult eligibility;
    private boolean cameraBound;
    private boolean askedCameraOnce;
    private boolean askedLocationOnce;
    private boolean lastPromptWasLocation;
    private int runId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> startFlow());
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> checkCampus(runId));

    private boolean enrolling() { return enrollUid != null; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityVerifyBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        enrollUid = getIntent().getStringExtra(EXTRA_ENROLL_UID);
        analysisExecutor = Executors.newSingleThreadExecutor();

        b.screenTitle.setText(enrolling() ? R.string.enroll_title : R.string.verify_title);
        b.btnAdmin.setVisibility(enrolling() ? View.GONE : View.VISIBLE);
        b.btnAdmin.setOnClickListener(v -> startActivity(new Intent(this, AdminLoginActivity.class)));
        b.btnRetry.setOnClickListener(v -> startFlow());
        b.btnPermission.setOnClickListener(v -> onPermissionButton());
    }

    @Override
    protected void onStart() {
        super.onStart();
        startFlow();
    }

    @Override
    protected void onStop() {
        runId++;                       // ignore everything still in flight
        handler.removeCallbacksAndMessages(null);
        stopAnalysis();
        if (cameraProvider != null) cameraProvider.unbindAll();   // release the camera while in background
        cameraBound = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        analysisExecutor.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ flow

    private void startFlow() {
        final int id = ++runId;
        stopAnalysis();
        eligibility = null;
        setButtons(false, false);
        showState(FaceScanState.INITIALIZING, null);

        if (!hasPermission(Manifest.permission.CAMERA)) {
            lastPromptWasLocation = false;
            showState(FaceScanState.PERMISSION_REQUIRED, null);
            setButtons(false, true);
            if (!askedCameraOnce) { askedCameraOnce = true; cameraPermission.launch(Manifest.permission.CAMERA); }
            return;
        }
        bindCamera(id);
    }

    private void bindCamera(final int id) {
        if (cameraBound) { afterCameraReady(id); return; }
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (id != runId || !alive()) return;
            try {
                cameraProvider = future.get();
                CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                if (!cameraProvider.hasCamera(selector)) {
                    showError("This device has no front camera.");
                    return;
                }
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(b.previewView.getSurfaceProvider());
                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis);
                cameraBound = true;
                afterCameraReady(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                showError("Camera start was interrupted.");
            } catch (ExecutionException | CameraInfoUnavailableException | RuntimeException e) {
                showError("The camera could not be started.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void afterCameraReady(final int id) {
        container.io.execute(() -> {
            final ModeResolver.Decision d = container.mode();
            final FaceEmbedder e = container.activeEmbedder(d.mode);
            ui(() -> {
                if (id != runId) return;
                decision = d;
                embedder = e;
                onModeResolved(id);
            });
        });
    }

    private void onModeResolved(int id) {
        boolean demo = enrolling() ? embedder.isDemo() : decision.mode == ModeResolver.Mode.DEMO;
        b.modeBanner.setVisibility(demo ? View.VISIBLE : View.GONE);

        if (enrolling()) {
            if (!embedder.isReady()) {
                showState(FaceScanState.NOT_READY, embedder.readinessMessage());
                setButtons(true, false);
                return;
            }
            startScanning(id, new ArrayList<FaceMatcher.Candidate>());
            return;
        }
        if (decision.mode == ModeResolver.Mode.NOT_READY) {
            StringBuilder sb = new StringBuilder();
            for (String s : decision.blockers) sb.append("• ").append(s).append('\n');
            sb.append("An administrator can finish setup via Admin.");
            showState(FaceScanState.NOT_READY, sb.toString().trim());
            setButtons(true, false);
            return;
        }
        boolean needsLocation = decision.mode == ModeResolver.Mode.CONFIGURED
                && !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        if (needsLocation && !askedLocationOnce) {
            askedLocationOnce = true;
            showState(FaceScanState.LOCATING_CAMPUS, "Precise location is needed to read the Wi-Fi name and, if required, GPS.");
            locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        checkCampus(id);
    }

    private void checkCampus(final int id) {
        if (id != runId || decision == null || !alive()) return;
        showState(FaceScanState.LOCATING_CAMPUS, null);
        container.campus.check(decision.mode == ModeResolver.Mode.DEMO, result -> {
            if (id != runId || !alive()) return;
            if (!result.isEligible()) {
                showState(FaceScanState.CAMPUS_REJECTED, result.message());
                boolean permissionIssue = result.outcome() == EligibilityResult.Outcome.PERMISSION_DENIED
                        || result.outcome() == EligibilityResult.Outcome.APPROXIMATE_ONLY;
                lastPromptWasLocation = true;
                setButtons(true, permissionIssue);
                return;
            }
            eligibility = result;
            loadCandidatesThenScan(id);
        });
    }

    private void loadCandidatesThenScan(final int id) {
        final String modelId = embedder.modelId();
        container.io.execute(() -> {
            final List<FaceMatcher.Candidate> candidates = container.faculty.loadCandidates(modelId);
            ui(() -> {
                if (id != runId) return;
                if (candidates.isEmpty()) {
                    showState(FaceScanState.NO_ENROLLED_FACES, null);
                    setButtons(true, false);
                } else {
                    startScanning(id, candidates);
                }
            });
        });
    }

    private void startScanning(int id, List<FaceMatcher.Candidate> candidates) {
        if (id != runId || imageAnalysis == null) return;
        RecognitionPolicy policy = container.settings.getRecognitionPolicy();
        analyzer = new FaceAnalyzer(embedder, policy,
                enrolling() ? FaceAnalyzer.Mode.ENROLL : FaceAnalyzer.Mode.VERIFY, candidates, this);
        imageAnalysis.setAnalyzer(analysisExecutor, analyzer);
        showState(enrolling() ? FaceScanState.ENROLLING : FaceScanState.SCANNING, null);
        b.scanOverlay.setScanning(true);
    }

    private void stopAnalysis() {
        if (analyzer != null) { analyzer.cancel(); analyzer = null; }
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        b.scanOverlay.setScanning(false);
    }

    // ------------------------------------------------------------------ analyzer callbacks (main thread)

    @Override public void onState(FaceScanState state) {
        if (analyzer == null) return;
        showState(state, null);
    }

    @Override public void onEnrollmentProgress(int collected, int needed) {
        if (analyzer == null) return;
        b.statusDetail.setText("Captured " + collected + " of " + needed + ". Hold still and keep looking at the camera.");
    }

    @Override
    public void onVerified(final String facultyUid, final float similarity) {
        stopAnalysis();
        showState(FaceScanState.VERIFYING, null);
        final ModeResolver.Mode mode = decision.mode;
        final EligibilityResult elig = eligibility;
        final FaceEmbedder emb = embedder;
        final int id = runId;
        container.io.execute(() -> {
            IdentityResult identity = IdentityResult.verified(facultyUid, similarity, emb.modelId(), emb.isDemo());
            final ScanResult result = container.attendanceService(mode).recordScan(identity, elig);
            ui(() -> { if (id == runId) showScanResult(result); });
        });
    }

    @Override
    public void onEnrollmentComplete(final float[] template) {
        stopAnalysis();
        showState(FaceScanState.VERIFYING, null);
        final String uid = enrollUid;
        final FaceEmbedder emb = embedder;
        final float threshold = container.settings.getRecognitionPolicy().similarityThreshold();
        final int id = runId;
        container.io.execute(() -> {
            final OpResult r = container.faculty.saveTemplate(uid, template, emb.modelId(), emb.isDemo(), threshold);
            Arrays.fill(template, 0f);                  // do not keep biometric data in memory longer than needed
            ui(() -> {
                if (id != runId) return;
                if (r.ok) { toast(r.message); setResult(RESULT_OK); finish(); }
                else { showError(r.message); }
            });
        });
    }

    @Override public void onFatal(String message) { stopAnalysis(); showError(message); }

    // ------------------------------------------------------------------ result handling

    private void showScanResult(ScanResult r) {
        ZoneId zone = container.settings.getAttendancePolicy().zone();
        switch (r.type()) {
            case LOGIN_RECORDED:
                showCustom(UiText.OK, "Logged in", "Welcome, " + r.facultyName() + ". Login time "
                        + TimeFormat.time(r.record().loginAt(), zone) + ".");
                goToDashboardSoon(r.facultyUid());
                break;
            case LOGOUT_RECORDED:
                showCustom(UiText.OK, "Logged out", "Goodbye, " + r.facultyName() + ". Logout time "
                        + TimeFormat.time(r.record().logoutAt(), zone) + ".");
                goToDashboardSoon(r.facultyUid());
                break;
            case DUPLICATE_IGNORED: {
                String wait = r.retryAfter() == null ? "" : " You can scan again in about "
                        + Math.max(1, (r.retryAfter().getSeconds() + 59) / 60) + " min.";
                showCustom(UiText.WARN, "Already recorded", r.message() + wait);
                if (r.facultyUid() != null) goToDashboardSoon(r.facultyUid());
                else setButtons(true, false);
                break;
            }
            default:
                showCustom(UiText.ERROR, "Attendance not recorded", r.message());
                setButtons(true, false);
                break;
        }
    }

    private void goToDashboardSoon(final String uid) {
        final int id = runId;
        handler.postDelayed(() -> {
            if (id != runId || !alive()) return;
            Intent i = new Intent(this, DashboardActivity.class);
            i.putExtra(DashboardActivity.EXTRA_UID, uid);
            startActivity(i);
        }, 1800);
    }

    // ------------------------------------------------------------------ rendering

    private void showState(FaceScanState s, @Nullable String detailOverride) {
        String detail = detailOverride != null ? detailOverride : UiText.detail(s);
        showCustom(UiText.kind(s), UiText.title(s, enrolling()), detail);
        boolean busy = s == FaceScanState.INITIALIZING || s == FaceScanState.LOCATING_CAMPUS || s == FaceScanState.VERIFYING;
        b.statusProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showCustom(int kind, String title, String detail) {
        b.statusTitle.setText(title);
        b.statusDetail.setText(detail);
        b.statusProgress.setVisibility(View.GONE);
        int color;
        switch (kind) {
            case UiText.OK: color = R.color.state_ok; break;
            case UiText.WARN: color = R.color.state_warn; break;
            case UiText.ERROR: color = R.color.state_error; break;
            default: color = R.color.state_info; break;
        }
        b.scanOverlay.setAccent(ContextCompat.getColor(this, color));
    }

    private void showError(String message) {
        showState(FaceScanState.ERROR, message);
        setButtons(true, false);
    }

    private void setButtons(boolean retry, boolean permission) {
        b.btnRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        b.btnPermission.setVisibility(permission ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ permissions

    private boolean hasPermission(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void onPermissionButton() {
        String perm = lastPromptWasLocation ? Manifest.permission.ACCESS_FINE_LOCATION : Manifest.permission.CAMERA;
        boolean canAsk = shouldShowRequestPermissionRationale(perm);
        if (lastPromptWasLocation) {
            if (canAsk) locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            else openAppSettings();      // permanently denied, or only approximate location was granted
        } else {
            if (canAsk) cameraPermission.launch(Manifest.permission.CAMERA);
            else openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }
}
