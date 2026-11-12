# Settings of this mod project for the starsector-native-smoke skill (smoke.sh checks, run-live-checks.sh --project).
# The runner sources this file in bash: plain assignments and the two functions, nothing else.

# slot owner, shown in smoke.sh status: letters, digits, '.', '_' and '-'
OWNER=indevo

# ids (mod_info.json "id") of mods to enable besides this one. Dependencies named in mod_info.json files are added
# by the runner, so list only extra mods the checks want, such as lw_console
DEPS=(lw_lazylib MagicLib lunalib shaderLib patchlib lw_console)

# the mod's own Java package, so the profile report covers and warns about its code
PROFILE_PKG=indevo

# the check mod: a copy of the skill's templates/live-check-mod with this project's groups
CHECK_DIR="$PROJECT/native-smoke/live"

# How the mod under test gets built and staged. Without stage_mod the runner links the project's folders and copies
# its built jars (it stops with STALE_JAR when a jar is older than the sources).
# With a compile gate, build with it and stage its jar, so the game runs what the gate checked. The function runs in
# the runner's shell: PROJECT is this project, STAGE the folder to fill, LIVE_SCRIPTS the skill's scripts/live.
# stage_mod() {
#     (cd "$PROJECT" && java jars/src/mymod/tools/ReleaseBuild.java compile) || return 1
#     bash "$LIVE_SCRIPTS/stage-mod.sh" "$PROJECT" "$STAGE" "$PROJECT/out/compile/MyMod.jar"
# }

stage_mod() {
    local out jar
    out=$(cd "$PROJECT" && java jars/src/indevo/tools/ReleaseBuild.java compile 2>&1) || { printf '%s\n' "$out"; return 1; }
    printf '%s\n' "$out"
    jar=$(printf '%s\n' "$out" | sed -n 's/^Checked jar: \(.*\) (release jar left as is)\r\?$/\1/p' | tail -n 1)
    [[ -n $jar ]] || return 1
    jar=$(cygpath -u "$jar")
    bash "$LIVE_SCRIPTS/stage-mod.sh" "$PROJECT" "$STAGE" "$jar" || return 1
    rm -rf -- "$(dirname -- "$jar")"
}

# the iecheck mod reads ie.check, and only the run itself may start its steps
java_args() {
    [[ ${LAUNCH:-} == checks ]] && printf '%s\n' "-Die.check=$GROUP"
    [[ -n ${SWITCHES:-} ]] && printf '%s\n' "-Die.switches=$SWITCHES"
    return 0
}
