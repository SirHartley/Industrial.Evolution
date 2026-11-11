# Industrial.Evolution

Starsector 0.98a mod (`IndEvo`) that adds industries, exploration content, abilities, consumable items and events to the campaign.

## Required workflow

These instructions apply to all coding agents. App-specific tools and model assignments are scoped below.

Reviews, explanations, audits, and proposals are read-only unless the user requests changes. For implementation, finish the authorized edit, commit, validation, pull request, and merge steps. Use reasonable assumptions for routine details; ask when a missing decision would materially change the scope or behavior. A request to stop or propose only overrides the implementation workflow.

### Read the current documentation

1. Read the current remote `CLAUDE.md` in full at the start of each new task. Within the same task, reuse the version already read unless the instructions change. Read new or changed instruction files before following them.
2. Use the `starsector-knowledge` skill before answering or changing anything related to Starsector. It covers 0.98a-RC8. Do not rely on memory.
3. Use the `house-style` skill for every task in this repository. Its personal rules (answers, vocabulary, comments, commits, punctuation) apply everywhere; this file wins on project details.
4. Use the `starsector-rules-writing` skill in knowledgebase mode for any change to `data/campaign/rules.csv`, dialogue or rule commands. Use the `patchlib` skill only when the official API cannot reach what is needed.
5. Use the `starsector-native-smoke` skill with [SMOKE_TESTS.md](docs/SMOKE_TESTS.md) when a change must be seen or exercised in the real game.
6. Third-party libraries (GraphicsLib, LazyLib, LunaLib, MagicLib, PatchLib, Console Commands, Nexerelin, Shadowyards, RAT) are not in the knowledge skill. Their compile jars are in `lib/` ([Compile dependencies](#compile-dependencies)); decompile one with the knowledge skill's Vineflower into a temporary directory, never into the repository. Ask the user for a jar only when it is missing there or a newer version is needed.
7. Select the required guides using [Which guide to read](#which-guide-to-read). Apply every matching row, including for reviews and fixes to existing behavior.

### Which guide to read

After reading this file, use the task to choose the next document. Read the relevant sections and their prerequisites; lookup tables do not need a cover-to-cover read. A task spanning several areas needs every matching route.

| Task | Required reading / order |
|---|---|
| Locate code or data, trace a feature, change an owner, registration or lifecycle | [ARCHITECTURE.md](docs/ARCHITECTURE.md) before tracing code; then the feature doc it routes to. |
| Change, fix or review one feature (an industry, exploration content, ability, item, dialogue system) | The feature doc under `docs/<area>/`, found through the [ARCHITECTURE.md](docs/ARCHITECTURE.md#feature-documents) table. Read its Contracts and Known issues before editing. |
| Add a new industry, condition, ability, item or other feature | [ARCHITECTURE.md](docs/ARCHITECTURE.md) (registration and package layout), the closest existing feature doc as a model, and the house-style `packages.md`. Write the new feature doc in the same commit. |
| Add, rename or read a player setting, whitelist or `settings.json` key | [SETTINGS.md](docs/core/SETTINGS.md) |
| Change the settings mechanism (reader, cache, LunaLib listener, settings checks) | [SETTINGS.md](docs/core/SETTINGS.md), then [SETTINGS_REWORK.md](docs/core/SETTINGS_REWORK.md) for the decisions behind it. SETTINGS.md describes current behavior; the rework doc is the record of decisions and the baseline it replaced. |
| Add or reuse a helper, timer, memory accessor, animation or trail | [UTILS.md](docs/core/UTILS.md) before writing a new helper. |
| Work on `rules.csv`, dialogue or a rule command | [RULE_COMMANDS.md](docs/dialogue/RULE_COMMANDS.md), the feature doc, and the `starsector-rules-writing` skill. |
| Touch another mod's integration (Nexerelin, Console Commands, Vayra, Prism, Shadowyards, BetterColonies) | [COMPATIBILITY.md](docs/core/COMPATIBILITY.md) |
| Write or change a PatchLib patch | [ARCHITECTURE.md](docs/ARCHITECTURE.md#patches), the feature doc, and the `patchlib` skill. |
| Combat scripts, weapon AI or projectiles under `jars/src/data/scripts` | [COMBAT.md](docs/core/COMBAT.md) and the feature doc that uses the weapon. |
| Revive or remove WIP code | [WIP.md](docs/core/WIP.md) |
| Plan, start or check the status of a new feature | [NEW_FEATURES.md](docs/NEW_FEATURES.md): plans, decisions and open questions per feature. It does not govern current behavior; the feature docs do. |
| Write or revise any player-facing text (descriptions, intel, dialogue, tooltips, LunaLib help text) | The house-style `player-text.md` and the feature doc. |
| Edit a guide or add documentation | [Documentation upkeep](#documentation-upkeep), the owning guide and its inbound links. |
| Change the build, rebuild the release jar, or prepare a release | [RELEASE.md](docs/RELEASE.md) and [Building](#building). |
| See or exercise a change in the real game, write a probe, or run a smoke test or live session | [SMOKE_TESTS.md](docs/SMOKE_TESTS.md) and the `starsector-native-smoke` skill. |
| Any implementation task | [CHANGELOG.md](docs/CHANGELOG.md): add or amend the task's entries in the current unreleased section before merge. |

### Technical references and conflicts

The feature docs own feature behavior; [ARCHITECTURE.md](docs/ARCHITECTURE.md) owns registration, lifecycle and cross-feature contracts; [SETTINGS.md](docs/core/SETTINGS.md) owns the settings mechanism; [UTILS.md](docs/core/UTILS.md) owns shared helpers. Engine facts come from the `starsector-knowledge` skill for 0.98a-RC8. Resolve any technical conflict against the exact source and record the evidence; do not pick a winner by filename, recency or an authority label.

`Changelog_todo` is the user's own list of open tasks and known issues. Feature docs link its entries; do not edit it unless asked.

### Make and record changes

- Use one task branch for the user's message.
- Make one commit per requested change. If one message contains several changes, commit them separately in the requested order.
- Commit messages are as short as possible: all lowercase, at most 8 words, programmer speak (`fix museum parade stacking`). One line only. No body, no conventional-commit prefix, no testing notes, agent or model names, co-author trailers, session metadata, links or trailing full stop.
- Update the affected documentation in the same commit, following [Documentation upkeep](#documentation-upkeep). Do this without a separate user request.
- Update `Changelog` in the same commit, using [CHANGELOG.md](docs/CHANGELOG.md).
- Bump versions, rebuild `jars/release/IndEvo.jar` and publish releases only when the user explicitly asks; follow [RELEASE.md](docs/RELEASE.md). Ordinary work leaves the release jar stale.
- Work only in the current task checkout. Live installations and unrelated checkouts are read-only unless the user explicitly asks you to change them.
- Do not deploy or synchronize after merge unless the user explicitly requests it.
- IE is released and players keep saves across versions. Every change to a class stored in the save (fields of scripts, listeners, intel, industries, memory values) needs a save compatibility check, and the changelog states save compatibility at release. Never claim save compatibility without evidence. See [ARCHITECTURE.md](docs/ARCHITECTURE.md#save-data).
- Prefer listeners and existing callbacks over polling. Before adding or retaining a polling solution, verify against the exact source that no existing callback covers the requirement. Document the missing coverage and keep the scan scope and frequency to what the behavior needs.
- Register new listeners and scripts through the existing idempotent `register()` or `getInstanceOrRegister()` pattern and list them in [ARCHITECTURE.md](docs/ARCHITECTURE.md#registration-and-lifecycle).

### Validate and merge

- Every runtime-affecting final branch must pass the full clean build described in [Building](#building).
- Build the exact final remote task-branch revision. Earlier builds, partial builds, IDE analysis, and static checks do not satisfy the gate.
- Missing compilers or required dependencies are merge blockers. Documentation-only changes do not require a build.
- A native smoke test supplements the compile gate and is not a merge requirement. When one ran, record it in the PR ([SMOKE_TESTS.md](docs/SMOKE_TESTS.md#results)).
- A change to `rules.csv` shows no new errors in the `starsector-rules-writing` skill's `rules_check.py . --baseline git:origin/dev`. A change to any CSV keeps UTF-8 without BOM, straight quotes and the column count.
- No CI runs on pushes or pull requests. The only workflow is the manual `release.yml` ([RELEASE.md](docs/RELEASE.md#release-workflow)); start it only on the user's command. Do not add triggers or check workflows.
- Open a pull request and merge it when the work is complete. Do not leave finished work on an unmerged branch.
- Use the connected GitHub app to create, inspect, and merge pull requests. Use `gh` only if the app is unavailable.
- Fetch current remote `master` before branching and again before merging. Integrate intervening changes, push the exact final commit, and verify the merged remote revision.

### Branch cleanup

GitHub normally deletes a pull request's head branch after merge. Verify that the branch is gone. Remove it manually when it was pushed without a pull request, its pull request was closed without merging, or it predates the repository setting. If deletion returns HTTP 403, report the branch name and leave it in place.

After merge, fast-forward the local task branch to the new `master`.

## Documentation upkeep

Update documents automatically as part of each relevant change. Before committing, check which entries the diff makes stale and update those entries in that commit.

| Document | Owns | Update when / how |
|---|---|---|
| `AGENTS.md` | Discovery for other agents | Keep it a short pointer to this file. Do not copy workflow policy into it. |
| `CLAUDE.md` | Task scope, subagent instructions, tools, commits, PRs, builds, comments, document maintenance | Replace changed policies here and update links. Keep provider-specific instructions explicitly scoped. |
| `docs/ARCHITECTURE.md` | Overview: areas, feature doc index, registration and lifecycle in `ModPlugin`, save data, data layout, cross-feature contracts, dormant code summary | Update the affected route, registration line or contract; remove obsolete owners. Use exact paths and symbols. No feature internals (those go in the feature doc), lore, release history or duplicate policy. |
| `docs/<area>/<FEATURE>.md` | One feature each: files, ids, registration, flow, contracts, known issues | Update with every change to that feature's code, data or settings. Keep the template sections. Add a row to the ARCHITECTURE.md feature table for a new doc. |
| `docs/core/SETTINGS.md` | Settings reader, LunaLib tabs, `settings.json` key groups, whitelists | Update when the mechanism, a tab, a key group or a whitelist changes. Setting values belong in the feature doc. |
| `docs/NEW_FEATURES.md` | New feature plans: decisions, open questions, staged plans | Record decisions when made; when a stage merges, move its technical detail to the feature doc and ARCHITECTURE.md and leave a pointer. |
| `docs/core/SETTINGS_REWORK.md` | Settings rework record: decisions, the baseline it replaced, staged plan | Record decisions; tick subtasks as they merge and move implemented detail to SETTINGS.md and ARCHITECTURE.md. Does not govern setting values or help text. |
| `docs/core/UTILS.md` | Shared helpers in `indevo/utils` | Update when a helper is added, removed or changes its contract. |
| `docs/core/COMPATIBILITY.md` | Integrations with other mods | Update when an integration, a hard or optional dependency, or a config file for another mod changes. |
| `docs/RELEASE.md` | Version numbers, release builder, package contents, release workflow, version file and publishing | Keep it in step with the `ReleaseBuild` constants, `.github/workflows/release.yml` and `IndEvo.version`; change them together. |
| `docs/SMOKE_TESTS.md` | Smoke test setup, the `native-smoke/` wrapper and probes, reading and reporting results | Update when `native-smoke/` changes, the modding install or dependency set changes, or the skill's runner contract changes. |
| `Changelog` and `docs/CHANGELOG.md` | Shipped change notes and how agents write them | Add or amend unreleased entries with each implementation task; preserve published entries. |
| `.claude/agents/*.md` | Subagent definitions | Update when the dispatch rules or model assignments change. |

Give each fact one home. Move useful detail to its proper owner and link to it. After moving a section, repair inbound links and check that no requirement or technical constraint was lost.

For a new or renamed guide, update the required-reading table, this ownership table and the ARCHITECTURE.md feature table and links in the same commit. State when to read it and what it does not govern.

Write documentation in short programmer speak with normal capitalization and punctuation. Use direct verbs and concrete nouns; remove slogans, inflated claims, repeated contrasts, and commentary about how important the document is. Keep identifiers exact. Cite `file:line` for contracts. No history: describe the current state. Preserve the distinction between absolute restrictions and defaults such as "normally" or "prefer."

## Answers and wording

- Keep answers short and succinct: lead with the result, no preamble or recap. Detailed evidence goes in the pull request.
- Programming-related text (commits, PRs, comments, identifiers, technical docs) uses the vocabulary programmers have used for the last 20 years. Prefer the old word: use, fix, add, show; not leverage, address, introduce, surface.
- No em dashes and no hyphen used as a dash. No emojis unless topical.

## Comments and documentation

- Default to no comments. Add one only where it explains what is not apparent from the code: engine quirks, required ordering, lifecycle rules, save compatibility, serialization, reflection, units, invariants, non-obvious math, cross-file coupling.
- Comments are minimal, all lowercase, terse programmer speak, one line where possible (`// also called for refit previews, listener never advances there`). Code identifiers keep their exact spelling.
- Add no other comments: no javadoc, section headers, restated code, history, TODOs or commented-out code.
- Fix or remove a comment your change makes stale; leave untouched comments alone. Existing IE code has older comments in other styles; do not mass-reformat them.
- Documentation records current architecture, contracts, entry points, and live hazards. It must not become a development diary, pull-request history, or postmortem.
- Update or remove stale documentation in the same commit as the behavior change.

## Code layout

- Packages follow `indevo/<area>/<feature>/<role>/`. Areas: `industries`, `exploration`, `abilities`, `items`, `dialogue`, `economy`, `submarkets`, `ambience` (visual only effects), `config` (player settings, flat), `utils`, `ids`, `console`, `other`, `WIP`, `tools` (release builder, never shipped), `skillshot` (framework kept in Catch.Release's layout, see its README). Role packages are named for the engine type (`industry`, `conditions`, `listeners`, `plugins`, `scripts`, `rules`, `intel`, `entities`, `hullmods`, `terrain`, `fleet`, `memory`, `patches` for PatchLib patches). See the house-style `packages.md`.
- Ids, tags, memory keys and setting keys carry the `IndEvo_` prefix (`$IndEvo_` for memory). Shared ids live in `indevo/ids/Ids.java` and `ItemIds.java`; feature-local keys stay in the feature.
- Class member order: nested enums; static fields; instance fields; other nested types; constructors; lifecycle and public entry points in execution order; helpers near their callers. Group fields by responsibility. No section header comments.
- All code is Java 17. Do not add Kotlin: the user's IDE does not compile it.

## Agent behavior

- Keep the user's selected model. The main agent owns planning, integration, and shipped code. Delegate bounded research only when authorized.
- Treat repository instructions as project requirements and skill guidance as scoped reference material. If a skill blocks work or changes its scope, identify the exact instruction and explain the effect.
- Give brief progress updates and concise final results. Put detailed evidence in the pull request.

### Subagent dispatch template

Use it for every authorized subagent task. Include the user's clarifications explicitly: the subagent has not seen the conversation. Subagents do not inherit skills: pass the absolute `starsector-knowledge` skill root and tell them to read its `SKILL.md` and follow its Accuracy rules.

```text
Task: <specific question or result, and what counts as complete>
Checkout: <absolute path and revision; note relevant uncommitted changes>
Context: <user clarifications, constraints and work already completed>
Access: <allowed sources; permitted writes and paths, if any>
Starting points: <relevant guides, files or symbols, if known>
Return: <desired detail, format and output path, if needed>

1. Read AGENTS.md and CLAUDE.md, then follow the required-reading routes for
   this task. Starting points are navigation help, not the full reading list.
   Follow required skills and linked prerequisites. Read full documents where
   required; otherwise read the relevant sections and their context.
2. Use searches to locate material. Open the surrounding section before using
   a match as evidence. Read definitions, scope notes, exceptions and warnings;
   do not answer from filenames, headings or search snippets alone.
3. Preserve what each passage applies to: game version, vanilla or mod code,
   current implementation, example, deprecated code or external tool. An account
   of an old mistake is not a current instruction to repeat it. Keep qualifiers
   such as "normally", "only", "never" and "not" when summarizing.
4. Use lookup tables to find relevant entries and follow their source links.
   An entry's presence does not prove it is usable in every context. Do not read
   or count an entire lookup table unless the task requires that coverage.
5. For implementation work or behavioral claims, follow the documentation to
   the relevant code, callers, registration and data within the allowed sources.
   Check the actual version and applicable source corrections. Do not invent
   APIs, names or behavior to fill gaps. If access is documentation-only, say
   what the guide describes and leave implementation verification to the parent.
6. Distinguish documented facts, source-verified behavior and your inferences.
   Before claiming a contradiction, cite what both passages actually say and
   check whether they concern the same version and situation. If evidence is
   missing or still conflicts, identify the uncertainty instead of guessing.
7. Return the requested result with file/line references or source URLs for
   material claims, plus checks performed and remaining questions. For searches
   or crawls, state the scope searched; "not found there" does not prove absence
   everywhere. Do not invent findings or expand the task to fill the report.
```

The main agent checks the cited evidence before using a conclusion to change code or documentation. A subagent's report does not replace the parent's integration review or the required validation.

## Claude model assignments

| Work | Model |
|---|---|
| Planning, architecture, sequencing, and all code changes | The user's selected model in the main session |
| Repository search, call tracing, and reading platform sources | Opus subagents at medium effort, using the `researcher` agent (`.claude/agents/researcher.md`) |

Subagents perform research and scoping, not shipped code.

## Version names

Versions use major.minor.letter (current `4.1.b`). See [RELEASE.md](docs/RELEASE.md#versions); do not bump versions or publish tags as part of ordinary task work.

## Building

### Required compile gate

After the final runtime-affecting commit and before merge:

1. Fetch and build the exact remote task-branch revision.
2. From the repository root, with JDK 17, run `java jars/src/indevo/tools/ReleaseBuild.java compile` ([toolchain](#toolchain)). It compiles every Java file under `jars/src/` that the IntelliJ module compiles (it reads the `excludeFolder` entries in `IndEvo.iml`) against `lib/`, in a fresh `out/release-build-*` directory, runs the settings checks (`SettingsChecks`, [SETTINGS.md](docs/core/SETTINGS.md#settings-checks)) and checks the resulting jar. It never touches `jars/release/IndEvo.jar`.
3. Require a successful exit with no compile errors and no failed settings check.
4. Record the command, branch commit, toolchain versions and result in the pull request. The final reply links the PR and states the result and any untested behavior.
5. Stop before merge if the build, the toolchain or a required dependency is unavailable.

A change to the builder also runs its checks: `javac --release 17 -encoding UTF-8 -d out/release-tools jars/src/indevo/tools/ReleaseBuild.java jars/src/indevo/tools/ReleaseBuildChecks.java`, then `java -cp out/release-tools indevo.tools.ReleaseBuildChecks`. Release jar, packaging and publishing: [RELEASE.md](docs/RELEASE.md).

### Toolchain

- JDK 17; the builder refuses other versions. Cloud sessions ship JDK 21 only and `api.adoptium.net` is blocked there: take Temurin 17.0.13+11 from GitHub, `https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz`, SHA-256 `8682892fc02965930b9022c066fa164dd6f458ef4a5dc262016aa28333b30f49`.
- Unpack it outside the repository and verify the hash before use.

### Compile dependencies

From `IndEvo.iml` and the imports:

- From the game's `starsector-core`: `starfarer.api.jar`, `starfarer_obf.jar` and `fs.common_obf.jar` (IE imports some obfuscated classes), `janino.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`.
- Mods: GraphicsLib, LazyLib, LunaLib, MagicLib, PatchLib (API jar only), Console Commands (`lw_Console`), Nexerelin (`ExerelinCore`), Shadowyards (`SHI`), Random Assortment of Things (`assortment_of_things`, used by the courier port).

Dependency source: `lib/` in the repository, read-only. It holds the game jars (`starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `janino.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`) and the mod jars `Graphics.jar` (GraphicsLib 1.9.0), `LazyLib.jar` (LazyLib 3.0.0), `LunaLib.jar` (2.0.1), `MagicLib.jar` (1.5.1), `PatchLibAPI.jar` (PatchLib 1.0.3), `lw_Console.jar`, `ExerelinCore.jar` (Nexerelin 0.12.2d), `SHI.jar` (Shadowyards 0.9.5-rc4) and `RandomAssortmentofThings.jar` (RAT 3.3.1). Only the jars the gate needs are kept, never full mod folders. Replace a jar only when the user supplies a new version, and note the version here. The builder puts every `lib/*.jar` on the classpath and hashes them into the release jar's `Source-SHA256`, so a replaced jar makes the release jar stale. The game loads only the jars `mod_info.json` lists, so `lib/` never reaches the game and never ships ([RELEASE.md](docs/RELEASE.md#what-ships)).

IntelliJ on the user's machine (`IndEvo.iml`, artifact `IndEvo:jar`) builds `jars/IndEvo.jar` for local testing. See [ARCHITECTURE.md](docs/ARCHITECTURE.md#build).

## Repository navigation

After the required read of this file, follow [Which guide to read](#which-guide-to-read). [ARCHITECTURE.md](docs/ARCHITECTURE.md) is the code and data map, not a substitute for the feature docs.
