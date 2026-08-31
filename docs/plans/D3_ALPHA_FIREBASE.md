# D3 — Alpha: signed APK to Firebase App Distribution

## Goal

Every merge into `main` puts a signed release APK on the testers' phones through Firebase App
Distribution, and a pull request that only touches prose stops paying for four Gradle jobs.
This is deliverable D3 of `docs/plans/REFACTORING_PLAN.md`, plus the path filtering SpendControl
already runs and this repository does not.

## Decisions

- Where does path filtering apply? → **Pull-request checks only**, not the alpha build, because
  that is SpendControl's model verbatim: its `decide.sh` answers `run=false` for every push to
  `main`, so a job that runs on such a push cannot use it. Cost accepted knowingly: a merge that
  changed only documentation still builds and still notifies the testers.
- Which Firebase tester group? → **`qa`**, the same alias SpendControl uses. The group has to
  exist in the Firebase console (App Distribution → Testers & Groups); an upload naming a group
  that does not exist succeeds and reaches nobody.
- Who creates the missing secrets? → **Split.** This run sets `ANDROID_KEYSTORE_B64` (from the
  local `.key/Drevo.Keystore`) and `FIREBASE_APP_ID` (from the Firebase project). The maintainer
  sets `SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD` and `FIREBASE_SERVICE_ACCOUNT_JSON`,
  which this run has no access to.
- Which jobs get the filter? → The four Gradle jobs. `Guardrails` needs no JDK and no Gradle and
  finishes in seconds, so filtering it would add a moving part to save nothing.
- Job-level `if:` or step-level? → **Step level.** All five jobs are required status checks with
  `strict: true`; a job skipped at job level never reports its context, and a required check that
  never reports blocks the merge button permanently.
- What does the alpha job depend on? → `needs: [unit-tests]`, as D3 states. On a push to `main`
  that job now skips its own steps and reports green in seconds, so the gate is a formality — the
  tests that matter ran on the pull request. Same in SpendControl.

## Steps

- [ ] 1. Port the `decide-work` composite action from SpendControl and wire it into the four
      Gradle jobs of `ci.yml` with step-level `if:` — files: `.github/actions/decide-work/action.yml`,
      `.github/actions/decide-work/decide.sh`, `.github/actions/decide-work/test.sh`,
      `.github/workflows/ci.yml` — lenses: none — done when: `bash .github/actions/decide-work/test.sh`
      prints `all tests passed` locally, with a case per branch of the decision.
- [ ] 2. Add a `signingConfig` to `app/build.gradle.kts` reading the `SN_*` project properties, with
      a non-null default `storeFile` so configuration still succeeds without them — files:
      `app/build.gradle.kts` — lenses: security — done when: `./gradlew assembleRelease` signs an APK
      against a throwaway keystore passed through `-PSN_*`, and `./gradlew testDebugUnitTest` still
      configures with no `SN_*` property set at all.
- [ ] 3. Add the `alpha` job to `ci.yml`: push to `main` only, `needs: [unit-tests]`,
      `concurrency: alpha`, `fetch-depth: 0`, keystore decoded from the secret, the real
      `google-services.json` asserted rather than the stub, `assembleRelease`, upload to Firebase
      App Distribution group `qa` — files: `.github/workflows/ci.yml` — lenses: security —
      done when: the workflow parses as YAML and the job's gating conditions read as intended;
      the first real run happens on the merge, which is outside this branch.
- [ ] 4. Bring the documentation back in step: the CI section of `CLAUDE.md`, `README.md` where it
      describes the pipeline, and the D3 checklist in `docs/plans/REFACTORING_PLAN.md` —
      files: `CLAUDE.md`, `README.md`, `docs/plans/REFACTORING_PLAN.md` — lenses: none —
      done when: every behaviour this branch adds is described in exactly one of them and none
      of the three contradicts the workflow.

## Out-of-tree actions

Not code, so not steps; done at the end of the run and reported.

- `gh secret set ANDROID_KEYSTORE_B64` from `base64 -i .key/Drevo.Keystore`.
- `gh secret set FIREBASE_APP_ID` with the Android app id of the Firebase project.
- Handed to the maintainer: `SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD`,
  `FIREBASE_SERVICE_ACCOUNT_JSON`, and the `qa` group in the Firebase console.

## Rulings

## Parked
