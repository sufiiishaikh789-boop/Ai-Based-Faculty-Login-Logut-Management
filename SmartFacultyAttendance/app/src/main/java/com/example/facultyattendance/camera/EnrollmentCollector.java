package com.example.facultyattendance.camera;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers several embeddings of one person, checks they agree with each other, and averages them
 * into one L2-normalised template. Rejects unstable captures (e.g. different people, blur).
 */
public final class EnrollmentCollector {
    public static final float MIN_SELF_CONSISTENCY = 0.80f;

    private final int needed;
    private final List<float[]> samples = new ArrayList<float[]>();

    public EnrollmentCollector(int needed) { this.needed = needed; }

    public void add(float[] embedding) { if (!isComplete()) samples.add(FaceMatcher.l2Normalize(embedding)); }

    public int collected() { return samples.size(); }

    public int needed() { return needed; }

    public boolean isComplete() { return samples.size() >= needed; }

    public void clear() { samples.clear(); }

    /** @throws IllegalStateException when incomplete or when the samples do not agree. */
    public float[] buildTemplate() {
        if (!isComplete()) throw new IllegalStateException("not enough samples");
        float[] mean = FaceMatcher.l2Normalize(FaceMatcher.average(samples));
        for (float[] s : samples) {
            if (FaceMatcher.cosine(s, mean) < MIN_SELF_CONSISTENCY)
                throw new IllegalStateException("Captures were inconsistent. Retry with steady lighting and one face.");
        }
        return mean;
    }
}
