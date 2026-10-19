package com.example.facultyattendance.camera;

/**
 * *** NOT FACE RECOGNITION ***  Debug-only stand-in so the UI and attendance flow can be exercised
 * on an emulator without a real model. It summarises coarse brightness of the face crop, which
 * changes with lighting and pose, and can confuse different people. Anything it produces is flagged
 * DEMO everywhere (records, CSV, banner) and is refused by AttendanceService in non-demo mode.
 */
final class DemoFaceEmbedder implements FaceEmbedder {
    private static final int SIZE = 32;
    private static final int GRID = 8;

    @Override public String modelId() { return "DEMO-brightness-grid-v1"; }
    @Override public int inputSize() { return SIZE; }
    @Override public float inputMean() { return 127.5f; }
    @Override public float inputStd() { return 127.5f; }
    @Override public boolean isDemo() { return true; }
    @Override public boolean isReady() { return true; }
    @Override public String readinessMessage() { return "DEMO embedder (debug build) - not identity verification."; }

    @Override
    public float[] embed(float[] rgb) {
        float[] cells = new float[GRID * GRID];
        int cell = SIZE / GRID;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = (y * SIZE + x) * 3;
                float lum = 0.299f * rgb[i] + 0.587f * rgb[i + 1] + 0.114f * rgb[i + 2];
                cells[(y / cell) * GRID + (x / cell)] += lum / (cell * cell);
            }
        }
        float mean = 0;
        for (float c : cells) mean += c / cells.length;
        for (int i = 0; i < cells.length; i++) cells[i] -= mean;
        return cells;
    }

    @Override public void close() { }
}
