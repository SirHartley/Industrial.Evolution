# Isolated native Starsector smoke run on Windows: real loader, textures, fonts, widgets and OpenGL context.
# The mod under test loads as itself (its own mod_info.json, ModPlugin, .version file and dependencies) from a
# staged copy, and the probe runs as the plugin of a companion mod whose name sorts last, so it starts after
# every other mod's onApplicationLoad. Nothing in the game install is changed.
# Usage: powershell -File run-native-smoke.ps1 GAME_ROOT PROJECT_ROOT PROBE_JAVA NEW_OUTPUT_DIR
#   GAME_ROOT     the install folder holding starsector-core, mods and jre, e.g. D:\Starsector
#   PROJECT_ROOT  the mod's root folder, the one holding mod_info.json
# Optional environment:
#   SMOKE_MOD_JAR        prebuilt mod jar, normally from the project's compile gate. It replaces the mod_info "jars"
#                        entry with the same file name, else the first entry.
#   SMOKE_SRC_DIRS       without SMOKE_MOD_JAR: ';'-separated source dirs, absolute or under PROJECT_ROOT,
#                        compiled into the first "jars" entry (default: src)
#   SMOKE_PROBE_SOURCES  ';'-separated extra test-only Java files compiled into the companion mod with the probe
#   SMOKE_SUPPORT_SOURCES ';'-separated test-only Java helpers compiled with NativeSmokeSupport on the game's classpath,
#                        outside the security gate, so they may use reflection and files (CombatSmokeSupport)
#   SMOKE_EXTRA_MODS     mods to link and enable from GAME_ROOT\mods, each a mod_info.json id (highest version wins) or a
#                        folder name; space-separated, or ';'-separated when a name holds spaces. Missing dependencies
#                        of the mod under test are found by id without being listed here
#   SMOKE_CLASSPATH      ';'-separated extra jars for compiling only; never on the runtime classpath
#   SMOKE_PATCHLIB       1 (default) links and enables PatchLib (id patchlib); 0 skips it
#   SMOKE_TIMEOUT        seconds before the isolated JVM is killed (default: 240)
#   SMOKE_HEAP           heap size for -Xms/-Xmx (default: 3g)
#   SMOKE_RESOLUTION     windowed resolution (default: 1024x768)
#   SMOKE_JAVAC_DIR      folder holding javac.exe, jar.exe and jdeps.exe (default: the one smoke.sh setup stored, then
#                        PATH, then GAME_ROOT\jdk-17\bin)
#   SMOKE_JFR            1 (default) records <out>\profile.jfr with Java Flight Recorder, read it with scripts/live/jfr-top.sh; 0 turns it off
#   SMOKE_CURSOR_GUARD   1 (default) keeps the game from moving the real cursor (templates\CursorGuard.java); 0 lets it
# The run needs a slot: bash scripts/smoke.sh probe PROJECT_ROOT PROBE_JAVA takes one, picks the output folder and
# passes the stored game folder. By hand: bash scripts/live/smoke-slot.sh run OWNER -- powershell ...
# The game window really opens on the desktop for the duration of the run.
param(
    [Parameter(Mandatory, Position = 0)][string]$GameRoot,
    [Parameter(Mandatory, Position = 1)][string]$ProjectRoot,
    [Parameter(Mandatory, Position = 2)][string]$ProbeJava,
    [Parameter(Mandatory, Position = 3)][string]$OutDir
)
$ErrorActionPreference = 'Stop'

$CompanionId = 'native_smoke'
$CompanionName = '~ Native Smoke'
$CompanionFolder = 'native-smoke'
# the game's own jars in the vanilla launcher's order (0.98a); a wildcard would also pull in fast rendering's jars
$GameJars = @('janino.jar', 'commons-compiler.jar', 'commons-compiler-jdk.jar', 'starfarer.api.jar', 'starfarer_obf.jar',
    'jogg-0.0.7.jar', 'jorbis-0.0.15.jar', 'json.jar', 'lwjgl.jar', 'jinput.jar', 'log4j-1.2.9.jar', 'lwjgl_util.jar',
    'fs.sound_obf.jar', 'fs.common_obf.jar', 'xstream-1.4.10.jar', 'txw2-3.0.2.jar', 'jaxb-api-2.4.0-b180830.0359.jar',
    'webp-imageio-0.1.6.jar')
# top-level project folders that are never part of a mod; folders starting with '.' are skipped too
$NotModFolders = @('jars', 'out', 'build', 'lib', 'libs', 'docs', 'src', 'gradle', 'native-smoke')

function Fail([string]$message) { [Console]::Error.WriteLine($message); exit 1 }
function Split-List([string]$value, [string]$sep) {
    if ([string]::IsNullOrWhiteSpace($value)) { return @() }
    return @($value -split $sep | Where-Object { $_ })
}
function Env-Or([string]$name, [string]$default) {
    $v = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrEmpty($v)) { return $default } else { return $v }
}
function Fwd([string]$path) { return $path.Replace('\', '/') }

# every run that starts a game holds a slot, so sessions that share the machine see each other (references/slots.md)
if ($env:SMOKE_LOCK -ne '0' -and -not $env:SMOKE_SLOT) {
    $here = Fwd $PSScriptRoot
    Fail ('NO_SLOT: this run starts a game and holds no slot. From Git Bash, run it through: bash "' + $here + '/smoke.sh" probe PROJECT_ROOT PROBE.java ' +
        '(or wrap this command: bash "' + $here + '/live/smoke-slot.sh" run OWNER -- powershell ...). SMOKE_LOCK=0 turns the check off, only on a machine no other session uses.')
}

# mod_info.json is loose JSON with '#' comments, so read the few keys needed with patterns
function Read-ModInfo([string]$folder) {
    $file = Join-Path $folder 'mod_info.json'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { Fail "Missing mod_info.json in $folder" }
    $text = Get-Content -Raw -LiteralPath $file
    $text = [regex]::Replace($text, '(?m)^\s*#.*$', '')
    $text = [regex]::Replace($text, '(?m),\s*#.*$', ',')
    $deps = [regex]::Match($text, '"dependencies"\s*:\s*\[(.*?)\]', 'Singleline')
    $main = if ($deps.Success) { $text.Remove($deps.Index, $deps.Length) } else { $text }
    $jars = [regex]::Match($main, '"jars"\s*:\s*\[(.*?)\]', 'Singleline')
    $version = [regex]::Match($main, '"gameVersion"\s*:\s*"([^"]*)"')
    $modVersion = [regex]::Match($main, '"version"\s*:\s*("[^"]*"|\{[^}]*\})').Groups[1].Value
    $info = [pscustomobject]@{
        Folder       = $folder
        Version      = @([regex]::Matches($modVersion, '\d+') | ForEach-Object { [long]$_.Value })
        Id           = [regex]::Match($main, '"id"\s*:\s*"([^"]*)"').Groups[1].Value
        Name         = [regex]::Match($main, '"name"\s*:\s*"([^"]*)"').Groups[1].Value
        GameVersion  = if ($version.Success) { $version.Groups[1].Value } else { '0.98a-RC8' }
        Jars         = @(if ($jars.Success) { [regex]::Matches($jars.Groups[1].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value } })
        Dependencies = @(if ($deps.Success) { [regex]::Matches($deps.Groups[1].Value, '"id"\s*:\s*"([^"]*)"') | ForEach-Object { $_.Groups[1].Value } })
    }
    if (-not $info.Id) { Fail "No id in $file" }
    if (-not $info.Name) { Fail "No name in $file" }
    return $info
}

# Folder names carry versions ("LazyLib-3.0.0", "! PatchLib !-1.0.3"), so a mod is found by its mod_info.json id,
# highest version first. A folder name that exists is used as given.
$script:installed = $null
function Find-Mod([string]$name) {
    $direct = Join-Path $Game "mods\$name"
    if (Test-Path -LiteralPath (Join-Path $direct 'mod_info.json')) { return Read-ModInfo $direct }
    if ($null -eq $script:installed) {
        $script:installed = @(Get-ChildItem -LiteralPath (Join-Path $Game 'mods') -Directory | ForEach-Object {
            $file = Join-Path $_.FullName 'mod_info.json'
            if (Test-Path -LiteralPath $file) {
                $id = [regex]::Match((Get-Content -Raw -LiteralPath $file), '"id"\s*:\s*"([^"]*)"').Groups[1].Value
                [pscustomobject]@{ Id = $id; Folder = $_.FullName }
            }
        })
    }
    $best = $null
    foreach ($candidate in @($script:installed | Where-Object { $_.Id -ieq $name })) {
        $info = Read-ModInfo $candidate.Folder
        if ($null -eq $best -or (Compare-Version $info.Version $best.Version) -gt 0) { $best = $info }
    }
    if ($null -eq $best) { Fail "No mod with id or folder '$name' in $Game\mods" }
    return $best
}
function Compare-Version([long[]]$a, [long[]]$b) {
    for ($i = 0; $i -lt [Math]::Max($a.Count, $b.Count); $i++) {
        $x = if ($i -lt $a.Count) { $a[$i] } else { 0 }
        $y = if ($i -lt $b.Count) { $b[$i] } else { 0 }
        if ($x -ne $y) { return [Math]::Sign($x - $y) }
    }
    return 0
}

# same rule as the game's script classloader gate (com.fs.starfarer.loading.scripts, 0.98a-RC8)
$GateAllowed = @('java.io.BufferedInputStream', 'java.io.BufferedReader', 'java.io.FilterInputStream',
    'java.io.InputStreamReader', 'java.io.Reader', 'java.io.Serializable', 'java.io.InvalidClassException',
    'java.io.ObjectStreamException', 'java.io.InputStream', 'java.io.IOException', 'java.io.PrintStream',
    'java.io.PrintWriter', 'java.io.ByteArrayInputStream', 'java.io.FilterOutputStream', 'java.io.OutputStream',
    'java.io.Closeable', 'java.io.Flushable', 'java.io.StringReader', 'java.io.FileReader',
    'java.lang.reflect.AnnotatedElement', 'java.lang.reflect.InvocationTargetException', 'java.lang.reflect.Type',
    'java.lang.reflect.GenericDeclaration')
function Is-GateBlocked([string]$name) {
    if ($GateAllowed -ccontains $name) { return $false }
    foreach ($prefix in 'java.io', 'java.nio.file.File', 'javax.script', 'java.util.prefs', 'java.lang.reflect') {
        if ($name.StartsWith($prefix, [StringComparison]::Ordinal)) { return $true }
    }
    return $name -ceq 'sun.reflect.misc.MethodUtil'
}

$Skill = Split-Path -Parent $PSScriptRoot
$Game = (Resolve-Path -LiteralPath $GameRoot).Path.TrimEnd('\')
$Project = (Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd('\')
$Probe = (Resolve-Path -LiteralPath $ProbeJava).Path
$Out = [IO.Path]::GetFullPath($OutDir).TrimEnd('\')
$Support = Join-Path $Skill 'templates\NativeSmokeSupport.java'
$PrefsHelper = Join-Path $Skill 'templates\SmokePrefs.java'
$Core = if (Test-Path -LiteralPath (Join-Path $Game 'starsector-core\starfarer.api.jar')) { Join-Path $Game 'starsector-core' } else { $Game }
$UsePatchLib = (Env-Or 'SMOKE_PATCHLIB' '1') -eq '1'
$Plugin = [IO.Path]::GetFileNameWithoutExtension($Probe)
$ModJar = Env-Or 'SMOKE_MOD_JAR' ''
$Timeout = [int](Env-Or 'SMOKE_TIMEOUT' '240')
$Heap = Env-Or 'SMOKE_HEAP' '3g'
$Resolution = Env-Or 'SMOKE_RESOLUTION' '1024x768'
$ExtraCp = Split-List (Env-Or 'SMOKE_CLASSPATH' '') ';'

# tools: the game's own Java 17 runtime to run, a JDK 17+ to compile
$Java = Join-Path $Game 'jre\bin\java.exe'
$JavacDir = Env-Or 'SMOKE_JAVAC_DIR' ''
if (-not $JavacDir) {
    # the JDK that smoke.sh setup stored for this machine
    $machine = if ($env:SMOKE_LOCK_ROOT) { $env:SMOKE_LOCK_ROOT } else { Join-Path $env:LOCALAPPDATA 'starsector-smoke' }
    $machineConfig = Join-Path $machine 'config'
    if (Test-Path -LiteralPath $machineConfig -PathType Leaf) {
        $stored = Get-Content -LiteralPath $machineConfig | Where-Object { $_ -like 'javac_dir=*' } | Select-Object -Last 1
        if ($stored) { $JavacDir = $stored.Substring(10) }
    }
}
if (-not $JavacDir) {
    $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
    $JavacDir = if ($onPath) { Split-Path -Parent $onPath.Source } else { Join-Path $Game 'jdk-17\bin' }
}
$Javac = Join-Path $JavacDir 'javac.exe'
$Jar = Join-Path $JavacDir 'jar.exe'
$Jdeps = Join-Path $JavacDir 'jdeps.exe'

$GameCp = @($GameJars | ForEach-Object { Join-Path $Core $_ })
$required = @($Java, $Javac, $Jar, (Join-Path $Core 'native\windows\lwjgl64.dll'), $Probe, $Support, $PrefsHelper) + $GameCp
if ($ModJar) { $required += $ModJar }
foreach ($file in $required) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { Fail "Missing prerequisite: $file" }
}
foreach ($name in 'data', 'graphics', 'sounds') {
    if (-not (Test-Path -LiteralPath (Join-Path $Core $name) -PathType Container)) { Fail "Missing directory: $(Join-Path $Core $name)" }
}
foreach ($file in $ExtraCp) { if (-not (Test-Path -LiteralPath $file)) { Fail "Missing SMOKE_CLASSPATH entry: $file" } }
$probeSources = Split-List (Env-Or 'SMOKE_PROBE_SOURCES' '') ';'
foreach ($file in $probeSources) { if (-not (Test-Path -LiteralPath $file)) { Fail "Missing probe source: $file" } }
$supportSources = Split-List (Env-Or 'SMOKE_SUPPORT_SOURCES' '') ';'
foreach ($file in $supportSources) { if (-not (Test-Path -LiteralPath $file)) { Fail "Missing support source: $file" } }

# the user may play on another Java through a custom launcher; say which one this run uses
Write-Host ("Java: " + ((& cmd.exe /c "`"$Java`" -version 2>&1") | Select-Object -First 1) + " ($Java)")
$otherJava = @(Get-ChildItem -Path "$Game\*" -File -Include *.bat, *.cmd, vmparams -ErrorAction SilentlyContinue |
    Select-String -Pattern '[^\s"@=]*bin[\\/]javaw?(\.exe)?' -AllMatches | ForEach-Object { $_.Matches.Value } |
    Where-Object { $_ -notmatch '(^|[\\/])jre[\\/]bin' } | Sort-Object -Unique)
if ($otherJava.Count) { Write-Warning "Launch files in $Game use another Java: $($otherJava -join ', '). Ask which one the user plays on." }
if (Test-Path -LiteralPath $Out) { Fail "Output directory already exists; choose a fresh path: $Out" }

# the mod under test, its linked dependencies and the companion
$Mod = Read-ModInfo $Project
if ($Mod.Id -eq $CompanionId) { Fail "The mod under test must not use the companion id $CompanionId" }
$ModFolder = Split-Path -Leaf $Project
$wanted = @()
if ($UsePatchLib) { $wanted += 'patchlib' }
$extraRaw = Env-Or 'SMOKE_EXTRA_MODS' ''
$extraSep = if ($extraRaw.Contains(';')) { ';' } else { ' ' }
$wanted += Split-List $extraRaw $extraSep
$linked = @()
function Add-Linked($info) {
    if ($info.Id -eq $Mod.Id -or @($script:linked | Where-Object { $_.Id -eq $info.Id }).Count) { return }
    $leaf = Split-Path -Leaf $info.Folder
    if ($leaf -eq $ModFolder -or $leaf -eq $CompanionFolder) { Fail "Extra mod folder name clashes with the staged mods: $leaf" }
    $script:linked += $info
}
foreach ($name in $wanted) { Add-Linked (Find-Mod $name) }
# dependencies of the mod under test and of the linked mods, found by id
do {
    $enabledIds = @($linked | ForEach-Object { $_.Id }) + $Mod.Id
    $missing = @(@($Mod) + $linked | ForEach-Object { $_.Dependencies } | Where-Object { $enabledIds -notcontains $_ } | Sort-Object -Unique)
    foreach ($id in $missing) {
        if ($id -ieq 'patchlib' -and -not $UsePatchLib) { Fail "$($Mod.Name) depends on PatchLib; leave SMOKE_PATCHLIB at 1" }
        $found = Find-Mod $id
        Write-Host "Dependency $id -> $($found.Folder)"
        Add-Linked $found
    }
} while ($missing.Count)
foreach ($info in @($linked) + $Mod) {
    if ([string]::CompareOrdinal($info.Name, $CompanionName) -ge 0) {
        Fail "Mod name '$($info.Name)' sorts after '$CompanionName', so its onApplicationLoad would run after the probe."
    }
}

# where the mod jar goes: the entry SMOKE_MOD_JAR names, else the first one
$mainJar = $null
if ($Mod.Jars.Count) {
    $mainJar = $Mod.Jars[0]
    if ($ModJar) {
        $byName = @($Mod.Jars | Where-Object { (Split-Path -Leaf $_) -ieq (Split-Path -Leaf $ModJar) })
        if ($byName.Count) { $mainJar = $byName[0] }
    }
} elseif ($ModJar) {
    Fail "SMOKE_MOD_JAR is set, but $Project\mod_info.json lists no jars"
}
$srcPaths = @()
if ($mainJar -and -not $ModJar) {
    foreach ($dir in (Split-List (Env-Or 'SMOKE_SRC_DIRS' 'src') ';')) {
        $full = if ([IO.Path]::IsPathRooted($dir)) { $dir } else { Join-Path $Project $dir }
        if (-not (Test-Path -LiteralPath $full -PathType Container)) {
            Fail "Missing source directory: $full. Set SMOKE_MOD_JAR to the jar of the project's compile gate, or SMOKE_SRC_DIRS."
        }
        $srcPaths += $full
    }
}

$Stage = Join-Path $Out "mods\$ModFolder"
$Companion = Join-Path $Out "mods\$CompanionFolder"
foreach ($sub in 'classes\project', 'classes\probe', 'classes\support', 'classes\empty', 'logs', 'saves', 'screenshots',
    'home', 'tmp', 'prefs') {
    New-Item -ItemType Directory -Force -Path (Join-Path $Out $sub) | Out-Null
}
New-Item -ItemType Directory -Force -Path $Stage, (Join-Path $Companion 'jars') | Out-Null

# directory junctions need no admin rights; they are removed (never their targets) when finished
$junctions = New-Object System.Collections.Generic.List[string]
function Link([string]$link, [string]$target) {
    New-Item -ItemType Junction -Path $link -Target $target | Out-Null
    $junctions.Add($link)
}
function Javac-Run([string]$what, [string]$classpath, [string]$dest, [string[]]$files, [switch]$quiet) {
    $list = Join-Path $Out "classes\$what.args"
    ($files | ForEach-Object { '"' + (Fwd $_) + '"' }) | Set-Content -Encoding ASCII -LiteralPath $list
    # mod jars such as magiclib bundle .java files; an empty sourcepath and -implicit:none keep javac on their classes
    $javacArgs = @('--release', '17', '-proc:none', '-encoding', 'UTF-8', '-sourcepath', (Join-Path $Out 'classes\empty'), '-implicit:none')
    if ($quiet) { $javacArgs += '-nowarn' }
    $javacArgs += @('-cp', $classpath, '-d', $dest, "@$list")
    & $Javac @javacArgs
    if ($LASTEXITCODE) { Fail "Compile failed: $what" }
}
function Jar-Run([string]$file, [string]$classes) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $file) | Out-Null
    & $Jar --create --file $file -C $classes .
    if ($LASTEXITCODE) { Fail "jar failed: $file" }
}

$exit = 1
try {
    # support classes live on the game's classpath, outside the security gate
    Javac-Run 'support' ($GameCp -join ';') (Join-Path $Out 'classes\support') (@($Support, $PrefsHelper) + $supportSources)

    # copy the licence/settings from the registry into a throwaway XML file (deleted below)
    $prefsXml = Join-Path $Out 'prefs\prefs.xml'
    & $Java -cp (Join-Path $Out 'classes\support') SmokePrefs export $prefsXml
    if ($LASTEXITCODE -or -not (Test-Path -LiteralPath $prefsXml)) {
        Fail 'Could not export com/fs/starfarer preferences from the registry. Start the real game once so it exists.'
    }

    # test-only agent: the game moves the real cursor through LWJGL even when its window is not in front
    $guardJar = $null
    if ((Env-Or 'SMOKE_CURSOR_GUARD' '1') -ne '0') {
        $guardClasses = Join-Path $Out 'classes\cursor-guard'
        New-Item -ItemType Directory -Force -Path $guardClasses | Out-Null
        & $Javac -nowarn '-Xlint:-options' -source 17 -target 17 --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' `
            -d $guardClasses (Join-Path $Skill 'templates\CursorGuard.java')
        if ($LASTEXITCODE) { Fail 'Compile failed: cursor guard' }
        $guardManifest = Join-Path $Out 'classes\cursor-guard.mf'
        'Premain-Class: CursorGuard' | Set-Content -Encoding ASCII -LiteralPath $guardManifest
        $guardJar = Join-Path $Out 'classes\cursor-guard.jar'
        & $Jar --create --file $guardJar --manifest $guardManifest -C $guardClasses .
        if ($LASTEXITCODE) { Fail 'jar failed: cursor guard' }
    }

    # dependencies are linked as they are installed
    $compileCp = @($GameCp)
    foreach ($info in $linked) {
        Link (Join-Path $Out "mods\$(Split-Path -Leaf $info.Folder)") $info.Folder
        $compileCp += @($info.Jars | ForEach-Object { Join-Path $info.Folder $_ } | Where-Object { Test-Path -LiteralPath $_ })
    }
    $compileCp += $ExtraCp

    # stage the mod under test: folders linked, top-level files copied, jars built fresh
    # folders holding jar entries are staged per jar, so a built jar is never written through a junction
    $jarFolders = @($Mod.Jars | Where-Object { $_ -match '[\\/]' } | ForEach-Object { ($_ -split '[\\/]')[0] })
    foreach ($item in Get-ChildItem -LiteralPath $Project -Force) {
        if ($item.Name.StartsWith('.')) { continue }
        if ($item.PSIsContainer) {
            if ($NotModFolders -contains $item.Name -or $jarFolders -contains $item.Name) { continue }
            if (($Out + '\').StartsWith($item.FullName + '\', [StringComparison]::OrdinalIgnoreCase)) { continue }
            Link (Join-Path $Stage $item.Name) $item.FullName
        } else {
            Copy-Item -LiteralPath $item.FullName -Destination $Stage
        }
    }
    $stagedJars = @()
    foreach ($entry in $Mod.Jars) {
        $target = Join-Path $Stage $entry
        $stagedJars += $target
        if ($entry -eq $mainJar) {
            if ($ModJar) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
                Copy-Item -LiteralPath $ModJar -Destination $target
            } else {
                $sources = @($srcPaths | ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -Filter *.java -File } | ForEach-Object { $_.FullName })
                if ($sources.Count -eq 0) { Fail "No Java sources found in $($srcPaths -join ';')" }
                Javac-Run 'project' ($compileCp -join ';') (Join-Path $Out 'classes\project') $sources -quiet
                Jar-Run $target (Join-Path $Out 'classes\project')
            }
        } elseif (-not (Test-Path -LiteralPath $target)) {
            $source = Join-Path $Project $entry
            if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { Fail "mod_info.json lists $entry, which is missing in $Project" }
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
            Copy-Item -LiteralPath $source -Destination $target
        }
    }

    # the probe and its helpers become the companion mod's jar: mod code, behind the gate like the mod
    $probeCp = @((Join-Path $Out 'classes\support')) + $stagedJars + $compileCp
    Javac-Run 'probe' ($probeCp -join ';') (Join-Path $Out 'classes\probe') (@($probeSources) + @($Probe))
    $probeJar = Join-Path $Companion 'jars\Probe.jar'
    Jar-Run $probeJar (Join-Path $Out 'classes\probe')
    if (Test-Path -LiteralPath $Jdeps -PathType Leaf) {
        # windows powershell turns redirected native stderr into terminating errors under 'Stop'
        $ErrorActionPreference = 'Continue'
        $refs = & $Jdeps -verbose:class $probeJar 2>$null | ForEach-Object { if ($_ -match '->\s+(\S+)') { $Matches[1] } }
        $ErrorActionPreference = 'Stop'
        $blocked = @($refs | Sort-Object -Unique | Where-Object { Is-GateBlocked $_ })
        if ($blocked.Count) {
            Fail ("Probe code names classes the game's security gate blocks: $($blocked -join ', '). " +
                'Reach files and reflection through NativeSmokeSupport instead.')
        }
    } else {
        Write-Warning "jdeps.exe not found in $JavacDir; skipping the security gate preflight"
    }

    ('{"enabledMods":[' + ((@($enabledIds) + $CompanionId | ForEach-Object { '"' + $_ + '"' }) -join ',') + ']}') |
        Set-Content -Encoding ASCII -LiteralPath (Join-Path $Out 'mods\enabled_mods.json')
    ('{"id":"' + $CompanionId + '","name":"' + $CompanionName + '","author":"Local verification","version":"1",' +
        '"description":"Isolated native smoke probe.","gameVersion":"' + $Mod.GameVersion + '","utility":true,' +
        '"jars":["jars/Probe.jar"],"modPlugin":"' + $Plugin + '"}') |
        Set-Content -Encoding ASCII -LiteralPath (Join-Path $Companion 'mod_info.json')

    $q = { param($p) '"' + $p + '"' }
    $jvmArgs = @("-Xms$Heap", "-Xmx$Heap", '-Xss4m', '-noverify',
        $(if ($guardJar) { '-javaagent:' + (& $q $guardJar) }),
        $(if ($guardJar) { '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' }),
        '--add-opens=java.base/sun.nio.ch=ALL-UNNAMED', '--add-opens=java.base/java.nio=ALL-UNNAMED',
        '--add-opens=java.base/java.util=ALL-UNNAMED', '--add-opens=java.base/java.util.concurrent=ALL-UNNAMED',
        '--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED', '--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED',
        '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED', '--add-opens=java.base/java.lang.ref=ALL-UNNAMED',
        '--add-opens=java.base/java.text=ALL-UNNAMED', '--add-opens=java.desktop/java.awt.font=ALL-UNNAMED',
        '--add-opens=java.desktop/java.awt=ALL-UNNAMED', '--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED',
        '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED', '--add-exports=java.base/sun.nio.ch=ALL-UNNAMED',
        '-Dfile.encoding=UTF-8', '-Djava.util.Arrays.useLegacyMergeSort=true',
        ('-Djava.library.path=' + (& $q (Join-Path $Core 'native\windows'))),
        ('-Djava.io.tmpdir=' + (& $q (Join-Path $Out 'tmp'))),
        ('-Duser.home=' + (& $q (Join-Path $Out 'home'))),
        '-Djava.util.prefs.PreferencesFactory=SmokePrefs',
        ('-Dsmoke.prefs.xml=' + (& $q $prefsXml)),
        ('-Dcom.fs.starfarer.settings.paths.saves=' + (& $q (Join-Path $Out 'saves'))),
        ('-Dcom.fs.starfarer.settings.paths.screenshots=' + (& $q (Join-Path $Out 'screenshots'))),
        ('-Dcom.fs.starfarer.settings.paths.mods=' + (& $q (Join-Path $Out 'mods'))),
        ('-Dcom.fs.starfarer.settings.paths.logs=' + (& $q (Join-Path $Out 'logs'))),
        '-DlaunchDirect=true', "-DstartRes=$Resolution", '-DstartFS=false', '-DstartSound=false',
        ('-Dnative.smoke.output=' + (& $q $Out)),
        $(if ((Env-Or 'SMOKE_JFR' '1') -ne '0') { '-XX:StartFlightRecording=filename=' + (& $q (Join-Path $Out 'profile.jfr')) + ',settings=profile,dumponexit=true' }),
        '-cp', (& $q ((@((Join-Path $Out 'classes\support')) + $GameCp) -join ';')),
        'com.fs.starfarer.StarfarerLauncher')

    Write-Host "Running isolated native smoke. Output: $Out"
    $stdout = Join-Path $Out 'launcher.out.log'
    $stderr = Join-Path $Out 'launcher.err.log'
    $proc = Start-Process -FilePath $Java -ArgumentList $jvmArgs -WorkingDirectory $Core `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Normal
    $null = $proc.Handle   # keeps the handle open so ExitCode is readable after exit
    # a crash shows a modal "Fatal" box that keeps the process alive until someone clicks OK, so watch the log for it
    $gameLog = Join-Path $Out 'logs\starsector.log'
    $deadline = (Get-Date).AddSeconds($Timeout)
    $crashed = $false
    while (-not $proc.WaitForExit(1000)) {
        if ((Get-Date) -gt $deadline) { break }
        if ((Test-Path -LiteralPath $gameLog) -and (Select-String -LiteralPath $gameLog -SimpleMatch 'ERROR com.fs.starfarer.combat.CombatMain' -Quiet)) {
            $crashed = $true; Start-Sleep 2; break
        }
    }
    if (-not $proc.HasExited) {
        # only the process this run started
        & taskkill.exe /PID $proc.Id /T /F | Out-Null
        $proc.WaitForExit()
        $exit = if ($crashed) { 125 } else { 124 }
    } else {
        $exit = $proc.ExitCode
    }
    $log = Join-Path $Out 'launcher.log'
    Get-Content -LiteralPath $stdout, $stderr -ErrorAction SilentlyContinue | Set-Content -Encoding UTF8 -LiteralPath $log
    Remove-Item -LiteralPath $stdout, $stderr -ErrorAction SilentlyContinue

    if ($exit -eq 124) { [Console]::Error.WriteLine("Native smoke timed out after $Timeout s. Read $log") }
    elseif ($exit -eq 125) { [Console]::Error.WriteLine("The game crashed (CombatMain ERROR, Fatal box). Read the ERROR lines in $gameLog") }
    elseif ($exit -ne 0) { [Console]::Error.WriteLine("Native smoke failed with exit $exit. Read $log") }
    elseif (-not (Select-String -LiteralPath $log -SimpleMatch -Pattern 'NATIVE_SMOKE COMPLETE' -Quiet)) {
        [Console]::Error.WriteLine("No completion marker. Read $log"); $exit = 1
    }
    elseif (-not (Get-ChildItem -LiteralPath $Out -Filter *.png -File -ErrorAction SilentlyContinue)) {
        [Console]::Error.WriteLine("Probe produced no framebuffer PNGs in $Out"); $exit = 1
    }
    else { Write-Host "PASS: native smoke completed. Inspect PNGs and NATIVE_SMOKE results in $Out" }
    if ($exit -ne 0) {
        Select-String -LiteralPath $log -Pattern 'NATIVE_SMOKE FAIL', 'not allowed to scripts' -ErrorAction SilentlyContinue |
            Select-Object -First 20 | ForEach-Object { [Console]::Error.WriteLine($_.Line) }
    }
}
finally {
    # copied preferences go; junctions are removed without touching their targets
    Remove-Item -LiteralPath (Join-Path $Out 'prefs') -Recurse -Force -ErrorAction SilentlyContinue
    foreach ($link in $junctions) { try { [IO.Directory]::Delete($link) } catch { } }
}
exit $exit
