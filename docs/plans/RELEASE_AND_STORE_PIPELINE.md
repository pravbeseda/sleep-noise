# Release pipeline, store listing and per-locale screenshots

## Goal

Rename the app to **Sleepy Cocktail: White Noise**, put its store texts under version control in all
six shipping locales, and replace the by-hand Play release with the three-verb pipeline SpendControl
runs (`release` → `promote` → `rollout`). Add a per-locale screenshot run so the store page can be
rebuilt from the repository rather than from a developer's desktop.

The store page is published by its own dispatch, never by the release job, because the page must not
describe a build the world cannot download yet.

## Where this project stands

- **No Play automation at all.** `docs/plans/REFACTORING_PLAN.md` D4 planned it and it was never
  built. Every release to Play today is a by-hand upload; CI stops at the Firebase App Distribution
  alpha on every push to `main`.
- **No store metadata in the repository.** Title, descriptions, release notes and screenshots exist
  only in the Play Console.
- **`versionName`** is `app/version.properties` (`1.0.4`); **`versionCode`** is the commit count and
  `verifyReleaseVersioning` refuses a release built from a shallow clone.
- **Six locales:** `en` (default), `ar`, `de`, `es`, `ru`, `uk` — Play codes `en-US`, `ar`, `de-DE`,
  `es-ES`, `ru-RU`, `uk`.
- **AAB, not APK.** The listing postdates August 2021, so Play requires a bundle. Play App Signing is
  already in use and `Drevo.Keystore` is the upload key.
- **`applicationId` never changes.** `ru.pravbeseda.sleepnoise` is the app's identity on Play; a
  rename is a title, not a package.

## Constraints that shape the design

- **"No ads" cannot go in the title.** Google's metadata policy names `No Ads` alongside `Free` as
  promotional text banned from the title and the developer name. It belongs in the full description,
  which is where competitors put it and where SpendControl already carries the same sentence.
- **Title fits in 30 characters.** `Sleepy Cocktail: White Noise` is 28.
- **Short description fits in 80.** `Mix six live-generated noises into your perfect sleep cocktail`
  is 61.
- **A publish with no local graphics leaves the published images alone.** Checked against Gradle
  Play Publisher 4.0.0 rather than assumed: `PublishListings` builds its media list from the graphics
  files it actually finds and calls the uploader only for those, so an absent directory issues no
  image operation at all. This plan's first draft had it the other way round — that a text-only
  publish would wipe the screenshots — and it is worth recording as wrong, because that false premise
  would have made the screenshot stage a prerequisite of the publish stage rather than an
  independent piece of work.

## Open questions

1. **How far does the rename go?** The Play title changes for certain. Does the launcher label
   (`app_name`) change with it, and is the name kept in English in every locale or half-translated?
2. **How is the artifact uploaded?** Gradle Play Publisher, as SpendControl uses, or the
   `r0adkll/upload-google-play` action the old D4 sketch named.
3. **Where do the screenshots live?** Committed to the repository, or produced as a CI artifact and
   uploaded from a developer machine.
4. **What already exists in the Play Console?** A service account with API access, and which tracks
   the app has.

## Decisions

- **How far does the rename go?** → The launcher label is `Sleepy Cocktail` in every locale; the Play
  title keeps the brand in English and translates the descriptive half — `Sleepy Cocktail: Белый шум`,
  `Sleepy Cocktail: Rauschen`, `Sleepy Cocktail: Ruido blanco`, `Sleepy Cocktail: ضوضاء بيضاء`,
  `Sleepy Cocktail: Білий шум`. Because the title is the strongest search field and people search in
  their own language, while a launcher label has room for one word. German is shortened: the full
  `Weißes Rauschen` puts the title at 32 characters, over Play's limit of 30.
- **How is the artifact uploaded?** → Gradle Play Publisher, applied behind `-PplayPublish`. Because
  all four verbs this plan needs — publish, promote, roll out, publish the listing — are its tasks,
  and SpendControl has already paid for the traps in them (`--commit`, `--rerun`, the edit cache).
- **Where do the screenshots live?** → Committed to the repository, under each locale's listing.
  Because a store page rebuilt from a checkout is the point of the work, and CI cannot publish a page
  whose graphics live on somebody's desktop.
- **`app_name` is read in four places, not one** — the launcher label, the action-bar title, the
  subject of the email to the developer and the title of the ongoing playback notification. → One
  brand in all four. Because the name a person searches for in the store and sees on the icon should
  be the name they see in the notification shade, and a second translated title would give one app
  two names in reviews. The strings around it stay translated, so the notification reads
  `Sleepy Cocktail` over `Воспроизведение`.
- **What already exists in the Play Console?** → To be confirmed, but **open testing will exist**
  either way. So the track model is SpendControl's: `release.yml` publishes to `beta` by default with
  `internal` on the dropdown, `promote.yml` derives `beta → production` and `internal → beta`, and
  `rollout.yml` drives the production percentage. Creating the service account and the open-testing
  track are prerequisites of stage 2, listed below.

- **Which Gradle Play Publisher?** → 3.13.0, the newest of the 3.x line. Not 4.x: 4.0.0 is built
  against Android Gradle Plugin 9 and this project is on AGP 8.12.2 with Gradle 8.13, and the
  plugin's own release notes say to stay on 3.x rather than upgrade one to reach the other.
- **Deployment environments?** → None. SpendControl has six, `<flavor>-<track>`, because two apps and
  three tracks make "where is each version" a question the Releases feed cannot answer. One app with
  three tracks does not, and three environments to create by hand would be a prerequisite bought for
  nothing.
- **Where does the release guard read its green checks from?** → From the merged pull request's head,
  whenever the release commit is a merge whose tree equals its second parent's. Because the release
  commit's own checks are vacuous: a push to `main` answers `decide-work` with false, so every Gradle
  job reports success in seconds having executed nothing, and `Guardrails` — a pull-request-only job
  — reports skipped. Measured on c4e9072, the merge that landed #58: `Instrumented tests (API 36)`
  succeeded in nine seconds without booting an emulator. Strict branch protection is what makes the
  parent answer for the commit, the two trees being identical. **The contract this creates:** a
  release from `main` travels on a merge commit; a squash or rebase merge has no second parent and is
  refused on `Guardrails`. This repository merges with merge commits and GitHub preselects that
  method.
- **What version is the first release through this pipeline?** → **2.0.0**, up from the 1.0.4 that is
  on Play. A major bump because the app is renamed, its store page is rewritten and it gained three of
  its six noises since 1.0.4. It is written into `app/version.properties` in **stage 5**, not here:
  `CLAUDE.md` reserves that file, `versionCode` and the versioning block for the release pull request,
  and stage 5 is that pull request.
- **What guards the very first release?** → Nothing, on two of the four counts, and the run says so
  in a `::warning::`. No `v*+*` tag exists, so the version code has nothing to exceed and the notes
  have nothing to be stale against — including the `versionName`, which still reads 1.0.4, the
  version already on Play. Stage 5 is where it is bumped and the notes are settled, which is the same
  human step a seed tag would have been protecting; a seed tag would also have to be pushed onto a
  guessed commit, since the history records only `Release 1.0.3 (5)`. Every release after the first
  is compared against the tag the first one creates.

- **One stage or two for the page and the release?** → **Two.** The page's workflow publishes nothing
  by itself and can land whenever; the version bump is release-PR territory by `CLAUDE.md`'s own
  rule, and merging 2.0.0 before a 2.0.0 exists would have every alpha build the `qa` group receives
  call itself 2.0.0 in the meantime. Decided with the user after stage 3 merged, which is why the
  stage list below has five entries where the plan opened with four.

## Stages

Five pull requests. Each is independently mergeable and leaves the repository in a working state.
Stage 4 was split in two once stage 3 had landed: the decision is the last one above.

### Stage 1 — The name and the store texts, under version control

- [x] Merged — PR #58.

- Set `app_name` to `Sleepy Cocktail` in the default bucket and delete the five translated copies,
  per the decision above.
- Create `app/src/main/play/` in Gradle Play Publisher's layout: `listings/<locale>/title.txt`,
  `short-description.txt`, `full-description.txt`; `release-notes/<locale>/default.txt`;
  `contact-email.txt`; `default-language.txt`. No plugin yet — the tree is data at this point.
- Write the full description in all six locales, carrying the no-ads sentence.
- A JVM test (`PlayMetadataTest`) holding the rules a Play rejection would otherwise teach: every
  app locale has a listing, every listing locale has a release note, title ≤ 30, short description
  ≤ 80, full description ≤ 4000, release note ≤ 500, and no listing directory exists for a locale
  the app does not ship.
- Update `README.md` and `CLAUDE.md`.

Files: `app/src/main/res/values*/strings.xml`, `app/src/main/play/**`, `app/build.gradle.kts`,
`app/src/test/java/ru/pravbeseda/sleepnoise/store/PlayMetadataTest.kt`, `README.md`, `CLAUDE.md`.

Lenses: compatibility (the app's launcher label and its store identity change).

Done when: `./gradlew testDebugUnitTest` is green with `PlayMetadataTest` failing first against a
deliberately over-long title, and the app installs showing the new label.

### Stage 2 — Release, promote and rollout

- [x] Merged — PR #61.

- Add Gradle Play Publisher to `gradle/libs.versions.toml` and apply it to `:app` behind a
  `-PplayPublish` property, so an ordinary build never needs the credentials.
- `.github/workflows/release.yml` — the guards, then a signed AAB, then the upload, the tag and the
  GitHub Release. Guards: the tag must not exist; the version code must exceed the newest `v*+*`
  tag's; this commit's seven required checks must be green; the release notes must not repeat the
  previous tag's.
- `.github/workflows/promote.yml` — moves a version code between tracks; the source is derived from
  the destination.
- `.github/workflows/rollout.yml` — `set-fraction`, `complete`, `halt` on production.
- `.github/scripts/check_release_readiness.py` and `resolve_release_tag.sh`, adapted: one module, no
  flavors.
- Tag scheme `v<versionName>+<versionCode>`, e.g. `v1.1.0+58`.
- **The listing is untouched by all three.** `publishBundle` and `promoteArtifact` carry the
  artifact and the release notes only.

Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.github/workflows/{release,promote,rollout}.yml`,
`.github/scripts/*`, `CLAUDE.md`, `README.md`.

Lenses: security (a new plugin on the release path and a new credential), compatibility (the tag
scheme and the version code contract).

Done when: `release.yml` dispatched with `dry_run: true` passes every guard and publishes nothing.

### Stage 3 — Per-locale screenshots

- [x] Merged — PR #62.

- An instrumented test that drives the app through the three states worth showing — the mixer at
  rest, a session playing with the countdown, the mixer in the dark theme — in every locale the app
  ships, and copies the app's own window for each.
- `.github/workflows/screenshots.yml`, `workflow_dispatch`, one emulator covering all six locales in
  a single run, writing `app/src/main/play/listings/<locale>/graphics/phone-screenshots/`.
- The run commits the images, per the decision above, and pushes them as a branch: `main` is
  protected, and a pull request opened by `GITHUB_TOKEN` would report none of the seven required
  checks. It does not gate stage 4: with no local graphics the publish leaves whatever Play already
  holds, so the store texts can go out before a single screenshot has been taken.

Files: `app/src/androidTest/java/ru/pravbeseda/sleepnoise/store/StoreScreenshotTest.kt` and
`StoreScreenshot.kt`, `.github/workflows/screenshots.yml`, `app/build.gradle.kts`, `CLAUDE.md`,
`README.md`.

Lenses: none.

Done when: one dispatch produces a readable screenshot per locale per screen, with the locale's own
strings on it.

### Stage 4 — Publishing the store page

- [x] Merged — PR #63.

- `.github/workflows/publish-listing.yml`, `workflow_dispatch` with a `dry_run` default of true:
  `publishReleaseListing`, with `--commit` only on a run that asked for it, sending texts and
  graphics together. No push and no merge reaches it.
- Documented as the step that runs **after** the rollout reaches production, or beside it — never
  before, so the page never advertises a build nobody can install. Not enforced: Play would have to
  be asked what is live, which is an API call and a guard for a mistake a dispatch has to be typed
  to make.
- Record the whole path in `CLAUDE.md` and `README.md`.

Files: `.github/workflows/publish-listing.yml`, `app/build.gradle.kts` (one stale comment),
`CLAUDE.md`, `README.md`.

Lenses: security (a credential that can rewrite the public store page).

Done when: a dry run validates the edit against Play and commits nothing.

### Stage 5 — The first release through the pipeline

Its own pull request, and dispatched rather than merged into: this is the release, and it is written
when a release is actually going out.

- Bump `versionName` to **2.0.0** in `app/version.properties`.
- Settle the release notes in all six locales. Stage 1 wrote a first draft of them with the rest of
  the store tree; this stage is where they are read against the release actually going out and
  rewritten where they do not fit it. The first release has no previous tag, so guard 4 has nothing
  to compare them against, and this is the human step it stands in for.
- Check `NOISE_LAB_ENABLED` is `false`, as the noise lab section of `CLAUDE.md` requires of every
  release pull request.
- Then the dispatches, in order: `release.yml` from `main` → `promote.yml` to production at a
  fraction → `rollout.yml` to raise it and complete → `publish-listing.yml` for the page.

Files: `app/version.properties`, `app/src/main/play/release-notes/**`.

Lenses: compatibility (the version Play holds and the version the repository claims).

Done when: 2.0.0 is on production and the store page describes it.

## Operational prerequisites

Outside the repository, and blocking stage 2:

| What | Where | Note |
|---|---|---|
| Invite the service account in the Play Console | Play Console → Users and permissions | **Open.** The account behind the secret below exists; it still needs **release manager** for the tracks and **manage store presence** for the listing — two different permissions, and the second fails as a 403 on `edits:validate` rather than as a permission error |
| ~~`PLAY_SERVICE_ACCOUNT_JSON` secret~~ | GitHub repo secrets | **Done, 2026-09-09.** The JSON key of that account |
| An open-testing track | Play Console | **Open.** `release.yml` publishes there by default |

## Rulings

**Stage 1.** Three reviewers — spec, quality, compatibility.

- *Fixed.* `src/main/play` was not a Gradle input of `testDebugUnitTest`, so the Definition of done
  line reported green while the check that guards the store texts was skipped. Reproduced before
  fixing: a 32-character German title, two over Play's limit, gave `BUILD SUCCESSFUL`. The tree is
  now declared an input in `app/build.gradle.kts`; the same title now fails without `--rerun-tasks`,
  and the configuration cache still stores an entry. The reformat hazard `CLAUDE.md` warns about did
  not fire — the edit came out as 13 added lines and nothing else.
- *Fixed.* `README.md` said the app "is published on Google Play as Sleepy Cocktail: White Noise".
  It is not: this stage writes the tree and uploads nothing. Now "its next release is listed as".
- *Fixed.* `README.md` named the Gradle project `SleepNoise`; it is `Sleep Noise`, which `CLAUDE.md`
  had right in the same commit — the drift the two files are meant not to have.
- *Fixed, beyond the step's letter.* `README.md` claimed the app "needs no network access". The
  merged manifest carries `INTERNET` and `ACCESS_NETWORK_STATE` from Firebase, verified in
  `app/build`. Left alone it would have contradicted the store copy written in the same commit,
  where the claim is deliberately "playback needs no connection" and never "sends nothing".
- *Fixed.* The three listing file names were spelled twice in `PlayMetadataTest`, once for the
  completeness test and once for the limit test. One map now serves both.
- *Dropped.* Two reviewers called `everyShippedLocaleHasAListing` redundant with the completeness
  test, which does fail for a missing directory. Kept anyway: it answers a different question — a
  language added to the app with no store texts at all — and says so in one line instead of three
  "missing or empty" paths. That is the exact mistake the Localization section now points at, and six
  lines is a fair price for naming it.
- *Dropped.* The developer's address appears in six full descriptions besides `contact-email.txt`,
  with no test holding them equal. It is marketing copy, not a constant: removing it removes what a
  reader sees, and Play renders the contact block somewhere else on the page.
- *Dropped.* The locale scan fails any `values*` bucket whose `strings.xml` declares no `lang`, and
  `values-sw320dp` is the bucket a string override would land in. It has no `strings.xml` today, so
  there is no input on which the code goes wrong; the failure it would one day give names the file
  and says what to add.
- *Moot.* A reviewer noted the documented stale-green paragraph gave the wrong reason for CI being
  safe — a fresh checkout rather than build caching being off. The paragraph is gone with the fix.

**Stage 2.** Decided without the user, each recorded because a reasonable person might have chosen
otherwise:

- The App Bundle attached to the GitHub Release is renamed `SleepNoise-<name>-<code>-release.aab`,
  matching what the `applicationVariants` block already does to the APK. AGP names it
  `app-release.aab`, which says nothing about which release it is once downloaded.
- `resolve_release_tag.sh` gets its own test and all three workflows run it before trusting the
  script, on the same rule this repository already applies to `decide-work` and `no-deleted-tests`: a
  CI script that answers wrongly is the failure that reports green. (`release.yml` joined the other
  two in the pull request review; the entry below records why.)
- Two `::error::` lines in `release.yml` run to 166 and 156 characters. The 140 limit is detekt's and
  applies to Kotlin; a workflow annotation cannot be wrapped without breaking it, and `ci.yml`
  already carries lines of that length.

**Stage 2 review gate.** Four reviewers — spec, quality, security, compatibility.

- *Fixed.* `--version-code` was credited with a refusal Gradle Play Publisher does not make. Checked
  in `DefaultTrackManager.promote` at tag 3.13.0: the plugin fetches the source track and calls
  `mergeChanges(listOf(versionCode), base)` on **every** release on it, so an older tag dispatched
  after a newer one reached production rewrites the newer release backwards rather than failing. All
  three places that claimed otherwise now say what it actually does. No guard was built: asking Play
  what is on the track costs an API call, and the defect was the comment.
- *Fixed.* `CLAUDE.md` still described guard 3 as reading only a skipped context off the merged pull
  request's head, which is what it did before the decision above changed it to read all seven. One
  decision, three places, one of them already disagreeing.
- *Fixed, then fixed again.* `release.yml` carried its own copy of the tag-ordering pipeline that
  `resolve_release_tag.sh` owns and tests, while `CLAUDE.md` called the script "the one copy". It
  calls the script now — but the first form used `|| true` to map "no tag yet" to the empty value the
  guards read as "first release", and the pull request review pointed out that this maps *every*
  failure of the script the same way, switching guards 2 and 4 off silently rather than refusing a
  release. It now asks whether any `v*+*` tag exists before calling, and the call runs under `set -e`.
  `release.yml` also runs the script's test first, as the other two callers do.
- *Fixed.* `track.set("internal")` restated the plugin's own default and `release.yml` passes
  `--track` on every upload. Six lines gone.
- *Fixed twice.* The workflow-level and job-level concurrency groups in `promote.yml` and
  `rollout.yml` were the same string whenever the tag was typed out rather than left empty, so the
  job would have waited on the group its own run holds. Separating the namespaces fixed that and left
  a larger hole open, which the pull request review then found: the groups were per workflow, while
  the resource they protect is one Play edit per service account — *creating a new edit for an
  application invalidates any active edits for that application created by the same user*. A release
  overlapping a rollout voided the other run's edit. All five keys are now one static `play-edit`
  shared by the three workflows, and both job-level groups are gone with them.
- *Fixed, beyond the step's files.* The "the stub is only for forks" rule was a comment in
  `.github/actions/google-services/action.yml` that every caller had to remember, plus a copied
  14-line assertion in `ci.yml` and a second one this step added to `release.yml`. It is now a
  `require-real` input on the action itself, and both callers pass it. The second copy was this
  step's own doing, which is why it was fixed here rather than parked.
- *Dropped.* The spec reviewer noted that `rollout.yml`'s `mark-latest` job is not named by the
  step's text. It is the other half of the `--prerelease` that `release.yml` sets: without it the
  flag is set by automation and cleared by hand, and nothing else would ever clear it.

**Stage 3.** Decided without the user, each recorded because a reasonable person might have chosen
otherwise:

- **Three states rather than two or four**, and one instrumentation run rather than six. The user
  chose the three; the run is one because the app's locale is a preference the test writes, so six
  emulator boots would buy nothing.
- **The window is copied with `PixelCopy`,** not photographed with `UiAutomation.takeScreenshot`. The
  app draws edge to edge, so its own window is the whole display: the picture comes out without the
  emulator's status and navigation bars, and the demo-mode dance that cleans a system bar up is not
  needed at all.
- **JPEG at quality 92.** Measured on this screen: 1.5 MB per picture losslessly against 130 KB,
  indistinguishable side by side, 19 MB against 2 MB for a whole refresh — and every refresh stays in
  the repository's history for good. Gradle Play Publisher uploads a listing's graphics under
  `image/*`, checked in `DefaultPlayPublisher` at 3.13.0, so the extension is ours.
- **The errand is filtered by annotation, from the build script.** `-PstoreScreenshots` swaps the
  runner's `notAnnotation` for `annotation`, so one property decides both directions and
  `connectedAndroidTest` keeps meaning what it meant. An `@Ignore` would have been the other way to
  keep it out of CI, and it is the one this project forbids outright.
- **`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`** is what makes the pull work:
  the test writes into the app's external files directory and Gradle uninstalls the app when the run
  ends, taking the directory with it. The first run passed and left nothing behind.
- **The locale is set through `LocaleManager` from API 33 up.** `AppCompatDelegate.setApplicationLocales`
  reaches the framework through a context it takes from a running Activity, and before the first
  launch it stores nothing: measured on an API 36 emulator, `getApplicationLocales()` came back empty
  and all six locales were photographed in English while the run reported green. Below 33 AppCompat
  owns the locale and its call is the only way in.
- **The emulator is `pixel_2` and the test measures what it captured.** Play refuses a screenshot past
  a ratio of 2:1 and every modern phone profile is 20:9; the guard was watched failing on a 1080x2400
  display before the run was believed on a 1080x1920 one.

**Stage 4.** Decided without the user, each recorded because a reasonable person might have chosen
otherwise:

- **The dry/commit branch is a shell `if`, not a `${{ }}` ternary.** An empty string is falsy in a
  GitHub expression, so `inputs.dry_run && '' || '--commit'` — the obvious form — hands `--commit` to
  the dry run. The two `gradlew` lines written out cost a duplicated task name and cannot fail that
  way.
- **No `require-real` on the google-services step,** unlike `release.yml` and the alpha job: this one
  uploads text and images and builds nothing anybody installs, so a stub Firebase config changes
  nothing about what is published. The file is there only because the plugins are applied
  unconditionally.
- **The page's ordering after the rollout is documented, not enforced.** Enforcing it means asking
  Play what is live — an API call, a credential path and a guard, against a mistake that already
  takes a deliberate dispatch to make.
- **The checkout is full though nothing here packages an artifact.** `verifyReleaseVersioning` never
  runs on this path, so a shallow clone would work; every checkout on the Play path is full so that
  there is no exception for a reader to remember.

## Parked

- **`.kotlin/` is not in `.gitignore`,** so a Kotlin daemon session file can ride into a commit made
  while a build is running. It happened in this run: `.kotlin/sessions/kotlin-compiler-*.salive` was
  committed and the commit had to be rebuilt. One line in `.gitignore`, outside every stage's scope.
