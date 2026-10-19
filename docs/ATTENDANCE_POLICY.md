# Attendance timing policy (read this before deploying)

This project implements an **unusual, time-based** rule because it was specified that way. A university
may well want something different.

## The rule

1. A verified scan with a valid campus check and **no open session** creates a record: `LOGGED_IN`.
2. Another scan **before the duplicate window** (default **15 minutes**, measured from the login time)
   is ignored. Nothing is written; the original login timestamp is never overwritten.
3. A scan **at or after** the window records the logout time and sets `LOGGED_OUT`.
4. The next valid scan after a logout opens a new session.
5. A session nobody closes keeps a **null** logout. Reports show `OPEN_SESSION`. The app never invents
   a logout time. An administrator can close it (see below).

### Example (window = 15 min)

| Time | Scan | Result |
|---|---|---|
| 09:00:00 | yes | login recorded |
| 09:07:30 | yes | ignored (7.5 min since login) |
| 09:15:00 | yes | **logout** recorded |
| 09:15:20 | yes | ignored by the re-login cooldown (see below) |
| 09:40:00 | yes | new session: login |
| (nobody scans again) | | stays `OPEN_SESSION` until an admin corrects it |

## One deliberate addition: re-login cooldown (default 60 s, can be set to 0)

Without it, scanning twice in a row would logout and then immediately log in again from one gesture
(for example the person is still standing in front of the camera). With the cooldown, a scan within 60
seconds after a logout is ignored. Set it to **0** for the literal "next scan opens a new session".

## Consequences you should know about

* Every second scan is a logout, so someone who scans "to check" at minute 16 logs themselves out.
* A person who forgets to scan out leaves an open session; nothing closes it automatically.
* A real attendance system more commonly uses explicit in/out, shift schedules, or first/last scan of the day.
  `AttendanceService` is the single place to change the policy; its tests document the current behaviour.

## Time handling

Timestamps are stored as UTC epoch milliseconds (`Clock.systemUTC()`, injected so tests control time) and
displayed in the configured IANA time zone. `localDate` is computed in that zone at write time.
The device clock is the time source: a user who changes the phone time (or has "automatic time" off)
can distort it. Diagnostics shows the auto-time setting. A server time source is required for official use.
If the clock moves **backwards** past a login, the scan is treated as a duplicate rather than a logout.

## Concurrency

Repeated camera frames cannot repeat a write (the analyzer latches after one callback). Even if two
callers race, `AttendanceService` runs inside one Room transaction (writers are serialised), and the
database has a UNIQUE index on (faculty, open marker), so two open sessions for one person are impossible.

## Administrator correction of open sessions

Admin > Open sessions. The administrator chooses the logout time and enters a reason (5+ characters).
The app shows the exact date and time that will be saved. The logout must not be before the login or in
the future. The record is marked `corrected`, its logout evidence is `ADMIN_CORRECTION`, and an audit
entry (action, record, time, actor, reason) is written in the same transaction.
