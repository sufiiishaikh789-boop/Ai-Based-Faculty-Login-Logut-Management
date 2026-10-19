# Security and privacy notes

Face data and attendance records are sensitive personal data. This document states what the app does,
and, just as important, what it cannot do.

## What the app does

| Concern | Measure |
|---|---|
| Face images | Analysed in memory; **never written to disk, never logged, never sent anywhere**. |
| Face templates | Numeric embeddings, stored only as **AES-256-GCM ciphertext** with a key in the **Android Keystore**; the UID is bound as associated data so a ciphertext copied to another row will not decrypt. Never logged. |
| Network | **No INTERNET permission** (the manifest removes it even if a library adds it). No analytics, no cloud. |
| Backups | `allowBackup=false` plus data-extraction rules excluding everything: records, templates and credentials do not leave the device through backup or device transfer. |
| Admin access | Separate screen; first-run password (no default); PBKDF2-HMAC-SHA256 (210,000 iterations, random salt); constant-time compare; persisted lock-out; 5-minute idle session; password buffers zeroed. |
| Permissions | Camera, precise/approximate location (needed to read the Wi-Fi name and for GPS), Wi-Fi state. No contacts, SMS, storage, microphone. |
| Logs | Only generic exception class names. No coordinates, SSIDs, BSSIDs, UIDs, templates or timestamps of individuals. |
| Evidence stored | Method plus distance/accuracy only, not raw coordinates. |
| CSV | RFC 4180 escaping; cells starting with `= + - @` are neutralised against spreadsheet formula injection. |
| Faculty control | Remove a template, deactivate a profile, or erase all templates and the key. Consent notice shown before enrolment. |
| Integrity | One open session per person is enforced by a database unique index and by transactions. Admin corrections are audited. |

## What the app cannot guarantee (read this)

* **Location and Wi-Fi spoofing.** Everything is checked on the user's own device. A rooted phone, a
  hooking framework, a custom ROM, a GPS spoofer that hides itself, or a hotspot imitating the campus SSID
  can defeat device-side checks. Mock-location detection is a best-effort signal. A BSSID allowlist raises
  the bar but access-point MACs can be cloned. GPS alone is never proof of physical presence.
* **Liveness.** The blink check only stops a held-up still photo. It does **not** stop a video replay, a
  mask or a deepfake, and it is **not** independently validated presentation-attack detection (ISO/IEC 30107-3).
  Do not describe it as anti-spoofing.
* **Recognition accuracy** depends entirely on the model you install and the threshold you calibrate.
  Accuracy can differ across demographic groups, lighting and cameras; test on your own population.
* **Local-only storage.** There is no server of record, no tamper-evident log, and the device clock is the
  time source. Whoever controls the device (or its admin password reset by clearing data) controls the data.
  A lost or wiped device loses the history unless CSV exports are kept.
* **Keystore strength** varies by device (hardware-backed on most modern phones, weaker on others and on
  rooted devices).
* **Single administrator account.** No multi-admin, role separation or password recovery.
* **The `AUTO_TIME` setting** is shown in diagnostics but not enforced.

## Before this can be used for official university attendance

1. A **server-side system of record** with authenticated, TLS-protected sync, server time, and
   server-side authorisation. The repository interfaces were designed for that, but none is implemented.
2. **Managed, attested devices** (for example MDM and Play Integrity checks) and a policy for lost devices.
3. A **validated model and independently assessed presentation-attack detection**, with measured
   false-accept/false-reject rates on your population.
4. **Legal basis and consent** for biometric processing. Biometrics are special or sensitive personal data
   under many regimes (for example GDPR; India's DPDP Act 2023 for processing personal data). This project
   is not legal advice; involve your data-protection officer and counsel, run a privacy impact assessment,
   and define who may access what.
5. **A non-biometric alternative** for people who cannot or will not use face recognition.
6. **Retention and deletion schedule.** A reasonable starting point: delete a person's face template when
   they leave or withdraw consent; keep attendance records only as long as your HR/legal rules require;
   store CSV exports in access-controlled locations and delete copies you no longer need.
7. Multi-admin accounts with password recovery, and a tamper-evident audit log.
8. A security review and penetration test of the final build.

## Optional cloud backend (not implemented)

If you add one: authenticated access (never anonymous), server-side authorisation rules, TLS only, no
secrets in the app or repository, public configuration kept separate from secrets, and documented access
rules. Do not claim synchronisation works until it has been tested end to end.

## Branding note

The brand name "Lego" was requested. LEGO is a registered trademark of the LEGO Group. The app's mark is an
original abstract shape (two chevrons), deliberately not a toy-brick, and the name appears only as text. If the
name is for a real public deployment, check trademark clearance for your use.
