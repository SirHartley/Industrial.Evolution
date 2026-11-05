# native smoke run for IE: compile gate jar, dependencies found by mod_info id, output under out\native-smoke
param(
    [string]$Probe = 'IndEvoNativeSmoke.java',
    [string]$Game = $env:SMOKE_GAME,
    [string]$Skill = $env:SMOKE_SKILL,
    [string]$Out = '',
    [string]$ExtraMods = ''
)
$ErrorActionPreference = 'Stop'

function Fail([string]$message) { [Console]::Error.WriteLine($message); exit 1 }

# loose json with '#' comments; the dependencies block is cut first so its ids never match
function Mod-Id([string]$folder) {
    $file = Join-Path $folder 'mod_info.json'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $null }
    $text = [regex]::Replace((Get-Content -Raw -LiteralPath $file), '(?m)^\s*#.*$', '')
    $text = [regex]::Replace($text, '"dependencies"\s*:\s*\[.*?\]', '', 'Singleline')
    $m = [regex]::Match($text, '"id"\s*:\s*"([^"]*)"')
    if ($m.Success) { return $m.Groups[1].Value } else { return $null }
}

if (-not $Game) { $Game = 'E:\Games\Starsector 0.98\Starsector 098a-RC8 - Modding' }
# a trailing backslash would escape the closing quote when passed on to the runner
$Game = $Game.TrimEnd('\')
if (-not (Test-Path -LiteralPath (Join-Path $Game 'mods') -PathType Container)) { Fail "No mods folder in $Game" }
if (-not $Skill) { Fail 'Set -Skill or SMOKE_SKILL to the starsector-native-smoke skill folder.' }
$Runner = Join-Path $Skill.TrimEnd('\') 'scripts\run-native-smoke.ps1'
if (-not (Test-Path -LiteralPath $Runner -PathType Leaf)) { Fail "Missing runner: $Runner" }
$Repo = Split-Path -Parent $PSScriptRoot
$ProbePath = if (Test-Path -LiteralPath $Probe -PathType Leaf) { (Resolve-Path -LiteralPath $Probe).Path } else { Join-Path $PSScriptRoot $Probe }
if (-not (Test-Path -LiteralPath $ProbePath -PathType Leaf)) { Fail "Missing probe: $ProbePath" }
if (-not $Out) { $Out = Join-Path $Repo ('out\native-smoke\' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }

if ($ExtraMods) {
    $folders = @($ExtraMods -split ';' | Where-Object { $_ })
} else {
    $info = Get-Content -Raw -LiteralPath (Join-Path $Repo 'mod_info.json')
    $deps = [regex]::Match($info, '"dependencies"\s*:\s*\[(.*?)\]', 'Singleline').Groups[1].Value
    $ids = @([regex]::Matches($deps, '"id"\s*:\s*"([^"]*)"') | ForEach-Object { $_.Groups[1].Value })
    # mod folders may be symbolic links; Get-ChildItem lists them like folders
    $installed = @(Get-ChildItem -LiteralPath (Join-Path $Game 'mods') -Directory |
        ForEach-Object { [pscustomobject]@{ Name = $_.Name; Id = (Mod-Id $_.FullName) } })
    $folders = @()
    foreach ($id in $ids) {
        $found = @($installed | Where-Object { $_.Id -ceq $id })
        if ($found.Count -eq 0) { Fail "Dependency $id is not installed in $Game\mods" }
        if ($found.Count -gt 1) {
            Fail "Several folders have id $($id): $(($found | ForEach-Object { $_.Name }) -join ', '). Pick one with -ExtraMods."
        }
        $folders += $found[0].Name
    }
}

$JavaCmd = if ($env:SMOKE_JAVAC_DIR) { Join-Path $env:SMOKE_JAVAC_DIR 'java.exe' } else { 'java' }
Push-Location -LiteralPath $Repo
try {
    $build = @(& $JavaCmd jars/src/indevo/tools/ReleaseBuild.java compile)
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
$build | ForEach-Object { Write-Host $_ }
if ($code) { Fail 'ReleaseBuild compile failed' }
$jar = $build | ForEach-Object { if ($_ -match '^Checked jar: (.+) \(release jar left as is\)$') { $Matches[1] } } | Select-Object -Last 1
if (-not $jar) { Fail 'ReleaseBuild compile reported no checked jar' }

# the trailing ';' keeps the runner on ';' separation when only one folder is listed
$env:SMOKE_MOD_JAR = $jar
$env:SMOKE_EXTRA_MODS = ($folders -join ';') + ';'
$env:SMOKE_PATCHLIB = '0'
if (-not $env:SMOKE_RESOLUTION) { $env:SMOKE_RESOLUTION = '1280x800' }
Write-Host "Mods: $($folders -join ', ')"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Runner $Game $Repo $ProbePath $Out
exit $LASTEXITCODE
