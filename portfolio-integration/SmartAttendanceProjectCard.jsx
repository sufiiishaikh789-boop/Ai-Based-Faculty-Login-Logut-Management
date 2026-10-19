import React from "react";

/**
 * Smart Faculty Attendance project card.
 * Self-contained: styles are injected by the component and scoped to the "sfa-" class prefix.
 *
 * Props (all optional):
 *   githubUrl      real repository URL; the button is disabled until you pass one
 *   demoUrl        real demo URL; the button is hidden until you pass one
 *   screenshotSrc  path or URL of a real portrait screenshot; a placeholder is drawn until you pass one
 *   screenshotAlt  accurate alt text for that screenshot
 *   status         one honest sentence about project status
 *   theme          "light" | "dark" | undefined (undefined follows the visitor's system setting)
 */
const STYLES = `
.sfa-card {
  --sfa-ink: #1b1b1f;
  --sfa-muted: #5a5663;
  --sfa-paper: #ffffff;
  --sfa-wash: #f3eefb;
  --sfa-line: #ddd3ee;
  --sfa-brand: #4a148c;
  --sfa-signal: #7c4dff;
  --sfa-lilac: #e1d0ff;
  --sfa-stage: #2e0b5b;
  box-sizing: border-box;
  display: grid;
  gap: 0;
  max-width: 880px;
  margin: 0 auto;
  background: var(--sfa-paper);
  color: var(--sfa-ink);
  border: 1px solid var(--sfa-line);
  border-radius: 20px;
  overflow: hidden;
  font-family: inherit;
  line-height: 1.55;
}
@media (prefers-color-scheme: dark) {
  .sfa-card:not([data-theme="light"]) {
    --sfa-ink: #ece8f2; --sfa-muted: #b8b2c4; --sfa-paper: #1f1b26;
    --sfa-wash: #2a2434; --sfa-line: #3a3544; --sfa-brand: #d0bcff; --sfa-stage: #1d0838;
  }
}
.sfa-card[data-theme="dark"] {
  --sfa-ink: #ece8f2; --sfa-muted: #b8b2c4; --sfa-paper: #1f1b26;
  --sfa-wash: #2a2434; --sfa-line: #3a3544; --sfa-brand: #d0bcff; --sfa-stage: #1d0838;
}
.sfa-card *, .sfa-card *::before, .sfa-card *::after { box-sizing: inherit; }

/* Screenshot slot: the one expressive element. Replace .sfa-phone with a real <img>. */
.sfa-shot { margin: 0; padding: 28px 20px 18px; background: linear-gradient(160deg, var(--sfa-stage), #5b2a9c); display: grid; justify-items: center; gap: 12px; }
.sfa-phone { position: relative; width: 190px; aspect-ratio: 9 / 19; border-radius: 28px; border: 3px solid rgba(255,255,255,.55); background: rgba(10,6,18,.55); overflow: hidden; }
.sfa-shot img { display: block; width: 190px; max-width: 100%; height: auto; border-radius: 28px; }
.sfa-oval { position: absolute; left: 50%; top: 40%; width: 62%; aspect-ratio: 3 / 4; transform: translate(-50%, -50%); border: 3px solid var(--sfa-lilac); border-radius: 50%; box-shadow: 0 0 0 100vmax rgba(10,6,18,.45); clip-path: inset(-100vmax); }
.sfa-scan { position: absolute; left: 19%; right: 19%; top: 24%; height: 2px; background: var(--sfa-lilac); opacity: .9; }
@media (prefers-reduced-motion: no-preference) {
  .sfa-scan { animation: sfa-sweep 2.4s ease-in-out infinite alternate; }
  @keyframes sfa-sweep { from { top: 24%; } to { top: 56%; } }
}
.sfa-shot figcaption { margin: 0; max-width: 24ch; text-align: center; font-size: .78rem; color: #e1d0ff; }

.sfa-body { padding: 26px 24px 24px; display: grid; gap: 16px; align-content: start; }
.sfa-title { margin: 0; font-size: clamp(1.4rem, 2.6vw, 1.8rem); line-height: 1.15; letter-spacing: -.01em; color: var(--sfa-brand); }
.sfa-desc { margin: 0; max-width: 60ch; color: var(--sfa-ink); }
.sfa-caps { margin: 0; padding: 0; list-style: none; display: grid; gap: 8px; max-width: 62ch; }
.sfa-caps li { padding-left: 14px; border-left: 3px solid var(--sfa-signal); color: var(--sfa-muted); font-size: .94rem; }
.sfa-caps strong { color: var(--sfa-ink); font-weight: 650; }
.sfa-stack { margin: 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; gap: 6px; }
.sfa-stack li { padding: 3px 10px; border-radius: 999px; background: var(--sfa-wash); border: 1px solid var(--sfa-line); font-size: .8rem; }
.sfa-status { margin: 0; font-size: .85rem; color: var(--sfa-muted); }
.sfa-links { margin: 4px 0 0; display: flex; flex-wrap: wrap; gap: 10px; }
.sfa-link { display: inline-flex; align-items: center; min-height: 44px; padding: 0 18px; border-radius: 12px; font-weight: 600; text-decoration: none; border: 2px solid var(--sfa-brand); color: var(--sfa-brand); }
.sfa-link--primary { background: var(--sfa-brand); color: var(--sfa-paper); }
.sfa-link:hover { filter: brightness(1.08); }
.sfa-link:focus-visible { outline: 3px solid var(--sfa-signal); outline-offset: 3px; }
.sfa-link[hidden] { display: none; }

@media (min-width: 720px) {
  .sfa-card { grid-template-columns: 250px 1fr; }
  .sfa-shot { align-content: center; }
}
`;

const CAPABILITIES = [
  ["Face matching on the device.", "Camera frames are analysed locally and compared with an enrolled template. Raw images are not stored and nothing is sent over a network."],
  ["Campus eligibility.", "Trusted Wi-Fi first; a 10 m GPS radius with an accuracy requirement as the fallback. Missing configuration fails safely."],
  ["Configurable attendance rule.", "Login, duplicate-scan window (default 15 minutes), then logout, enforced inside one database transaction."],
  ["Administration.", "Faculty enrolment, open-session corrections with an audit trail, CSV export and diagnostics behind a separate admin sign-in."],
];

const STACK = [
  "Java 17",
  "Android XML + Material 3",
  "CameraX",
  "ML Kit face detection",
  "LiteRT embeddings (model supplied separately)",
  "Room",
  "Android Keystore",
  "Gradle 9 / AGP 9.1",
];

export default function SmartAttendanceProjectCard({
  githubUrl,
  demoUrl,
  screenshotSrc,
  screenshotAlt = "Face verification screen with an oval alignment guide and a status card",
  status = "Status: in development. Core attendance and campus-policy logic is unit-tested; device behaviour and the recognition model are still to be validated.",
  theme,
}) {
  return (
    <article className="sfa-card" aria-labelledby="sfa-title" data-theme={theme}>
      <style>{STYLES}</style>

      <figure className="sfa-shot">
        {screenshotSrc ? (
          <img src={screenshotSrc} alt={screenshotAlt} width="190" />
        ) : (
          <>
            <div className="sfa-phone" role="img" aria-label="Placeholder for an app screenshot: face verification screen">
              <div className="sfa-oval" />
              <div className="sfa-scan" />
            </div>
            <figcaption>Placeholder. Pass screenshotSrc with a real screenshot.</figcaption>
          </>
        )}
      </figure>

      <div className="sfa-body">
        <h3 className="sfa-title" id="sfa-title">Smart Faculty Attendance</h3>
        <p className="sfa-desc">An Android app that records faculty login and logout automatically after a face check and a campus-location check, working fully offline.</p>

        <ul className="sfa-caps">
          {CAPABILITIES.map(([lead, rest]) => (
            <li key={lead}><strong>{lead}</strong> {rest}</li>
          ))}
        </ul>

        <ul className="sfa-stack" aria-label="Technology stack">
          {STACK.map((s) => <li key={s}>{s}</li>)}
        </ul>

        <p className="sfa-status">{status}</p>

        <p className="sfa-links">
          {githubUrl ? (
            <a className="sfa-link sfa-link--primary" href={githubUrl} rel="noopener">Source code on GitHub</a>
          ) : (
            <span className="sfa-link sfa-link--primary" aria-disabled="true">Source code: add githubUrl</span>
          )}
          {demoUrl && <a className="sfa-link" href={demoUrl} rel="noopener">Live demo</a>}
        </p>
      </div>
    </article>
  );
}
