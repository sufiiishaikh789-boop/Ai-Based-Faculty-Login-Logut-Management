package com.example.facultyattendance.config;

/**
 * Tunable recognition parameters. The similarity threshold is model-specific: the default is a
 * PLACEHOLDER that must be calibrated with your own model and test population (see docs/MODEL_SETUP.md).
 */
public final class RecognitionPolicy {
    public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.70f;
    public static final float DEFAULT_AMBIGUITY_MARGIN = 0.05f;
    public static final int DEFAULT_CONSECUTIVE_MATCHES = 3;
    public static final int DEFAULT_ENROLLMENT_SAMPLES = 5;

    private final float similarityThreshold;
    private final float ambiguityMargin;
    private final int consecutiveMatches;
    private final boolean requireBlink;
    private final int enrollmentSamples;

    public RecognitionPolicy(float similarityThreshold, float ambiguityMargin,
                             int consecutiveMatches, boolean requireBlink, int enrollmentSamples) {
        if (!(similarityThreshold > 0f && similarityThreshold < 1f))
            throw new IllegalArgumentException("threshold must be in (0,1)");
        if (ambiguityMargin < 0f || ambiguityMargin > 0.5f)
            throw new IllegalArgumentException("margin must be in [0,0.5]");
        if (consecutiveMatches < 1) throw new IllegalArgumentException("consecutiveMatches >= 1");
        if (enrollmentSamples < 3) throw new IllegalArgumentException("enrollmentSamples >= 3");
        this.similarityThreshold = similarityThreshold;
        this.ambiguityMargin = ambiguityMargin;
        this.consecutiveMatches = consecutiveMatches;
        this.requireBlink = requireBlink;
        this.enrollmentSamples = enrollmentSamples;
    }

    public static RecognitionPolicy defaults() {
        return new RecognitionPolicy(DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_AMBIGUITY_MARGIN,
                DEFAULT_CONSECUTIVE_MATCHES, true, DEFAULT_ENROLLMENT_SAMPLES);
    }

    public float similarityThreshold() { return similarityThreshold; }
    public float ambiguityMargin() { return ambiguityMargin; }
    public int consecutiveMatches() { return consecutiveMatches; }
    public boolean requireBlink() { return requireBlink; }
    public int enrollmentSamples() { return enrollmentSamples; }
}
