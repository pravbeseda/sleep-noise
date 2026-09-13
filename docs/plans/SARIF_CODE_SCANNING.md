# Lint and detekt findings in the pull request diff

## Goal

Upload the SARIF reports of the `Lint` and `Detekt` jobs to GitHub code scanning, so a finding that
turns either job red shows up on the line it is about instead of in an artifact someone has to
download. Item A3 of the code-quality suggestions; no production code changes, no new job, no new
required check.

## Facts this plan rests on

Measured on this repository on 13 September 2026, not assumed:

- Detekt already writes `build/reports/detekt/detekt.sarif` on every run: 1.23.8 enables SARIF by
  default. With the baseline in place it holds **0 results** — baselined findings are filtered
  before any report is written, so a SARIF upload shows new findings only.
- Lint writes no SARIF today. Its text report ends with `0 errors, 0 warnings, 16 hints (and 22
  errors filtered by baseline)`, and its XML carries 18 issues: the baselined ones are gone, the
  informational version-currency checks are not.
- Code scanning is `not-configured`, no analysis has ever been uploaded, and the repository has no
  rulesets, so nothing uploaded here can block a merge.
- `github/codeql-action` v4 is current; v3 is deprecated in December 2026.
- A pull request from a fork gets a read-only `GITHUB_TOKEN`, and `security-events: write` cannot be
  granted to it, so an upload from such a run fails.

## Decisions

- **Where** → a step in the existing `Detekt` and `Lint` jobs. A job of its own would have to become
  a required check, and branch protection matches contexts by job name.
- **Permissions** → `security-events: write` on those two jobs only; the workflow-level default
  stays `contents: read`.
- **When the step runs** → `always()`, the `decide-work` gate, and only when the pull request comes
  from this repository (`github.event.pull_request.head.repo.full_name == github.repository`, or not
  a pull request at all). `always()` because a failing run is the one whose findings matter; the fork
  condition because a failed upload would turn a required check red for a reason unrelated to the
  code.
- **`category`** → `lint` and `detekt`, one per upload. Two SARIF files for one commit without
  distinct categories fail the upload.
- **Detekt paths** → set `basePath` to the root project in the `detekt {}` block, so the SARIF
  carries repository-relative paths that code scanning can map onto the diff.
- **The HTML and XML artifacts stay.** Code scanning shows a finding on a changed line; the artifact
  is still the only place a finding elsewhere in a file can be read in full.
- **CodeQL analysis is out of scope.** It is a different tool with its own build and its own cost,
  and belongs in a plan of its own.

## Open questions

### Q1: Upload on pushes to `main` too?

`decide-work` answers `false` on a push to `main`, so both jobs skip every step there and `main`
would carry no analysis for code scanning to compare a pull request against.

**Decision:** pull requests only. The point is the line a red required check is about, which needs no
analysis of `main`; the baseline already keeps old debt out of the SARIF, and a run on every merge
would break the `decide-work` rule for a report that is almost always empty. If step 3 shows the
missing base scan as noise, a weekly scheduled run on `main` is the fallback, not a push trigger.

### Q2: What to do with the 17 `note` results in the lint SARIF?

Step 1 measured them: 16 version-currency hints, whose messages embed version numbers, and one
`LintBaseline` summary. None of them fails the build, and every one would become a standing code
scanning alert on `main`'s files; a hint whose message moves with a release reads as a new alert.

**Decision:** drop `note` results from the lint SARIF with `jq` before the upload, so code scanning
shows what fails the `Lint` job and nothing else. The hints stay in the HTML and XML artifact, where
they are today.

## Steps

- [x] 1. Make lint write SARIF and detekt write relative paths — files: `app/build.gradle.kts`,
      `build.gradle.kts` — lenses: none — done when a local `./gradlew lint detekt` produces
      `app/build/reports/lint-results-debug.sarif`, and every `artifactLocation.uri` in both SARIF
      files is relative to the repository root. Record how many of the 16 informational hints land
      in the lint SARIF, and at which level. Measured: every uri is repository-relative under
      `%SRCROOT%` in both files. Without `basePath`, a probe finding in detekt came out as an
      absolute `file:///Users/...` uri with no base id. The lint SARIF holds **17 results, all at
      level `note`**: the 16 version-currency hints and one `LintBaseline` note saying 22 findings
      were filtered.
- [x] 2. Upload both reports from CI — files: `.github/workflows/ci.yml` — lenses: none — done when
      the two jobs carry the permission and the upload step under the conditions above, and
      `spotlessCheck detekt lint` stays green locally. Done: the whole Definition of done line
      exits 0. The `note` filter was run against a fixture and kept exactly the explicit error, the
      warning by rule default and the warning by SARIF default. Against the real lint report it
      leaves 0 results. Dependabot pull requests are not excluded: their token is read-only by
      default, but the job-level `permissions` key can raise it, which a fork's cannot.
- [x] 3. Prove it on the pull request — files: none — done when the PR run uploads both categories
      and the Security tab lists the two analyses. Then, on a throwaway commit pushed to the same
      branch and reverted before review, one new detekt finding appears as an annotation on its
      line. If the informational lint hints appear as alerts, they are dismissed or filtered here
      rather than left as noise. Done on PR #65: both analyses uploaded with 0 results, and their
      checks — named after the tool, `Android Lint` and `detekt` — reported "No new alerts in code
      changed by this pull request", with no complaint about a missing analysis of `main`. The
      probe, an empty function, produced alert #1 (`EmptyFunctionBlock`,
      `SarifProbe.kt:3`) as an annotation, while the required `Detekt` check failed as it should.
- [x] 4. Update the documentation — files: `CLAUDE.md` (CI section), `README.md` if it describes the
      reports — lenses: none — done when both say where lint and detekt findings are read and why
      fork pull requests upload nothing.

## Not covered

- A fork pull request still gets its findings from the artifact only.
- The first Dependabot pull request is the first run to exercise its raised token; if that upload is
  refused, it joins the fork condition then.

The stale lint counts beside the lines this plan changed were corrected on the way: `29` in
`app/build.gradle.kts` and `25` / `40` / `11` in `ci.yml` are now the measured `22`, `38` and `16`.
