# Face-recognition model setup

## What is and is not included

**No recognition model is bundled.** None could be downloaded or licence-checked while the project was
authored (no network access), and shipping an unverified model would be irresponsible for a biometric system.
Google ML Kit face **detection**, which *is* included, finds faces and eye landmarks but **cannot tell people
apart**. Identity comes only from the model you add here.

Until `app/src/main/assets/models/face_embedding.tflite` exists and loads:

* Diagnostics shows `model: Model file missing...`, mode is `NOT_READY`, and scanning is blocked.
* In a **debug** build only, you can switch on DEMO mode. It uses a brightness-grid stand-in that is **not**
  recognition, flags every record `DEMO`, and shows a banner. The release source set does not contain that
  class at all.

## Model contract (what `TfliteFaceEmbedder` expects)

* LiteRT/TensorFlow Lite file named exactly `face_embedding.tflite` in `app/src/main/assets/models/`.
* Input tensor `1 x H x W x 3`, **float32**, `H == W` (typically 112), RGB order.
* Output tensor `1 x N` float32 embedding, `N >= 64` (typically 128 or 192 or 512).
* Pre-processing is `(pixel - 127.5) / 128.0` (`inputMean()` / `inputStd()` in `TfliteFaceEmbedder`) and a
  face crop of 1.25 x the detected box, eye-level rotated (`FaceCropper.CROP_SCALE`). **These must match how
  your model was trained.** If your model expects different constants or a tighter crop, change them. A
  mismatch quietly degrades accuracy rather than failing.
* The model id stored with each template is `face_embedding-<first 12 hex of the file's SHA-256>`.
  Replacing the model file therefore **invalidates all enrolled templates** (they are ignored, not mixed). Re-enrol.

## Licence and provenance checklist (do this before choosing a model)

Face models have **three separate licences** that people routinely conflate:

1. the **code** that defines/converts the network,
2. the **trained weights** you actually ship,
3. the **training data**, which some publishers say restricts what trained models may be used for.

Check, in writing, from the model's own repository or paper:

- [ ] Weights licence permits your use (educational/internal deployment vs commercial).
- [ ] Redistribution is permitted if you will publish the APK or commit the file to a public GitHub repository.
      If unsure, **do not commit it** (the provided `.gitignore` excludes `*.tflite` for exactly this reason).
- [ ] Training-data terms do not forbid your use. Some widely used pretrained face models are published with
      non-commercial research-only terms; verify the *current* terms yourself rather than relying on memory.
- [ ] Published accuracy was measured on a population that resembles yours.
- [ ] Input/output shapes match the contract above (or adapt `TfliteFaceEmbedder`).
- [ ] You have recorded the source URL, version and SHA-256 in your own documentation.

## Install

1. Copy the file to `app/src/main/assets/models/face_embedding.tflite`.
2. Rebuild and run. Admin > Settings and diagnostics must show `Model loaded (...)`.
3. Enrol faculty (they will be stored under the new model id).

## Calibrating the threshold (do not skip)

The default similarity threshold (0.70) and ambiguity margin (0.05) are **placeholders**.

1. With written consent, collect genuine pairs (same person, different days/lighting) and impostor pairs
   (different people) from a small pilot group. Keep this data out of Git and delete it afterwards.
2. Compute cosine similarity for both sets.
3. Choose a threshold giving an acceptable false-accept rate for impostors, and check the false-reject rate
   for genuine users. A wrongly accepted face is worse than a rejected one for attendance fraud.
4. Enter the value in Admin > Configuration.

## Replacing the recognition approach

Implement `FaceEmbedder` (see its Javadoc) and return it from `AppContainer.activeEmbedder`. Nothing else changes.
