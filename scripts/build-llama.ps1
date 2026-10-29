# Builds the pinned llama.cpp submodule (CPU only) with MSVC + Ninja.
# Works in Windows PowerShell 5.1 and PowerShell 7. The llama.cpp version is the
# submodule commit; this script never changes it.
#
# NEMO: knowledge/workflows/verify-the-build-ran-before-trusting-a-test.md
# The build exit code is always printed and checked, so a failed build stops
# here instead of leaving an old binary to be run. Ninja skips relinking when
# nothing changed, so each tool is reported as "rebuilt" or "up to date".

param(
    [string]$BuildDir = "build\llama",
    [string[]]$Targets = @("llama-cli", "llama-completion", "llama-bench", "llama-quantize")
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$src = Join-Path $repo "third_party\llama.cpp"
$out = Join-Path $repo $BuildDir

if (-not (Test-Path (Join-Path $src "CMakeLists.txt"))) {
    throw "llama.cpp submodule missing. Run: git submodule update --init"
}

# Import the MSVC x64 environment into this process (vcvars64 via cmd).
if (-not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    $vs = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (-not $vs) { throw "Visual Studio with C++ tools not found" }
    $vcvars = Join-Path $vs "VC\Auxiliary\Build\vcvars64.bat"
    cmd /c "`"$vcvars`" >nul && set" | ForEach-Object {
        if ($_ -match "^([^=]+)=(.*)$") { Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] }
    }
}

$start = Get-Date

cmake -S $src -B $out -G Ninja -DCMAKE_BUILD_TYPE=Release -DGGML_CUDA=OFF -DLLAMA_CURL=OFF
$configure = $LASTEXITCODE
"configure exit: $configure"
if ($configure -ne 0) { throw "configure failed" }

cmake --build $out --target $Targets
$build = $LASTEXITCODE
"build exit: $build"
if ($build -ne 0) { throw "build failed; existing binaries would be stale" }

foreach ($t in $Targets) {
    $exe = Join-Path $out "bin\$t.exe"
    if (-not (Test-Path $exe)) { throw "missing $exe" }
    $age = if ((Get-Item $exe).LastWriteTime -ge $start) { "rebuilt" } else { "up to date" }
    "ok  $exe ($age)"
}
"llama.cpp " + (git -C $src describe --tags --always)
