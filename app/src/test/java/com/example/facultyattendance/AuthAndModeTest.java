package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.facultyattendance.authentication.AdminSession;
import com.example.facultyattendance.authentication.LoginThrottle;
import com.example.facultyattendance.authentication.PasswordHasher;
import com.example.facultyattendance.authentication.TemplateCodec;
import com.example.facultyattendance.domain.FacultyRecord;
import com.example.facultyattendance.util.ModeResolver;
import com.example.facultyattendance.util.ModeResolver.Mode;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class AuthAndModeTest {
    @Test public void correctPasswordVerifiesAndWrongOneDoesNot() {
        String stored = PasswordHasher.hash("correct horse 42".toCharArray(), 2000);
        assertTrue(PasswordHasher.verify("correct horse 42".toCharArray(), stored));
        assertFalse(PasswordHasher.verify("correct horse 43".toCharArray(), stored));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "garbage"));
        assertFalse(stored.contains("correct"));
    }

    @Test public void hashesAreSalted() {
        assertFalse(PasswordHasher.hash("same password!".toCharArray(), 2000)
                .equals(PasswordHasher.hash("same password!".toCharArray(), 2000)));
    }

    @Test public void passwordPolicyRejectsShortAndRepetitivePasswords() {
        assertFalse(PasswordHasher.isAcceptable("short".toCharArray()));
        assertFalse(PasswordHasher.isAcceptable("aaaaaaaaaaaa".toCharArray()));
        assertTrue(PasswordHasher.isAcceptable("Tr0ub4dor&3x".toCharArray()));
    }

    @Test public void throttleLocksAfterRepeatedFailuresAndResetsOnSuccess() {
        LoginThrottle t = new LoginThrottle(0, 0);
        for (int i = 0; i < 4; i++) t.recordFailure(1000);
        assertEquals(0, t.remainingLockMillis(1000));
        t.recordFailure(1000);
        assertEquals(30_000, t.remainingLockMillis(1000));
        t.recordFailure(1000);
        assertEquals(60_000, t.remainingLockMillis(1000));
        assertEquals(0, t.remainingLockMillis(1000 + 61_000));
        t.recordSuccess();
        assertEquals(0, t.failures());
    }

    @Test public void adminSessionExpiresWhenIdle() {
        MutableClock c = new MutableClock(Instant.parse("2026-03-02T04:00:00Z"));
        AdminSession s = new AdminSession(c, Duration.ofMinutes(5));
        assertFalse(s.touchIfActive());
        s.signIn();
        c.advance(Duration.ofMinutes(4));
        assertTrue(s.touchIfActive());           // refreshes the timer
        c.advance(Duration.ofMinutes(4));
        assertTrue(s.touchIfActive());
        c.advance(Duration.ofMinutes(6));
        assertFalse(s.touchIfActive());
        s.signIn(); s.signOut();
        assertFalse(s.touchIfActive());
    }

    @Test public void templateCodecRoundTrips() {
        float[] in = {0.5f, -1.25f, 3.0f};
        float[] out = TemplateCodec.toFloats(TemplateCodec.toBytes(in));
        assertEquals(3, out.length);
        for (int i = 0; i < 3; i++) assertEquals(in[i], out[i], 0f);
    }

    @Test public void uidValidation() {
        assertTrue(FacultyRecord.isValidUid("FAC-001"));
        assertFalse(FacultyRecord.isValidUid("ab"));
        assertFalse(FacultyRecord.isValidUid("has space"));
        assertFalse(FacultyRecord.isValidUid("semi;colon"));
        assertFalse(FacultyRecord.isValidUid(null));
    }

    @Test public void releaseBuildCanNeverResolveToDemoMode() {
        // demoSupportCompiledIn=false models a release build, even if the stored setting says "demo on".
        assertEquals(Mode.NOT_READY, ModeResolver.resolve(false, true, false, false).mode);
        assertEquals(Mode.CONFIGURED, ModeResolver.resolve(false, true, true, true).mode);
    }

    @Test public void demoOnlyInDebugAndOnlyWhenEnabled() {
        assertEquals(Mode.DEMO, ModeResolver.resolve(true, true, false, false).mode);
        assertEquals(Mode.NOT_READY, ModeResolver.resolve(true, false, false, true).mode);
        assertEquals(2, ModeResolver.resolve(true, false, false, false).blockers.size());
    }
}
