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
  its six noises since 1.0.4. It is written into `app/version.properties` in **stage 4**, not here:
  `CLAUDE.md` reserves that file, `versionCode` and the versioning block for the release pull request,
  and stage 4 is that pull request.
- **What guards the very first release?** → Nothing, on two of the four counts, and the run says so
  in a `::warning::`. No `v*+*` tag exists, so the version code has nothing to exceed and the notes
  have nothing to be stale against — including the `versionName`, which still reads 1.0.4, the
  version already on Play. Stage 4 is where it is bumped and the notes are written, which is the same
  human step a seed tag would have been protecting; a seed tag would also have to be pushed onto a
  guessed commit, since the history records only `Release 1.0.3 (5)`. Every release after the first
  is compared against the tag the first one creates.

## Stages

Four pull requests. Each is independently mergeable and leaves the repository in a working state.

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

- An instrumented test that drives the app through the screens worth showing and captures each one,
  under a locale the run supplies.
- `.github/workflows/screenshots.yml`, `workflow_dispatch`, an emulator over the six locales,
  writing `app/src/main/play/listings/<locale>/graphics/phone-screenshots/`.
- The run commits the images, per the decision above. It does not gate stage 4: with no local
  graphics the publish leaves whatever Play already holds, so the store texts can go out before a
  single screenshot has been taken.

Files: `app/src/androidTest/java/ru/pravbeseda/sleepnoise/store/ScreenshotTest.kt`,
`.github/workflows/screenshots.yml`, `app/build.gradle.kts`, `CLAUDE.md`.

Lenses: none.

Done when: one dispatch produces a readable screenshot per locale per screen, with the locale's own
strings on it.

### Stage 4 — Publishing the store page

- `.github/workflows/publish-listing.yml`, `workflow_dispatch` with a `dry_run` default of true:
  `publishListing --commit --rerun`, sending texts and graphics together.
- Documented as the step that runs **after** the rollout reaches production, or beside it — never
  before, so the page never advertises a build nobody can install.
- Bump `versionName` to **2.0.0**, write the release notes for the first release through the new pipeline, and
  record the whole path in `CLAUDE.md` and `README.md`.

Files: `.github/workflows/publish-listing.yml`, `app/version.properties`,
`app/src/main/play/release-notes/**`, `CLAUDE.md`, `README.md`.

Lenses: security (a credential that can rewrite the public store page).

Done when: a dry run validates the edit against Play and commits nothing.

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
- `resolve_release_tag.sh` gets its own test and promote/rollout run it before resolving, on the same
  rule this repository already applies to `decide-work` and `no-deleted-tests`: a CI script that
  answers wrongly is the failure that reports green.
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
- *Fixed.* `release.yml` carried its own copy of the tag-ordering pipeline that
  `resolve_release_tag.sh` owns and tests, while `CLAUDE.md` called the script "the one copy". It now
  calls the script; with no argument that script has exactly one failure, so `|| true` maps it to the
  empty value the guards read as "first release".
- *Fixed.* `track.set("internal")` restated the plugin's own default and `release.yml` passes
  `--track` on every upload. Six lines gone.
- *Fixed, not raised as a finding.* The workflow-level and job-level concurrency groups in
  `promote.yml` and `rollout.yml` were the same string whenever the tag was typed out rather than
  left empty, so the job would have waited on the group its own run holds. SpendControl never meets
  this because its job key carries a flavor suffix; there are no flavors here. The two are now
  different namespaces, `-dispatch-` and `-release-`.
- *Fixed, beyond the step's files.* The "the stub is only for forks" rule was a comment in
  `.github/actions/google-services/action.yml` that every caller had to remember, plus a copied
  14-line assertion in `ci.yml` and a second one this step added to `release.yml`. It is now a
  `require-real` input on the action itself, and both callers pass it. The second copy was this
  step's own doing, which is why it was fixed here rather than parked.
- *Dropped.* The spec reviewer noted that `rollout.yml`'s `mark-latest` job is not named by the
  step's text. It is the other half of the `--prerelease` that `release.yml` sets: without it the
  flag is set by automation and cleared by hand, and nothing else would ever clear it.

## Parked

- **`.kotlin/` is not in `.gitignore`,** so a Kotlin daemon session file can ride into a commit made
  while a build is running. It happened in this run: `.kotlin/sessions/kotlin-compiler-*.salive` was
  committed and the commit had to be rebuilt. One line in `.gitignore`, outside every stage's scope.
