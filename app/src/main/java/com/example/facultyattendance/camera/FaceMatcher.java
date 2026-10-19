package com.example.facultyattendance.camera;

import java.util.List;

/** Cosine-similarity matching of a probe embedding against enrolled templates (1:N identification). */
public final class FaceMatcher {
    public enum Status { MATCH, NO_MATCH, AMBIGUOUS, NO_TEMPLATES }

    public static final class Candidate {
        public final String uid;
        public final float[] template;
        public Candidate(String uid, float[] template) { this.uid = uid; this.template = template; }
    }

    public static final class MatchResult {
        public final Status status;
        public final String uid;      // only for MATCH
        public final float best;
        public final float second;
        MatchResult(Status status, String uid, float best, float second) {
            this.status = status; this.uid = uid; this.best = best; this.second = second;
        }
    }

    private FaceMatcher() { }

    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("embedding sizes differ");
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return -1f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }

    public static float[] l2Normalize(float[] v) {
        double n = 0;
        for (float f : v) n += f * f;
        n = Math.sqrt(n);
        if (n == 0) throw new IllegalArgumentException("zero vector");
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / n);
        return out;
    }

    public static float[] average(List<float[]> vectors) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("no vectors");
        float[] sum = new float[vectors.get(0).length];
        for (float[] v : vectors) {
            if (v.length != sum.length) throw new IllegalArgumentException("embedding sizes differ");
            for (int i = 0; i < sum.length; i++) sum[i] += v[i];
        }
        for (int i = 0; i < sum.length; i++) sum[i] /= vectors.size();
        return sum;
    }

    /** Best match must reach the threshold AND beat the runner-up by the margin (guards look-alikes). */
    public static MatchResult match(float[] probe, List<Candidate> candidates, float threshold, float margin) {
        if (candidates == null || candidates.isEmpty()) return new MatchResult(Status.NO_TEMPLATES, null, -1f, -1f);
        float best = -2f, second = -2f;
        String bestUid = null;
        for (Candidate c : candidates) {
            if (c.template.length != probe.length) continue;   // different model: never comparable
            float s = cosine(probe, c.template);
            if (s > best) { second = best; best = s; bestUid = c.uid; }
            else if (s > second) { second = s; }
        }
        if (bestUid == null) return new MatchResult(Status.NO_TEMPLATES, null, -1f, -1f);
        if (best < threshold) return new MatchResult(Status.NO_MATCH, null, best, second);
        if (second > -2f && best - second < margin) return new MatchResult(Status.AMBIGUOUS, null, best, second);
        return new MatchResult(Status.MATCH, bestUid, best, second);
    }
}
