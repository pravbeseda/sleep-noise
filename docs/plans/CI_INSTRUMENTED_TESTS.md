# CI job for instrumented tests (issue #31)

## Goal
Run `connectedAndroidTest` on an emulator in CI, so `NoiseEngineHammerTest` and every
instrumented test written after it become a signal instead of a file nobody executes.
Closes issue #31 and ticks the follow-up in `docs/plans/REFACTORING_PLAN.md`.

## Decisions
- Which action → `reactivecircus/android-emulator-runner@v2`, named by issue #31 as the
  reference implementation and already proven in SpendControl.
- Pin by tag `@v2`, not by commit SHA → the repository SHA-pins exactly one action,
  `wzieba/Firebase-Distribution-Github-Action`, and states the reason: it is handed a service
  account credential. The emulator runner is handed none, so it follows the majority rule the
  other four actions already use (`actions/checkout@v4`, `android-actions/setup-android@v3`,
  `gradle/actions/setup-gradle@v4`).
- `if:` on every step, never on the job → the same rule `CLAUDE.md` states for the other five:
  the job is meant to become a required status check, and a job skipped at job level never
  reports its context.
- No `-noaudio`, the one emulator option not taken from SpendControl → measured here before the
  job was written: on a local API 36 emulator the same 100 hammer cycles put the worst `stop()`
  at 1158 ms with the flag and at 321 ms without it, against the test's 1000 ms bound. The flag
  fails the very test this job exists to run.
- Job-level `timeout-minutes` → an emulator that never boots must fail the job, not burn the
  default 6 hours.
- Trigger scope → every pull request, like the other four Gradle jobs, with `decide-work`
  sparing a Markdown-only PR. The two alternatives — post-merge only, or behind a label — both
  need an exception to `decide.sh` or a human to remember, and neither can become a required
  check, which is what issue #31 is for.
- API levels → 26 and 36, the floor and the ceiling of the supported range, `fail-fast: false`
  so one flake does not hide the other level. That is where the plumbing genuinely differs:
  `POST_NOTIFICATIONS` at 33, a mandatory `foregroundServiceType` at 34, audio focus at 26. No
  ATD image exists below API 30, so the floor runs `target: default`. A third level at 33 was
  turned down: nothing tests notifications yet, so it would buy coverage that does not exist.

## Steps
- [x] 1. Add the `instrumented-tests` job to `.github/workflows/ci.yml`: checkout with
      `fetch-depth: 0`, `decide-work`, JDK 17 + Android SDK + Gradle, the shared
      `google-services` action, free disk space, enable KVM, `android-emulator-runner`
      running `./gradlew connectedDebugAndroidTest`, and an `always()` report upload —
      files: `.github/workflows/ci.yml` — lenses: security — done when:
      `grep -c connectedDebugAndroidTest .github/workflows/ci.yml` is 1, the file parses as
      YAML, and the job's step-level `if:` guards match the other four jobs.
- [ ] 2. Update the prose that says CI has no device — files: `CLAUDE.md` (job count, the
      required-check list, the `NoiseEngine` architecture note), `README.md` (the instrumented
      tests paragraph), `docs/plans/REFACTORING_PLAN.md` (tick the follow-up checkbox) —
      lenses: none — done when: no file outside this plan still claims CI runs no instrumented
      test, and the checkbox is `[x]` with the PR named.

## Verification outside the loop
The suite is run on a local API 36 emulator before the job is written, so a red instrumented
test is found here rather than inside a CI run that also proves nothing about the YAML. The
job itself can only be proven green on CI, which is the final gate: the PR edits
`.github/workflows/`, and `decide-work` deliberately does not ignore that path, so this very
PR executes the job it adds.

## Rulings

## Parked
