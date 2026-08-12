# Agent Orchestrator rollout

How this project moves from a hand-driven development loop to orchestration.
SleepNoise goes first; the other projects (Angular, Dart, PHP) are connected
last, once the scheme has earned it here.

## Principle

**One stage, one piece of automation.** Never "configure everything and see".
Each stage switches on exactly one mechanism, has an objective criterion for
whether it works, and a one-line rollback. Until the criterion is met the next
stage does not start, however tempting.

The order is not arbitrary. It runs from automation whose verdict a machine can
check (CI is green or it is not) to automation whose verdict is a judgement call
(is this phase plan any good). When the first kind is wrong you see it
immediately. When the last kind is wrong you see it three PRs later.

**The criterion for moving on is the same everywhere: three consecutive tasks
went through the stage without a hand reaching in.** One good task proves
nothing.

## Current state

**Stage 1, one task of the three done.**

### What stage 1 has already proved

The four expectations in the stage 1 table are now measured facts rather than
guesses. The agent ran the full definition-of-done suite inside its worktree and
reached green CI, which it could not have done had any of them been wrong: the
`app/google-services.json` symlink resolved, the hooks fired from the shared
`.git/config`, the Spotless ratchet found `origin/main`, and `versionCode`
counted the full history. The worktree question is settled; what remains is the
three-tasks-without-intervention part of the criterion.

### Task 1 — issue #10, PR #11 (`ao/sleepnoise-5/no-print-stack-trace`)

Replaced two `e.printStackTrace()` calls in `MainActivity`. Four things worth
carrying forward:

- **The fix itself was right.** `Log.w` for the `ClassCastException` (only the
  optional icons are lost), `FirebaseCrashlytics.recordException` for the
  `NameNotFoundException` (the app failing to find its own package is
  impossible), each with a one-line justification. That was the single
  judgement call in the task and it went the right way. Detekt baseline went
  from 20 entries to 19.
- **62 changed lines in `MainActivity.kt` for a two-line fix.** Almost all of it
  is the ratchet: the file entered the diff, so Spotless reformatted the whole
  of it — import order, trailing comma, blank lines between `when` branches.
  Expect this on the first PR that touches any long-untouched file. One change
  is **not** yet explained: `onOptionsItemSelected` was converted to an
  expression body, which is not whitespace. Unverified whether ktlint did that
  or the agent did.
- **`CLAUDE.md` was edited although the issue said "these two call sites only".**
  Correct in substance — the file claimed "Two call sites remain" and "20
  entries covering 38 findings", both of which the change made false — but it
  contradicts the "do not widen the scope" rule. See the open decisions below.
- **Cost: $1.37 for the implementation, on Opus.** 1.3M tokens, of which 47 were
  fresh input and the rest cache read: a ~50k context re-read across ~26 turns.
  Nothing wasteful was loaded; the model was simply the wrong size for the task.
  Wall time 21 minutes against 2m34s of API time — the difference is
  `permissions: default` waiting for approvals, which stage 2 removes.

Processing the single review comment in that same session took the total to
2.5M tokens. Lesson: once a session is long, run `ali-process-pr-comments` in a
**fresh** agent session started in the worktree directory, not in the session
terminal. The branch is the same; the context is not.

### Open decisions

1. **Documentation versus scope.** Proposed addition to `agentRules`:
   "Documentation that the change makes false is part of the change, not a
   widening of scope. Update it in the same PR and say so in the description."
   Without it, the same collision recurs on the next task — the `Locale` fix
   will make the `DefaultLocale` paragraph in `CLAUDE.md` false too.
2. **Worker model.** Set `worker.agentConfig.model` to Sonnet rather than
   relying on the GUI default. The exact identifier has to come from the model
   dropdown in the desktop app; the ones in AO's own docs are from an older
   generation.

### Next task

`Locale` on the three `String.format` call sites in `timer/` — `TimerView.kt:43`,
`TimerController.kt:18` and `:20`. Deliberately chosen second because it is the
first task to touch **`app/lint-baseline.xml`** (4 `DefaultLocale` entries),
which means `./gradlew updateLintBaseline` and stripping the informational
entries it adds back. That is the most temperamental part of the tooling and is
better met on a three-line change.

---

## Stage 0 — what to fix first

`git worktree list` shows two `prunable` worktrees left by earlier AO runs:

```
~/.ao/data/worktrees/sleepnoise/orchestrator/sleepnoise-orchestrator  [ao/sleepnoise-orchestrator] prunable
~/.ao/data/worktrees/sleepnoise/sleepnoise-4                          [ao/sleepnoise-4/root]       prunable
```

The directories are gone, the metadata in `.git` is not. Clear it:

```bash
git worktree prune
git branch -D ao/sleepnoise-orchestrator ao/sleepnoise-4/root   # if the branches are not wanted
```

While here: the working tree carries a modified `gradlew.bat` and an untracked
`.claude/`. Decide on the first; the second goes either into `.gitignore` or
into a commit. Starting orchestration from a dirty `main` is a bad trade — a
worktree inherits `HEAD`, not the working tree, and the divergence takes a long
time to find later.

Confirm both CLIs are present and authenticated:

```bash
ao doctor
```

Without `codex` installed the second reviewer from stage 4 will not start, and
that gets discovered at spawn time rather than now.

---

## Stage 1 — observation

**Switched on:** nothing. `agent-orchestrator.yaml` sits in the repository with
every reaction at `auto: false`. AO only creates the worktree, launches Claude
Code, and displays state.

**What is actually under test:** whether the build survives a worktree. It is
the one question whose answer, if it is "no", makes every later stage
worthless. Spawn one small task and, inside its worktree, run by hand:

```bash
./gradlew spotlessCheck detekt testDebugUnitTest lint
```

Three places where this could break, and why they probably will not:

| Mechanism | Risk in a worktree | Expectation |
| --- | --- | --- |
| `app/google-services.json` | Gitignored and required; a worktree does not get it | Covered by the symlink in the config — **this is the real test of the stage** |
| `.githooks` via `core.hooksPath` | The setting lives in `.git/config`, which a worktree "does not have" | Works: a worktree shares `config` with the main repository |
| Spotless `ratchetFrom("origin/main")` | Needs the `origin/main` ref | Works: the object store is shared |
| `versionCode` from `git rev-list --count HEAD` | A shallow clone undercounts | Works: the worktree sees the full history |

The right-hand column is exactly that — expectations. This stage exists to turn
them into measured facts.

**Criterion to move on:** three agent-made PRs in a worktree taken to green CI.
Reaching into the code by hand is fine at this stage; reaching into the build
is not.

**Rollback:** delete `agent-orchestrator.yaml` and work as before.

---

## Stage 2 — CI self-repair

**Switched on:** `ci-failed: auto: true`. Red CI wakes the agent without you.

This is the first automation not because it is the most useful but because its
verdict is objective: the build is green or it is not, and the agent cannot
talk itself into having succeeded. This is also where permissions relax to
`auto-edit` — repairing CI without the right to edit files is pointless.

```yaml
agentConfig:
  permissions: auto-edit

reactions:
  ci-failed:
    auto: true
    retries: 2
    message: |
      CI is red. Read the failing check logs, reproduce locally, fix the
      smallest cause, and push.
      Do not touch the baselines: lint-baseline.xml and detekt/baseline.xml
      only ever shrink. Do not weaken a test — not by deleting it, not with
      @Ignore, not by loosening an assertion. If that is the only available
      fix, stop and ask.
```

`retries: 2` matters more than it looks: an agent that cannot fix the build in
two attempts will not fix it in five, but will have made a lot of edits trying.

**Criterion to move on:** three red builds repaired by the agent alone, none of
which came down to weakening a check. The last part is verified by reading the
diff; nothing catches it automatically.

**Rollback:** `ci-failed: auto: false`.

---

## Stage 3 — one reviewer

**Switched on:** `reviewers: [claude-code]`. Review runs automatically, but its
result is still only displayed — `changes-requested` stays at `auto: false`.

```yaml
reviewers:
  - harness: claude-code
```

**Under test:** whether automated review matches what `ali-review-pr` produces
by hand. The check is direct — run both on three PRs and compare the findings.
If the AO reviewer finds noticeably less, the cause is the reviewer prompt
rather than the model, and it has to be fixed before stage 4; otherwise the
second reviewer merely doubles a weak review.

**Criterion to move on:** across three PRs, automated review missed nothing
that `ali-review-pr` would have caught.

**Rollback:** remove the `reviewers` block.

---

## Stage 4 — a second reviewer, for a fresh pair of eyes

**Switched on:** Codex alongside Claude.

```yaml
reviewers:
  - harness: claude-code
  - harness: codex
```

This is the reason for having two vendors in the scheme at all: two models are
wrong in different ways, so the intersection of their findings is a signal and
the divergence is a prompt to look yourself. The secondary benefit is more
mundane — Codex runs on a separate OpenAI subscription and does not eat into
the weekly Claude limit.

**Under test:** whether double review turns into duplicate noise. If both
reviewers say the same thing in different words, working through the comments
costs more than it did by hand and the point is lost.

**Criterion to move on:** three PRs where total noise did not grow while the
list of findings got fuller.

**Rollback:** drop `codex` from the list.

---

## Stage 5 — the review loop, with escalation

The substantial stage. `changes-requested` goes to `auto: true` here, and this
is exactly where a working stop mechanism is needed — otherwise the agent
starts making the decisions currently made by hand through
`ali-process-pr-comments`.

### How escalation is built

AO has no notion of "the agent is unsure". Three usable parts exist: the
`agent-needs-input` reaction (an urgent notification when a session waits for
input), `retries`, and `escalateAfter`. So the signal has to be produced by the
agent, and AO carries it. The mechanics: a rule forces the agent to stop and
wait → AO sees `session.needs_input` → urgent notification → the answer goes
back via `ao send`.

The stop should be formatted the way `ali-one-by-one` already formats one:
context, two or three options with their trade-offs, a recommendation. The
answer then feels familiar, and the decision taken is written back into the
phase plan file.

### Stop conditions

Telling "obvious" from "not obvious" by how the agent feels about it is
useless — a feeling cannot be checked. What works is a list of objective
signals, each taken from the project's own rules and each easy to recognise:

| Always stop | Why |
| --- | --- |
| The change grows `lint-baseline.xml` or `config/detekt/baseline.xml` | Baselines only shrink. Regenerating one turns a five-minute fix into permanent debt, and does it invisibly |
| A test is deleted, gets `@Ignore`, or has an assertion loosened | Never silently. A test that seems wrong is a discussion, not an edit |
| A new `@Suppress` or `tools:ignore` | Allowed only with a reason on the same line — and a human writes the reason |
| A new dependency in `libs.versions.toml` | The Compose stack is already on the classpath as seven artifacts and used nowhere |
| `versionCode`, `version.properties` or the versioning block is touched | Release-PR territory |
| Anything happens on `main` or `release` | The branches are protected; an attempt to work around that is itself the signal |
| The change alters public behaviour rather than the shape of the code | Refactoring and behaviour changes do not share a PR |
| Reviewer and agent have disagreed twice | A human settles an argument between two models faster than a third model does |

The first six rows are visible in the diff, which means they can later be
backed by a check in CI. The last two live in the rule only.

### Config

```yaml
agentConfig:
  permissions: permissionless

reactions:
  changes-requested:
    auto: true
    retries: 2
    escalateAfter: "30m"
    message: |
      Work through the unresolved review comments one at a time.
      For each: verify the claim rather than taking it on trust.
      If the fix is obvious and does not hit the stop list in
      docs/plans/AO_ROLLOUT.md, apply it and resolve the thread.
      If it does hit the list, or if there is more than one reasonable
      answer, stop: write an ESCALATION block (context, options with
      trade-offs, your recommendation) and wait. Do not push before
      the answer arrives.

  bugbot-comments:
    auto: true
    priority: info
```

The same paragraph belongs in `agentRules` as well, because a reaction
`message` is seen once while the rules are seen always.

### What to verify, and it matters more than the rest

Whether escalation fires. This cannot be established passively. It needs a PR
with a contested decision planted in it deliberately — a review comment that
can be satisfied either by fixing the code or by growing a baseline. The agent
must stop. If it silently picks one, the stage has not passed and the wording
of the rules is what needs work.

**Criterion to move on:** three PRs where the obvious comments were handled
without you, and at least one correct stop on a contested one.

**Rollback:** `changes-requested: auto: false`, back to running
`ali-process-pr-comments` by hand.

---

## Stage 6 — the phase planner

**Switched on:** sessions that plan rather than write code.

`docs/plans/REFACTORING_PLAN.md` is the large plan. A planner session takes the
next phase out of it and expands that phase into a detailed plan of its own, as
a separate file — say `docs/plans/phase-N-<slug>.md`. Such a session writes no
code.

The key idea: **the phase plan travels through the same machinery as the code.**
The session opens a PR containing exactly one new markdown file; the same two
reviewers land on it; contested points become stops under the stage 5 rules.
The "AI review of the plan" step therefore comes for free out of what is
already configured, and the decisions taken on the plan stay recorded in a file
rather than in a conversation.

This only works with tighter permissions — a planner has no business writing
anywhere it likes.

```yaml
agentRules: |
  # ... in addition to the general rules, for planning sessions:
  A planning session does not change code. The only file it may modify is the
  phase plan under docs/plans/. Do not settle open questions alone: list them
  with their options and a recommendation.
```

**Criterion to move on:** three phase plans accepted without being rewritten
from scratch.

**Rollback:** plan by hand, as today. Nothing else depends on this stage — it
is last for a reason.

---

## Stage 7 — the other projects

What is shared moves into `~/.agent-orchestrator/config.yaml` (`defaults`,
`notificationRouting`, reactions); what is per-project stays in each
repository's `agent-orchestrator.yaml`. By this point it is visible which is
which — right now it can only be guessed, which is why the global config stays
untouched until stage 7.

Almost certainly per-project: the `symlinks` set (every stack has its own
mandatory gitignored files), `postCreate` (`npm ci`, `composer install`,
`dart pub get` — Gradle has no such step), and the definition-of-done command.

---

## What AO will not do

Worth knowing, so no one goes looking for settings that do not exist.

- **AO does not merge.** `approved-and-green` only notifies, and `auto-merge`
  is an intent flag rather than a way around branch protection. A human presses
  the button, and that is correct.
- **Up to a two-minute delay.** Review-comment polling is throttled to once
  every two minutes per session. When it needs to be immediate: `ao review-check`.
- **The bot list is hardcoded** in the SCM plugin and cannot be configured from
  the project file. A reviewer posting from an account outside that list has
  its comments routed to `changes-requested` rather than `bugbot-comments`.
- **`permissionless` is not a sandbox.** The agent gets to run commands on the
  machine. The isolation here is the worktree — repository files — and nothing
  beyond that.
- **Parallel sessions consume the subscription limit in multiples.** An Opus
  orchestrator plus three workers is four full sessions. Moving review onto
  Codex helps; it does not solve it.
