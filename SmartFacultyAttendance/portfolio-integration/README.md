# Adding the Smart Faculty Attendance card to your portfolio

I did not have your portfolio source, so **nothing has been integrated into your site**. This folder contains reusable pieces.

| File | Use |
|---|---|
| `SmartAttendanceProjectCard.html` | Standalone page for preview. The `<article class="sfa-card">...</article>` block plus its `<style>` is what you paste. |
| `SmartAttendanceProjectCard.jsx` | React component with the same markup and scoped styles (class prefix `sfa-`). No dependencies beyond React. |

## Static HTML site

1. Open the HTML file in a browser to preview.
2. Copy the `<style>` content **except** the first two rules marked "Standalone preview chrome", and copy the `<article>` block.
3. Paste both into your page where the project should appear.

## React site

```jsx
import SmartAttendanceProjectCard from "./SmartAttendanceProjectCard";

<SmartAttendanceProjectCard
  githubUrl="https://github.com/<your-username>/Smart-Faculty-Attendance"   // only when it really exists
  screenshotSrc="/images/smart-attendance-verify.png"
  screenshotAlt="Face verification screen with an oval alignment guide and a status card"
/>
```

Without `githubUrl` the button shows as disabled ("add githubUrl"). Without `demoUrl` the demo button is not rendered.

## Replace the placeholders

* **GitHub link**: set the `href` of the element with `data-placeholder="github-url"` (HTML) or pass `githubUrl` (React).
  Do this only after the repository is published.
* **Live demo**: optional and hidden by default. Use it only if a real demo exists (a hosted screen recording is a fair
  option; the app itself cannot be run in a browser).
* **Screenshot**: take a portrait screenshot from a real device (about 1080 x 2280). In the HTML, replace the
  `.sfa-phone` block with the `<img>` shown in the comment; in React pass `screenshotSrc`. Keep the alt text accurate.
* **Status line**: edit `sfa-status` (or the `status` prop) as the project progresses. The shipped sentence says the project is
  in development and that the core logic is unit-tested, which is true today. Do not claim a verified build, accuracy figures,
  deployments or downloads you do not have.

## Theming and accessibility

* Colours are CSS variables on `.sfa-card`; override `--sfa-brand`, `--sfa-signal`, `--sfa-stage` to match your site.
* It follows the visitor's light/dark preference. Force it with `data-theme="light"` or `"dark"` on the article (React: `theme` prop).
* The scanning-line animation runs only when the visitor has not requested reduced motion.
* Links have visible focus styles and 44 px touch targets; the technology list and screenshot have text alternatives.
* The card inherits your site's font (`font-family: inherit`).

Not yet checked: rendering in a current desktop/mobile browser (the authoring environment only had an old WebKit build).
Please preview at phone width and at about 900 px before publishing.
