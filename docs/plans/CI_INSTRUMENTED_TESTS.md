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
  account credential. The emulator runner is handed none, so it follows the rule every other
  action in the workflow already follows: pinned to its tag.
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
- [x] 2. Update the prose that says CI has no device — files: `CLAUDE.md` (job count, the
      required-check list, the `NoiseEngine` architecture note), `README.md` (the instrumented
      tests paragraph), `docs/plans/REFACTORING_PLAN.md` (tick the follow-up checkbox) —
      lenses: none — done when: no file outside this plan still claims CI runs no instrumented
      test, and the checkbox is `[x]` naming issue #31. The pull request does not exist while the
      step runs, so its number is written into that checkbox by a commit of its own at the final
      gate — step 3.

- [ ] 3. Name the pull request in the ticked follow-up checkbox — files:
      `docs/plans/REFACTORING_PLAN.md` — lenses: none — done when: the entry reads "Landed in
      PR #N" in the same form as the Kover entry above it.

## Verification outside the loop
The suite is run on a local API 36 emulator before the job is written, so a red instrumented
test is found here rather than inside a CI run that also proves nothing about the YAML. The
job itself can only be proven green on CI, which is the final gate: the PR edits
`.github/workflows/`, and `decide-work` deliberately does not ignore that path, so this very
PR executes the job it adds.

## Rulings
- Final gate, quality reviewer, `.github/actions/decide-work/decide.sh:25` — "five green checks"
  is the twin of the sentence `CLAUDE.md` already had corrected to seven, and this branch is what
  made it false. **Fixed** although the file was outside the plan's steps: leaving it would ship a
  statement this diff itself disproves, and `CLAUDE.md` names exactly this failure mode.
- Final gate, quality reviewer, `docs/plans/CI_INSTRUMENTED_TESTS.md:14` and
  `.github/workflows/ci.yml:209` — the tag-pin rationale said "the other four actions" and then
  named three, while the workflow uses five. **Fixed** by dropping the enumeration: every other
  action in the workflow is tag-pinned, which is the claim that was actually being made and the
  one that does not drift when an action is added.
- Final gate, quality reviewer, `docs/plans/CI_INSTRUMENTED_TESTS.md:92` — the parked entry
  pointed at `CLAUDE.md:144`, a blank line. **Fixed** to 145.
- Step 2, spec reviewer, `CLAUDE.md:151` — "five Gradle jobs, two of them booting an emulator"
  counts matrix legs as jobs. **Fixed**: one job boots an emulator on each of its two legs, and
  the sentence now says so; the same paragraph counts jobs three clauses later.
- Step 2, spec reviewer, `docs/plans/REFACTORING_PLAN.md:247` — "lets the four Gradle jobs skip
  their steps" is now five. **Dropped**: that paragraph is the dated record of what deliverable D3
  landed, and four is what it landed against. Rewriting it would falsify the record rather than
  correct it, and the live statement of the same fact — `CLAUDE.md` — was updated. Cost if wrong:
  a reader takes a historical note for a current count, in a file whose surrounding lines are all
  historical notes.
- Step 2, spec reviewer, `.github/workflows/ci.yml:425` — the Guardrails comment says "the other
  four jobs". **Fixed** to five, together with one more count this branch made stale at
  `ci.yml:141`. Both are in a file this branch already changes.
- Step 2, quality reviewer, `docs/plans/CI_INSTRUMENTED_TESTS.md:43` — step 2 is ticked while its
  own done-criterion asks for a pull request number that cannot exist yet. **Fixed**: the
  criterion now says what was actually decided — the checkbox names issue #31 — and writing the
  number is step 3, at the final gate, where the pull request exists.
- Step 1, quality reviewer, `.github/workflows/ci.yml:144` — "Guardrails is the one job here
  carrying a job-level `if:`" is false; `alpha` carries one too. **Fixed**: the sentence now says
  "the one required check", and names `alpha` as the other, deliberately not required. A comment
  that a reader can disprove from the same file is worse than no comment.
- Step 1, spec reviewer, `docs/plans/CI_INSTRUMENTED_TESTS.md:19` — the commit that implements
  the step also adds a `## Decisions` bullet the step did not name. **Dropped**: the plan file is
  the run's memory and every decision taken on the user's behalf has to be written into it in the
  commit that acts on it, or an interrupted run resumes from a file that never recorded it. Cost
  if wrong: the step's diff is one bullet wider than its own text.

## Parked
- Nothing tracks the manual flip that makes `Instrumented tests (API 26)` and `(API 36)` required
  status checks. Issue #31 is closed by this pull request, and the fact then has to be edited in
  five places at once: `CLAUDE.md:139`, the context-name list at `CLAUDE.md:145`, `README.md:126`,
  `docs/plans/REFACTORING_PLAN.md:73` and the comment at `.github/workflows/ci.yml:140`. Raised by
  the step 2 quality reviewer.
