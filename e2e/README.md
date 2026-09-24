# Frontend end-to-end checks

79 checks that drive a real headless browser through the six screens and assert
what the DOM actually renders — including the things a unit test cannot see.

```bash
./e2e/run.sh                                        # against localhost:8080
BASE=https://quiz-api-xxxx-uc.a.run.app \
  ADMIN_CODE=<deployed code> ./e2e/run.sh           # against Cloud Run
```

Start the app first (`mvn spring-boot:run`). The run registers its own throwaway
student and admin, authors a quiz and takes it, then deletes every `E2E Quiz ...`
tile on the way out — against the deployed Postgres it would otherwise leave them on
the dashboard for real users. It sweeps all of them rather than just its own, because
a run that fails partway never reaches the cleanup.

The two throwaway accounts do stay behind: the API has no delete-user endpoint.
They are named `e2e.stu<stamp>` / `e2e.adm<stamp>`, so they are easy to spot and
clear out by hand.

## What it covers

Auth (per-field validation, the server's own 401/403 messages, the 6-character
password rule), role gating (the Create Quiz entry point, redirecting a student away
from the admin screen), authoring (repeatable questions, renumbering, nested
`questions[0].options[1].text` errors routed back to their input), taking a quiz
(progress, the unanswered-question guard and where focus lands), the result screen
(score, percentage, and the three review states), attempt history, focus rings on
every keyboard stop, `prefers-reduced-motion`, no horizontal scroll at 375px, and no
console errors or failed static requests across the whole run.

Two checks exist because a screenshot caught what a DOM assertion missed:

- **`painted()` rather than `element.hidden`.** `.alert` and `.error` set
  `display: flex`, which beats the browser's own `[hidden]` rule. An alert can read
  `hidden === true` and still be on screen as an empty coloured box, so these assert
  a measured box instead of the property.
- **Review-row backgrounds.** A review row keeps its radio checked, so it also
  matches `.option:has(input:checked)` — higher specificity than `.option-correct`.
  Without the fix in `app.css`, every review row paints accent blue and the
  correct/incorrect distinction vanishes while every text marker still reads right.

## Why not Playwright

There is no `package.json` in this project and no reason to add one: the harness is
`cdp.mjs` (~70 lines over Node's built-in `WebSocket`) plus the checks. It runs
against any Chrome binary already on the machine.
