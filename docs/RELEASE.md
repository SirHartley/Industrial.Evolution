# Release

Version numbers, the release builder, what a release contains, the release workflow and how a release runs. Read it before bumping a version, rebuilding the release jar or publishing. Changelog entries are in [CHANGELOG.md](CHANGELOG.md); the toolchain and the compile gate for ordinary changes are in [CLAUDE.md](../CLAUDE.md#building).

Nothing in this guide runs unless the user asks: no version bumps, no rebuilt release jar, no tags, no published releases. Ordinary work goes into the changelog's `- UNRELEASED` section and leaves the committed release jar stale on purpose.

## Versions

Scheme `major.minor.letter`, lowercase letter, no `v` prefix. Current public version `4.1.b`; the unreleased section is `4.2.a`.

| Part | Change | Example |
|---|---|---|
| First number | Major release, usually not save compatible | `3.3.e -> 4.0.a` |
| Second number | Gameplay additions players will notice | `4.0.i -> 4.1.a` |
| Final letter | Fixes, balance changes, small patches | `4.1.a -> 4.1.b` |

Reset the lower parts when a higher one changes. After a release, ask the user which version comes next if it has not been chosen.

## The pieces

| Piece | Path | Role |
|---|---|---|
| Changelog | `Changelog` | Source of the version and the release notes ([CHANGELOG.md](CHANGELOG.md)) |
| Mod metadata | `mod_info.json` | `version` is stamped into the zip's copy at packaging; the repository copy is not bumped |
| Version file | `IndEvo.version` | Version Checker file, forum thread `18011`; stamped at packaging, committed back to `master` by the workflow after the release exists |
| Version Checker registration | `data/config/version/version_files.csv` | Points Version Checker at `IndEvo.version` |
| Release builder | `jars/src/indevo/tools/ReleaseBuild.java` | Single-file Java program run with `java <file>`: `compile`, `build`, `package` |
| Builder checks | `jars/src/indevo/tools/ReleaseBuildChecks.java` | Headless checks of changelog parsing, stamping, file filters, source excludes and asset references |
| Settings checks | `jars/src/indevo/tools/SettingsChecks.java` | Checks `LunaSettings.csv` against `indevo.config.Setting`; compiled with the mod, run by the builder after compile ([SETTINGS.md](core/SETTINGS.md#settings-checks)) |
| Release jar | `jars/release/IndEvo.jar` | Committed; runtime classes only, manifest carries `Source-SHA256` |
| IDE jar | `jars/IndEvo.jar` | IntelliJ artifact `IndEvo:jar` for local testing, gitignored, never packaged |
| Compile jars | `lib/` | Game and library jars the build compiles against ([CLAUDE.md](../CLAUDE.md#compile-dependencies)); never shipped |
| Module | `IndEvo.iml` | Its `excludeFolder` entries decide which sources the builder compiles |
| Release workflow | `.github/workflows/release.yml` | Manual only: preview without a version, publish with one |

The package `indevo.tools` is never shipped: the builder drops it from the release jar.

The builder is the house-style `release/ReleaseBuild.java` template with IE deviations: the `compile` command, sources taken from the `IndEvo.iml` excludes, `data/scripts/` next to `indevo/` in the runtime packages, and every `lib/*.jar` (game jars included) on the classpath and in `Source-SHA256`, where the template takes the game install path and unpacks library zips.

## Builder: compile and build

```text
java jars/src/indevo/tools/ReleaseBuild.java compile
java jars/src/indevo/tools/ReleaseBuild.java build
```

Run from the repository root with JDK 17 ([toolchain](../CLAUDE.md#toolchain)).

1. Refuse anything but JDK 17.
2. Create a fresh `out/release-build-*` directory, so removed classes cannot survive from an earlier build.
3. Put every `lib/*.jar` on the classpath. Check that `lib/json.jar` is the game's (checked `JSONException`), not a Maven JSON jar.
4. Collect every `.java` file under `jars/src/` minus the `IndEvo.iml` `excludeFolder` entries, so IntelliJ and the builder compile the same files.
5. Run `javac --release 17 -encoding UTF-8` on them.
6. Run `SettingsChecks.run` from the compiled classes in a `URLClassLoader` over the classes and `lib/` (`ReleaseBuild.checkSettings`); the builder runs as a single source file and cannot link it directly. A failure lists every problem and stops the build.
7. Keep only runtime entries: classes under `indevo/` and `data/scripts/` except `indevo/tools/`.
8. Write a manifest with `Source-SHA256`: a hash of the compiled sources (tools excluded except the builder itself) and every `lib/*.jar`.
9. Verify the candidate jar: only runtime entries, every class Java 17 (major 61), `indevo/utils/ModPlugin.class` present.
10. `build` moves the candidate to `jars/release/IndEvo.jar`; a failed compile or settings check leaves the old jar untouched. `compile` leaves it in `out/` and is the compile gate.

Rebuild and commit the release jar only when the user asks, normally right before a release.

## Builder: package

```text
java jars/src/indevo/tools/ReleaseBuild.java package            # preview
java jars/src/indevo/tools/ReleaseBuild.java package 4.2.a      # release
```

- **Changelog parsing:** the newest heading matching `<version>`, `<version> - UNRELEASED` or `<version> - CURRENT PUBLIC VERSION`; old irregular headings (`2.2.a/b`) are ignored. No duplicate versions; at least one `Added/Adjusted/Fixed/Removed -` line; plain text only.
- **Preview** (no version): packages the newest section even if `UNRELEASED` and accepts a stale release jar with a warning.
- **Release** (with version): the version must match `4.2.a` exactly (no `v`), equal the newest section, and that section must not be `UNRELEASED`. The release jar must be fresh.
- **Stamping:** `mod_info.json` `version`; `IndEvo.version` `major`, `minor`, `patch` and `directDownloadURL`. The working files are not edited.
- **Asset references:** every literal `graphics/`, `sounds/`, `data/` or `IndEvo/sounds/` path (`png jpg jpeg ogg wav shader csv json`) inside packaged data must be in the zip if it names IE (`indevo` in any case) or is a tracked file. Vanilla paths are not in the repository and are skipped.
- **Required files:** `Changelog`, `mod_info.json`, `IndEvo.version`, `data/config/settings.json`, `data/config/LunaSettings.csv`, `data/config/version/version_files.csv`.
- **Deterministic zip:** sorted entries, fixed timestamp, one top-level `Industrial.Evolution/` folder ready for `mods/`.
- **Outputs** in `out/release/`: `Industrial.Evolution-<version>.zip`, `release-notes.md` (the changelog section inside a `text` block, so GitHub keeps line breaks and category headings), the stamped `IndEvo.version`, and `version.txt` for the workflow.

## What ships

Packaging is an allowlist over Git-tracked files only (`git ls-files`). Untracked files never ship; stage or commit new assets before a local preview, commit them before a workflow run.

| Path | Shipped |
|---|---|
| Root files | `mod_info.json`, `IndEvo.version`, `Changelog`, `_IndEvo.png`, `LICENSE*`, `NOTICE`, `CREDITS*` |
| `data/` | `csv json shader ship wpn proj system variant skin skill faction` |
| `graphics/` | `png jpg jpeg csv` (`graphics/clouds/clouds.csv` is a sprite list read through `settings.json`) |
| `IndEvo/sounds/` | `ogg wav` |
| Exact paths (`EXTRAS`) | `jars/source.url`, the pets swap note `graphics/pets/alt/*.txt` |
| `jars/release/IndEvo.jar` | Mapped to `Industrial.Evolution/jars/IndEvo.jar`, the path `mod_info.json` names |

Never shipped: source (`jars/src`), `lib/`, documentation (`docs/`, `Changelog_todo`), agent files (`CLAUDE.md`, `AGENTS.md`, `.claude/`), workflow and Git files, IDE files (`.idea/`, `IndEvo.iml`), the IDE jar, design notes (`.docx`), thumbnail caches (`photothumb.db`), and any path containing a dot directory or an `unused`, `unedited`, `tools` or `docs` folder.

Add a new runtime format to the allowlist deliberately, in the same commit as the first asset that needs it.

## Release workflow

`.github/workflows/release.yml`, started only by hand: Actions tab, Release, Run workflow, or `gh workflow run release.yml --ref master -f version=<version>`. Start it, preview included, only when the user asks. No push, pull request, tag or schedule trigger and no other workflow.

- **Defaults:** `permissions: contents: read`; one concurrency group per ref, no cancelling; actions pinned by commit SHA with the version in a trailing comment; `persist-credentials: false` on the packaging checkout.
- **package job:** Temurin 17; compile the builder and its checks and run the checks; run `package` with the version when one was given (on a tag run, the tag name, which a `version` input must match); upload the zip, `release-notes.md` and `IndEvo.version` as an artifact (14 days). Without a version the run stops here: a preview, nothing published.
- **publish job** (only with a version): download the checked artifact from the same run; fail if the release already exists. From a branch it creates the version tag on the packaged commit (`gh release create --target <sha>`), so nobody pushes a tag by hand, and stops if that tag already exists. On an existing version tag it publishes that tag (`--verify-tag`). Title `Industrial.Evolution <version>`, body `release-notes.md`. Then it checks out `master`, copies the stamped `IndEvo.version` over and commits `update version file to <version>` only if it changed.

The workflow never compiles: it packages the committed release jar. It never needs `lib/` beyond hashing it for the freshness check.

## Version file

`IndEvo.version` on `master` is the online version file (`masterVersionFile`) for Version Checker, LunaLib's update check and mod managers. It always names the newest published release: `modVersion`, `directDownloadURL` (that release's zip), `changelogURL` (raw `master` `Changelog`), `modThreadId` `18011`. Never bump it by hand for unreleased work. The workflow commits it back only after the release exists, so `master` never advertises a version without a download. If branch protection blocks the bot's commit, commit the stamped file from `out/release/` by hand.

## Running a release

On the user's request only.

1. Confirm the version. Ask if the next version has not been chosen.
2. In `Changelog`, change `<version> - UNRELEASED` to `<version> - CURRENT PUBLIC VERSION`, remove that label from the previous version, and add the save compatibility line the user gives.
3. Rebuild the release jar with `build` and commit `jars/release/IndEvo.jar` with the changelog.
4. Run `package <version>` locally as the preview check.
5. Open a PR and merge it. No CI runs on pull requests; the local checks are the gate.
6. Start the Release workflow on `master` with `version` set to exactly the version.
7. Verify the GitHub release (zip and notes) and that `master`'s `IndEvo.version` names it.
8. Start the next `- UNRELEASED` section with the next change.

## Maintenance

Update this guide when the version scheme, the version file, the builder, the package contents or the release steps change. Keep the builder constants (`MOD`, `FOLDER`, `RELEASES`, `PLUGIN`, `PACKAGES`, `ROOT_FILES`, `REQUIRED`, `EXTRAS`, `ASSETS`), `release.yml` and `IndEvo.version` in step with it. Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep).
