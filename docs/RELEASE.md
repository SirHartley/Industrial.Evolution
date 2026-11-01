# Release

Version numbers, the build, what a release contains and how it is published. Read it before bumping a version, building a release jar or publishing. Changelog entries are in [CHANGELOG.md](CHANGELOG.md); the compile gate for ordinary changes is in [CLAUDE.md](../CLAUDE.md#building).

Nothing in this guide runs unless the user asks for a release.

## Versions

Scheme `major.minor.letter`, lowercase letter, no `v` prefix. Current public version `4.1.b`; the unreleased section is `4.1.c`.

| Part | Change | Example |
|---|---|---|
| First number | Major release, usually not save compatible | `3.3.e -> 4.0.a` |
| Second number | Gameplay additions players will notice | `4.0.i -> 4.1.a` |
| Final letter | Fixes, balance changes, small patches | `4.1.a -> 4.1.b` |

Reset the lower parts when a higher one changes. After a release, ask the user which version comes next if it has not been chosen.

## The pieces

| Piece | Path | Role |
|---|---|---|
| Changelog | `Changelog` | Release notes; the newest section becomes the release body |
| Mod metadata | `mod_info.json` | `version` (`4.1.b`), `gameVersion`, dependencies |
| Version file | `IndEvo.version` | Version Checker file: `modVersion` (`major`, `minor`, `patch` letter), `directDownloadURL`, `changelogURL`, forum thread `18011` |
| Version Checker registration | `data/config/version/version_files.csv` | Points Version Checker at `IndEvo.version` |
| Jar | `jars/IndEvo.jar` | Built by the IntelliJ artifact `IndEvo:jar` (`.idea/artifacts`), gitignored, not committed |
| GitHub release | tag `<version>`, name `Industrial.Evolution <version>` | Asset `Industrial.Evolution<version>.zip`, the URL `IndEvo.version` links |

## Build

The jar is built in IntelliJ: module `IndEvo` (`IndEvo.iml`), JDK 17 (`corretto-17`), Kotlin 2.1, output to `jars/`, artifact `IndEvo:jar` writes `jars/IndEvo.jar` on make. Libraries are IntelliJ application libraries on the user's machine. The run configuration `Run Starsector` (`.idea/runConfigurations`) starts the game with a debugger on port 5005 from a local install path.

There is no scripted release builder. The compile dependencies are in `lib/`; the Kotlin compiler is still missing ([CLAUDE.md](../CLAUDE.md#building)), so an agent cannot build the jar yet. `lib/` is build input only and must stay out of the release zip. `.idea/`, `IndEvo.iml`, `CLAUDE.md`, `AGENTS.md`, `.claude/` and `docs/` are not needed by the game either. The house-style release setup (a `ReleaseBuild` tool, committed `jars/release/IndEvo.jar`, a manual `release.yml`) is not set up for IE.

## Running a release

On the user's request only:

1. Pick the version with the user.
2. In `Changelog`, change `<version> - UNRELEASED` to `<version> - CURRENT PUBLIC VERSION`, remove that label from the previous version, and add the save compatibility line the user gives.
3. Set the version in `mod_info.json` and in `IndEvo.version` (`modVersion` and the version in `directDownloadURL`).
4. The user builds `jars/IndEvo.jar` in IntelliJ, packages the mod folder as `Industrial.Evolution<version>.zip` and publishes the GitHub release with tag `<version>`.

`IndEvo.version` on `master` is the online master version file (`masterVersionFile`), so merging the version bump announces the update to every Version Checker user. Merge it only together with the published release.

## Maintenance

Update this guide when the version scheme, the version files, the build or the release steps change. Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep).
