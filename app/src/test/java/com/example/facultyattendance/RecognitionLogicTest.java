package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.facultyattendance.camera.EnrollmentCollector;
import com.example.facultyattendance.camera.FaceMatcher;
import com.example.facultyattendance.camera.FaceMatcher.Candidate;
import com.example.facultyattendance.camera.FaceMatcher.Status;
import com.example.facultyattendance.camera.FrameStabilizer;
import com.example.facultyattendance.camera.LivenessTracker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Synthetic vectors only; no real faces or biometric data are used anywhere in the tests. */
public class RecognitionLogicTest {
    private static float[] v(float... x) { return x; }

    private List<Candidate> two() {
        List<Candidate> c = new ArrayList<Candidate>();
        c.add(new Candidate("A", FaceMatcher.l2Normalize(v(1, 0, 0, 0))));
        c.add(new Candidate("B", FaceMatcher.l2Normalize(v(0, 1, 0, 0))));
        return c;
    }

    @Test public void clearMatchIsAccepted() {
        FaceMatcher.MatchResult r = FaceMatcher.match(v(0.99f, 0.05f, 0, 0), two(), 0.7f, 0.05f);
        assertEquals(Status.MATCH, r.status);
        assertEquals("A", r.uid);
    }

    @Test public void belowThresholdIsNoMatch() {
        FaceMatcher.MatchResult r = FaceMatcher.match(v(0.5f, 0.5f, 0.5f, 0.5f), two(), 0.9f, 0.05f);
        assertEquals(Status.NO_MATCH, r.status);
        assertEquals(null, r.uid);
    }

    @Test public void lookAlikeBetweenTwoPeopleIsAmbiguousNotAMatch() {
        List<Candidate> c = new ArrayList<Candidate>();
        c.add(new Candidate("A", FaceMatcher.l2Normalize(v(1, 0.10f, 0, 0))));
        c.add(new Candidate("B", FaceMatcher.l2Normalize(v(1, 0.12f, 0, 0))));
        FaceMatcher.MatchResult r = FaceMatcher.match(v(1, 0.11f, 0, 0), c, 0.7f, 0.05f);
        assertEquals(Status.AMBIGUOUS, r.status);
        assertEquals(null, r.uid);
    }

    @Test public void noTemplatesOrDifferentModelDimensionNeverMatches() {
        assertEquals(Status.NO_TEMPLATES, FaceMatcher.match(v(1, 0), new ArrayList<Candidate>(), 0.7f, 0.05f).status);
        assertEquals(Status.NO_TEMPLATES, FaceMatcher.match(v(1, 0), two(), 0.7f, 0.05f).status);
    }

    @Test public void enrollmentAveragesConsistentSamples() {
        EnrollmentCollector c = new EnrollmentCollector(3);
        c.add(v(1, 0.05f, 0)); c.add(v(1, -0.05f, 0)); assertFalse(c.isComplete());
        c.add(v(1, 0.0f, 0.05f)); assertTrue(c.isComplete());
        float[] t = c.buildTemplate();
        assertEquals(1.0f, FaceMatcher.cosine(t, t), 1e-5f);
        assertTrue(FaceMatcher.cosine(t, v(1, 0, 0)) > 0.99f);
    }

    @Test public void enrollmentRejectsInconsistentSamples() {
        EnrollmentCollector c = new EnrollmentCollector(3);
        c.add(v(1, 0, 0)); c.add(v(0, 1, 0)); c.add(v(0, 0, 1));
        try { c.buildTemplate(); fail("should reject"); } catch (IllegalStateException expected) { }
    }

    @Test public void enrollmentRejectsIncompleteCapture() {
        EnrollmentCollector c = new EnrollmentCollector(3);
        c.add(v(1, 0, 0));
        try { c.buildTemplate(); fail("should reject"); } catch (IllegalStateException expected) { }
    }

    @Test public void stabilizerNeedsConsecutiveSameIdentity() {
        FrameStabilizer s = new FrameStabilizer(3);
        assertFalse(s.offer("A")); assertFalse(s.offer("A"));
        assertFalse(s.offer("B"));                    // change resets
        assertFalse(s.offer("B")); assertTrue(s.offer("B"));
        assertFalse(s.offer(null));
        assertFalse(s.offer("B"));                    // null reset the streak
    }

    @Test public void blinkSequencePassesAndStaticFaceDoesNot() {
        LivenessTracker t = new LivenessTracker(5000);
        for (int i = 0; i < 20; i++) assertFalse(t.update(i * 100L, 0.95f, 0.95f));   // eyes always open
        assertFalse(t.isPassed());
        t.reset();
        assertFalse(t.update(0, 0.9f, 0.9f));
        assertFalse(t.update(200, 0.1f, 0.1f));
        assertTrue(t.update(400, 0.9f, 0.9f));
        assertTrue(t.isPassed());
    }

    @Test public void blinkOutsideWindowOrWithUnknownProbabilitiesDoesNotPass() {
        LivenessTracker t = new LivenessTracker(1000);
        t.update(0, 0.9f, 0.9f);
        t.update(500, 0.1f, 0.1f);
        assertFalse(t.update(2000, 0.9f, 0.9f));       // too slow, sequence restarted
        t.reset();
        assertFalse(t.update(0, -1f, -1f));
        assertFalse(t.update(100, -1f, 0.9f));
    }
}
