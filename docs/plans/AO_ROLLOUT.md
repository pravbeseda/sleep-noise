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

**Stage 1, two tasks of the three done, the third running.** A stage that was
not in the original list, 1a, was inserted along the way: its job is merged and
required, and its criterion is not met yet — `Guardrails` has so far only run on
PRs that touch no baseline, and the `Locale` task is the PR that will test it.

Task 3 is that `Locale` task, and it is the first to run with the config
actually applied: it was moved into AO's database before the session was
spawned, so `app/google-services.json` and `.claude` are symlinks AO created,
the model and permission mode came from that config, and the rules reached the
agent. Its PR is #21 and is not reviewed yet, so the write-up of the task —
including what it says about stage 1's criteria — waits for that to finish.

### What stage 1 has already proved

Three of the four expectations in the stage 1 table are now measured facts
rather than guesses. The agent ran the full definition-of-done suite inside its
worktree and reached green CI, which it could not have done had any of them been
wrong: the hooks fired from the shared `.git/config`, the Spotless ratchet found
`origin/main`, and `versionCode` counted the full history. The worktree question
is settled for those three.

Two things remain, not one. The three-tasks-without-intervention count is the
lesser of them. The fourth expectation — the `app/google-services.json` symlink —
did not prove what it was read as proving: the mechanism never ran, so the row
the stage 1 table calls "the real test of the stage" is untested, and finding
that out led somewhere larger. See below. **Stage 1 does not close on the task
count alone.** The symlink is the one mechanism of the four a worktree does not
supply for free — the other three fall out of the shared object store and the
shared `.git/config` — so a run of three green PRs with the file put in place by
hand answers a smaller question than the stage was set up to ask. Closing it
needs a session where the symlink is created by AO, which needs #19.

### The config file is not being read

Written down because everything above and below was designed on the assumption
that it is. The installed build of AO does not read `agent-orchestrator.yaml` at
all. Four facts, in order of how conclusive they are:

- In the surviving worktree of task 2, `app/google-services.json` is an ordinary
  file and `.claude` an ordinary directory — neither is a symlink. The
  `symlinks:` block has never run. The file arrived some other way, so the row
  the stage 1 table calls "the real test of the stage" is untested.
- The project's stored config is empty where this file is full:
  `sqlite3 ~/.ao/data/ao.db 'select config from projects'` gives
  `{"defaultBranch":"main","agentConfig":{},…}` — no permissions, no model, no
  symlinks, no rules.
- Neither the daemon binary nor `app.asar` contains the string
  `agent-orchestrator.yaml`; of YAML filenames the daemon knows `config.yaml`
  and agent-specific ones only.
- `ProjectConfig` in the daemon's own schema has no `reactions` key at all, so
  the reaction table below has nothing to bind to in this build. Config is set
  through `ao project set-config` and stored in that database.

What this costs the rollout: every stage that switches something on by editing
this file switches on nothing. The two tasks so far ran under AO's defaults, not
under the rules in `agentRules`. Issue #19 carries the repair — either the
config moves into `ao project set-config`, or the file is confirmed as the input
of a build that reads it. Until then, decisions 3 and 4 below are decisions
about what the config should say, not about what any session has run under.

### How the config reaches AO

One command, and it replaces the whole object:

```bash
ao project set-config sleepnoise --config-json "$(cat <built ProjectConfig>.json)"
```

`set-config` **replaces** a project's config rather than merging into it. Its
own help says so — "Set fields via flags, pass the whole object with
`--config-json`, or `--clear` to remove all config" — and that is the trap
worth naming: a later `--agent-rules "…"` on its own leaves the other fields
alone, but a later `--config-json` that omits a field drops it, silently and
without a diff to notice. So every field this file describes travels together,
in one payload, or not at all.

The payload is built from `agent-orchestrator.yaml` — `defaultBranch`,
`agentConfig`, `symlinks`, `agentRulesFile`, `agentRules`, `worker`,
`orchestrator`, `trackerIntake` — keeping the yaml as the readable source and
the database as what actually runs.

Four of the file's keys have no field of that name in the schema, and they are
not equally lost:

- **`agent`** survives, renamed. It becomes `worker.agent` and
  `orchestrator.agent`, which is where the schema keeps a harness. The stored
  config for this project carries `claude-code` in both.
- **`runtime` and `workspace`** have no counterpart and need none: the daemon
  runs sessions under tmux, and a git project gets a worktree — `ao spawn`
  states it plainly ("Git projects use worktrees; Scratch uses an AO-managed
  directory"). The file names what happens anyway; it just does not choose it.
- **`reactions`** is the only loss that changes behaviour, and it is what #19
  is for.

The translation is by hand today, which is a step waiting to be automated and
is tracked in #19 rather than carried here: a plan PR that ships tooling is two
changes in one diff.

Verify what landed rather than trusting the command:

```bash
sqlite3 -readonly ~/.ao/data/ao.db "select config from projects where id='sleepnoise'"
```

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
  `permissions: default` waiting for approvals, which decision 4 below removes.

Processing the single review comment in that same session took the total to
2.5M tokens. Lesson: once a session is long, run `ali-process-pr-comments` in a
**fresh** agent session started in the worktree directory, not in the session
terminal. The branch is the same; the context is not.

### Task 2 — issue #14, PR #15 (`ao/sleepnoise-6/ci-guardrails`)

Added the `Guardrails` job described under stage 1a. Three things worth
carrying forward:

- **The agent argued back, and was right to.** Review asked it to fail a
  deleted test as well. Rather than implement that, it showed that the obvious
  form of the check — counting `@Test` — would fail the `ExampleUnitTest`
  removal the quality plan schedules, opened issue #16, and wrote the gap into
  `CLAUDE.md` in plain words. This is the behaviour stage 5 is meant to produce
  through escalation, arriving on its own four stages early. It is also what
  settled the worker-model decision below.
- **Documentation was updated this time — but only one file of two.** The task 1
  collision did not recur for `CLAUDE.md`. `README.md` describes the same
  required checks and went stale unnoticed, and so did the definition-of-done
  command, which now covers four of the five required checks. Both were caught
  in review and fixed by hand afterwards. Hence the second sentence in the
  documentation rule below.
- **The one landmine in the spec was stepped over, not into.** `grep -c '<issue'`
  on the lint baseline returns 30 against 29 real entries, because the root
  `<issues>` element matches too. The issue named the trap explicitly and the
  agent handled it — which is not evidence it would have found it alone.

### Decisions taken

1. **Documentation versus scope — settled, in `agentRules`.** "Documentation
   that the change makes false is part of the change, not a widening of scope.
   Update it in the same PR and say so in the description. `CLAUDE.md` and
   `README.md` describe the same project and go stale silently: when one of
   them stops being true, check the other." The last sentence is not from the
   original proposal; task 2 showed the rule catches half the problem without
   it.
2. **Worker model — staying on Opus, deliberately.** The proposal was Sonnet, on
   the strength of $1.37 for a two-line fix. Task 2 changed the answer: the
   push-back that produced issue #16 is the judgement a larger model is kept
   for, and it landed on a task where a cheaper one would plausibly have built
   the bad check instead. The cost observation still holds but points elsewhere
   — 1.3M tokens on task 1 was 26 turns re-reading a 50k context, which
   `accept-edits` and a fresh session for review comments address directly and a
   smaller model would not.
3. **The model is pinned, as an alias.** `agentConfig.model: opus`. This half of
   the question was about determinism rather than tier: a GUI default that
   changes between task 2 and task 4 invalidates the three-task measurement
   without announcing itself, and the file claims to describe behaviour while
   the largest behavioural knob sat outside the repository. An exact identifier
   was rejected as the opposite failure — it is the one line in the file that
   goes stale by itself, at worst by refusing to start a session on the day the
   id is retired. AO reads `worker.agentConfig.model` first and falls back to
   `agentConfig.model`, so with no `worker` block the pin covers every session;
   the value reaches the agent as `claude --model opus`.
4. **`permissions: accept-edits` switched on ahead of stage 2.** Stage 1 asked
   before acting so that the agent's intentions were visible; two tasks made
   them visible and none of the prompts was a surprise. What the waiting cost
   is measured — task 1, 21 minutes of wall time against 2m34s of API time.
   Permissions and reactions are separate knobs — which turned out to matter
   less than it reads: the reaction keys this file counted on are not in the
   build at all, so stage 2 has no key to flip and never had one. See "The
   config file is not being read" above and the rewritten stage 2 below.

   This does not restart the stage 1 count, and the difference from decision 3
   is worth stating rather than assuming. Stage 1 measures the build, not the
   agent: whether the symlink resolves, the hooks fire, the ratchet finds
   `origin/main` and `versionCode` sees the full history. A permission mode
   changes none of those four. The model does bear on what the later stages
   measure — the quality of the agent's judgement — which is exactly why it is
   pinned before the count that measures it. Approvals were themselves a hand
   reaching in, so a task run under `accept-edits` is a cleaner reading of
   "three tasks without intervention" than the two before it, not a weaker one.

   Which task that first is depends on #19, not on this file. If the config is
   still unread when the `Locale` task starts, task 3 runs under AO's defaults —
   approval prompts and all — exactly as tasks 1 and 2 did, and the write-up of
   task 3 has to say which of the two it was. A measurement condition recorded
   here is not one that applied.

### Next task

`Locale` on the three `String.format` call sites in `timer/` — `TimerView.kt:43`,
`TimerController.kt:18` and `:20`. Deliberately chosen second because it is the
first task to touch **`app/lint-baseline.xml`** (4 `DefaultLocale` entries),
which means `./gradlew updateLintBaseline` and stripping the informational
entries it adds back. That is the most temperamental part of the tooling and is
better met on a three-line change.

It now carries a second job: the baseline goes from 29 entries to 25, which is
the first legitimate shrink the stage 1a guardrail will see. A check that has
only ever passed on PRs that touch no baseline has not been tested.

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

**Switched on:** nothing — deliberately, and in this build also unavoidably.
`agent-orchestrator.yaml` sits in the repository with every reaction at
`auto: false`, and those keys turn out to configure nothing. AO creates the
worktree, launches Claude Code, displays state — and nudges the session on CI
failure, review feedback and merge conflicts, which is not something this stage
chose.

**What is actually under test:** whether the build survives a worktree. It is
the one question whose answer, if it is "no", makes every later stage
worthless. Spawn one small task and, inside its worktree, run the Definition of
done command from `CLAUDE.md` by hand — named rather than copied here, because a
copy of it in a document nobody runs goes stale the first time a check is added,
which is exactly what happened to the copy this file used to carry.

Three places where this could break, and why they probably will not:

| Mechanism | Risk in a worktree | Expectation |
| --- | --- | --- |
| `app/google-services.json` | Gitignored and required; a worktree does not get it | Covered by the symlink in the config — **this is the real test of the stage**, and it is still untested: the symlink has never been created, see #19 |
| `.githooks` via `core.hooksPath` | The setting lives in `.git/config`, which a worktree "does not have" | Works: a worktree shares `config` with the main repository |
| Spotless `ratchetFrom("origin/main")` | Needs the `origin/main` ref | Works: the object store is shared |
| `versionCode` from `git rev-list --count HEAD` | A shallow clone undercounts | Works: the worktree sees the full history |

The right-hand column is exactly that — expectations. This stage exists to turn
them into measured facts.

**Criterion to move on:** three agent-made PRs in a worktree taken to green CI,
**and** one of them with `app/google-services.json` put there by AO rather than
by hand. Reaching into the code by hand is fine at this stage; reaching into the
build is not — and a file placed by hand to make the build work is reaching into
the build. The second half of the criterion is blocked on #19.

**Rollback:** delete `agent-orchestrator.yaml` and work as before.

---

## Stage 1a — machine-checkable guardrails

**Switched on:** nothing in AO. A fifth CI job, `Guardrails`, added as a
required status check.

This stage is not in the original list. It was inserted after task 1 because
stage 2 as written violates the ordering principle above. Its criterion reads
"none of which came down to weakening a check ... verified by reading the diff;
nothing catches it automatically" — so the first automation switched on would
have depended on a manual check. The first two rows of the stage 5 stop list
are visible in a diff, and the plan already noted they "can later be backed by
a check in CI". Later turned out to be now.

The job enforces those two rows, and only the half of each a diff makes
visible: neither baseline grows (entry counts against the base commit), and no
`@Ignore` or `@Disabled` is added under the test source sets. It needs no JDK,
no Android SDK and no Gradle, so it costs seconds rather than minutes.

Deleting a test outright is deliberately not covered — a bare `@Test` count
would fail the `ExampleUnitTest` removal the quality plan schedules. That half
needs its own design and has issue #16.

**Order that matters:** merge the job first, watch it run green on a pull
request, add it to the branch protection last. A required check that no run
ever reports blocks every merge in the repository.

**Criterion to move on:** the job has passed on a PR that legitimately shrinks
a baseline. The `Locale` task is that PR.

**Rollback:** remove `Guardrails` from the required checks. The job stays and
reports without blocking.

---

## Stage 2 — CI self-repair

**Switched on:** nothing, and that is the whole difference from how this stage
was written. The reaction it meant to enable is built into the daemon and is
already running.

### What the build actually does

AO watches every PR it owns and writes what it sees into its own database —
`ci_state`, `review_decision`, `mergeability`. When one of those turns bad it
sends the worker session a message on its own. The daemon calls them nudges,
keys them `ci:<url>:<check>`, `review:<url>` and `merge-conflict:<url>`, and
remembers what it already said in `pr.last_nudge_signature`, a small JSON
document of `{"seen": …, "attempts": …}` so a daemon restart does not re-fire
the lot.

Three things follow, and they are what this stage has to be rewritten around:

- **There is no switch.** `ProjectConfig` has no `reactions` key at all, so
  `auto: true`, `retries`, `priority` and `escalateAfter` have nothing to be
  written into. The nudges are on for every session, including the two that
  ran before any of this was understood.
- **The message is not ours.** The CI nudge ships its own wording — use the log
  tail and the failure URL first, fetch full logs only if needed, fix and push
  again. The paragraph this stage used to put in `message:` cannot be delivered
  that way.
- **`retries: 2` does not exist.** The daemon counts attempts per nudge key but
  exposes no cap, so "two tries and stop" is a rule the agent follows or does
  not, not a limit the machine enforces.

### Where the constraints go instead

Two places, and the split is the same one the rest of this file uses — what a
machine can check, and what only prose can say.

- **The machine-checkable half is already CI.** Stage 1a's `Guardrails` job
  fails a PR that grows either baseline or adds an `@Ignore`/`@Disabled` under
  the test source sets. An agent repairing a red build cannot quietly take the
  cheap way out of those two, because the way out is itself a red build.
- **The rest is `agentRules`,** now that the rules actually reach a session:
  do not touch the baselines, do not weaken a test, and if the only available
  fix is one of those, stop and ask. A reaction `message` would have been seen
  once; the rules are in front of the agent the whole time, which is the better
  place for them anyway.

Getting them there is not an edit to `agent-orchestrator.yaml` — see "How the
config reaches AO", and issue #19.

**What is under test:** whether an unattended repair stays honest. The nudge
fires whether or not this stage is "reached", so the only question left is
whether the rules and `Guardrails` are enough to keep a red build from being
fixed by lowering the bar.

**Criterion to move on:** three red builds repaired by the agent alone, none of
which came down to weakening a check. `Guardrails` now catches two of the ways
that could happen; the rest is still verified by reading the diff.

**Rollback:** there is no key to flip back. What is left is to stop leaving a
session alive while its PR is in CI, and `ao session kill <id>` if one is
already loose. Worth knowing before rather than during.

---

## Stage 3 — one reviewer

**Switched on:** `reviewers: [claude-code]`. This one is a real switch:
`ProjectConfig.reviewers[]` exists in the build, unlike the reaction keys. What
it cannot do is keep the result to itself — the `review:<url>` nudge reaches the
worker whether or not anyone here has decided stage 5 has started, so "runs but
is only displayed" is not a state this build has. Read the findings before the
agent acts on them, or spawn no worker while the review runs.

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

The substantial stage, and like stage 2 it switches nothing on: the
review-feedback nudge is built into the daemon too, keyed `review:<url>`, with
its own wording — address the requested changes and push, no need to re-fetch
the review. It fires when a review lands, whether or not anyone here decided
the stage had started.

So what is left to build is the half AO does not provide: the stop. Without it
the agent quietly makes the decisions that `ali-process-pr-comments` currently
puts to a human one at a time.

### How escalation is built

AO has no notion of "the agent is unsure", and the three parts the plan
expected to lean on — `retries`, `escalateAfter`, a configurable
`agent-needs-input` reaction — are not in this build. What is in it is the
session state machine and one notification type: an agent that stops and waits
moves to `needs_input`/`blocked`, and AO raises a `needs_input` notification
for it. That is the whole carrier.

So the mechanics are: a rule in `agentRules` forces the agent to stop and wait
→ the session goes to `needs_input` → the notification lands → the answer goes
back with `ao send --session <id> --message '…'`.

Two things measured while setting this up, both of which bite exactly here:

- **`ao send` refuses while the session sits on a permission prompt** —
  `SESSION_AWAITING_DECISION`. The answer has to be typed in the session
  terminal instead. An escalation that arrives while the agent is also waiting
  for a command approval is therefore not answerable through the CLI.
- **`accept-edits` covers file edits, not shell commands.** Every `./gradlew`
  invocation still asks, so a session left alone stops on its own regularly
  without any escalation being involved. Distinguishing "stopped because it was
  told to" from "stopped because it wants to run a command" is a matter of
  reading the pane, not of reading a status.

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

`Guardrails` backs the first row outright and the second one only in part: it
fails an *added* `@Ignore` or `@Disabled` under the test source sets, and
nothing else in that row. A deleted test is deliberately not covered — a bare
`@Test` count would fail the `ExampleUnitTest` removal the quality plan
schedules, which is issue #16 — and a loosened assertion is not checked at all.
Both of those still depend on someone reading the diff. The next four rows are
visible in a diff and could be machine-checked on the same terms; the last two
live in the rule only.

### The rule, which is the whole configuration

The paragraph goes into `agentRules`, through the one command described under
"How the config reaches AO":

```
Work through the unresolved review comments one at a time. For each: verify
the claim rather than taking it on trust. If the fix is obvious and does not
hit the stop list in docs/plans/AO_ROLLOUT.md, apply it and resolve the
thread. If it does hit the list, or if there is more than one reasonable
answer, stop: write an ESCALATION block (context, options with trade-offs,
your recommendation) and wait. Do not push before the answer arrives.
```

There is no `message:` to put it in and no `bugbot-comments` channel to route
anything to — a bot's review reaches the agent through the same `review:<url>`
nudge as a person's.

`permissions` stays at `accept-edits` here rather than moving to
`bypass-permissions`, and the reason is the signal rather than the safety.
`needs_input` is the only thing AO raises, and while every `./gradlew` still
asks for approval that one state means both "waiting for permission" and
"stopped because a rule said to". This stage measures the second. Telling them
apart currently means reading the pane, which is exactly the manual step
escalation exists to remove.

`bypass-permissions` would clean the signal up — almost every stop would then
be a deliberate one — and that is a real argument for it, not against. It is
also the last manual brake on a stage that has never once been exercised, so
the order is: get an escalation to fire and be answered, then decide. **Open
question, deliberately left open:** move stage 5 to `bypass-permissions` once
the first correct stop has been seen.

### What to verify, and it matters more than the rest

Whether escalation fires. This cannot be established passively. It needs a PR
with a contested decision planted in it deliberately — a review comment that
can be satisfied either by fixing the code or by growing a baseline. The agent
must stop. If it silently picks one, the stage has not passed and the wording
of the rules is what needs work.

One measurement already argues both ways. Task 2 produced exactly this
behaviour unprompted — it refused a review request, showed why the obvious
check was wrong and opened issue #16 — with no rule telling it to. Task 3, the
first session the rules actually reached, opened by trying to create its own
branch against a rule that says in plain words not to. A rule that reaches the
agent is not a rule the agent follows, and this stage is where that difference
becomes expensive.

**Criterion to move on:** three PRs where the obvious comments were handled
without you, and at least one correct stop on a contested one, answered through
`ao send`.

**Rollback:** the nudge cannot be turned off, so the rollback is to stop
leaving sessions alive on PRs under review, and to go back to running
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

What is shared moves into whatever global configuration the build then has —
the legacy `~/.agent-orchestrator/config.yaml` this line used to name is gone,
and per-project settings live in AO's database (see "How the config reaches
AO"). What is per-project stays per-project. By this point it is visible which
is which — right now it can only be guessed, which is why nothing global is
touched until stage 7.

Almost certainly per-project: the `symlinks` set (every stack has its own
mandatory gitignored files), `postCreate` (`npm ci`, `composer install`,
`dart pub get` — Gradle has no such step), and the definition-of-done command.

---

## What AO will not do

Worth knowing, so no one goes looking for settings that do not exist.

- **AO does not merge.** An approved, green PR raises a `ready_to_merge`
  notification and nothing else — the notification types in this build are
  `needs_input`, `ready_to_merge`, `pr_merged` and `pr_closed_unmerged`. A human
  presses the button, and that is correct.
- **Polling has a delay.** AO observes PR state on its own schedule, so a nudge
  arrives after the event rather than with it. A review pass can be forced with
  `ao review trigger <session>`; there is no such command for CI, whose state is
  read from the PR.
- **There is no separate channel for bot comments.** Every review reaches the
  agent through the same `review:<url>` nudge, whoever posted it, so "route
  bugbot to info" is not a setting that exists.
- **`bypass-permissions` is not a sandbox.** The agent gets to run commands on the
  machine. The isolation here is the worktree — repository files — and nothing
  beyond that.
- **Parallel sessions consume the subscription limit in multiples.** An Opus
  orchestrator plus three workers is four full sessions. Moving review onto
  Codex helps; it does not solve it.
