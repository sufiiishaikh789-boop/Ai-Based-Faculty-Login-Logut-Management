# Administrator configuration guide

Nothing campus-specific ships with the app. Every value below starts **empty or at a safe default**, and
until the campus configuration validates, real attendance is **blocked** (only the debug-only DEMO mode
can bypass it, and it is visibly labelled).

## 1. First run

Open the app. On first launch the administrator setup screen appears on top of the scanner.
Choose a password of 10+ characters. There is **no default password**. Only a salted PBKDF2 hash is stored.
Five wrong attempts trigger a lock-out that grows from 30 s up to 15 minutes. After 5 idle minutes the
admin session ends. If the password is lost, the only reset is clearing the app's data in Android settings,
which also deletes all attendance records and face templates, so export a CSV regularly.

## 2. Campus Wi-Fi (primary condition)

Admin > Configuration > **Campus Wi-Fi**.

* **Trusted SSIDs**: the exact (case-sensitive) names of the university networks, one per line.
  Never guess; ask the IT department.
* **Approved access points (BSSID)**: optional but stronger. Ask IT for the MAC addresses of the access
  points on campus (format `aa:bb:cc:dd:ee:ff`). When this list is non-empty, **only** those access points
  count. An SSID alone can be imitated by anyone running a hotspot with the same name.
* Android only reveals the SSID/BSSID if the app has **precise location permission** and device
  **Location is on**. Otherwise the app treats the network as unverifiable and falls back to GPS. It never
  assumes an arbitrary Wi-Fi network is the campus.

## 3. Campus GPS (fallback condition)

* **Centre point**: stand at the agreed reference point, outdoors if possible, open Configuration and tap
  **Use current GPS fix as campus centre**. Check the reported accuracy and repeat if it is worse than a few
  metres. Or type decimal-degree coordinates yourself.
* **Radius** default 10 m. **Maximum accepted accuracy** default 10 m: a reading that reports worse
  accuracy is rejected, because a 25 m-accurate fix says nothing about a 10 m radius.
* **Maximum fix age** (15 s) and **GPS wait** (15 s) prevent stale or slow readings.
* **Reject mock locations**: keep on. This is a best-effort signal only.
* Indoors, GPS is often 15 to 50 m off. A 10 m radius will therefore frequently reject honest users indoors.
  This is why Wi-Fi is primary. If indoor GPS fallback matters, consider a larger radius **and** a decision
  from your university about the trade-off.

## 4. Attendance rules

Duplicate window (minutes), re-login cooldown (seconds), and the IANA time zone (for example `Asia/Kolkata`).
See `docs/ATTENDANCE_POLICY.md`.

## 5. Recognition settings

Similarity threshold (default 0.70 is a **placeholder**), ambiguity margin, consecutive frames, blink
requirement. Calibrate the threshold with your model: `docs/MODEL_SETUP.md`.

## 6. Install the model, then enrol faculty

1. Add the recognition model (`docs/MODEL_SETUP.md`) and rebuild.
2. Admin > Settings and diagnostics: confirm "model: loaded" and mode `CONFIGURED`.
3. Admin > Faculty > **Add**, then **Enrol face**. The biometric notice is shown first. The faculty member
   looks at the camera while 5 samples are captured. The app refuses a face that already matches another
   active profile.

## 7. Acceptance test before real use

| Test | Expected |
|---|---|
| On campus Wi-Fi, enrolled person scans | login recorded, evidence `WIFI:...` |
| Same person again within the window | "already recorded", nothing written |
| Scan after the window | logout recorded |
| Off campus Wi-Fi, inside radius, good GPS | login recorded, evidence `GPS:...` |
| Outside radius / Location off / permission denied / GPS timeout | clear message, retry button, nothing written |
| A different, enrolled person tries to scan as someone else | recognised as themselves, never as the other person |
| An unenrolled person | "Face not recognised", nothing written |
| Photo of an enrolled person held to the camera | should be refused by the blink check; try also a video replay and **expect that to succeed**: see limitations |
| Mock-location app enabled | rejected (best effort) |
| Admin screens without signing in | impossible; redirected to sign-in |
| Release build, demo setting forced on | stays NOT_READY / CONFIGURED, never DEMO |

## 8. Data handling

* Export CSV from Admin > History. The file goes wherever the administrator saves it; treat it as personal data.
* Remove a person's template (Faculty > Remove face template) or deactivate the profile when they leave.
* "Erase ALL face templates" in Settings deletes every template and the encryption key.
