# Downloads a pinned Hugging Face checkpoint listed in models\manifest.json,
# converts it to GGUF with the pinned llama.cpp, quantizes it, and writes each
# file's size and SHA-256 back into the manifest. The manifest is the single
# source for repo, revision and quantization list.
# Requires scripts\build-llama.ps1 to have been run (for llama-quantize).

param(
    [string]$Name = "smollm2-135m-instruct"
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $repo "models\manifest.json"
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$model = $manifest.models | Where-Object { $_.name -eq $Name }
if (-not $model) { throw "model '$Name' not in manifest" }

$llama = Join-Path $repo "third_party\llama.cpp"
$quantize = Join-Path $repo "build\llama\bin\llama-quantize.exe"
if (-not (Test-Path $quantize)) { throw "run scripts\build-llama.ps1 first" }

# Python venv with the converter's pinned requirements.
$venv = Join-Path $repo ".venv"
$py = Join-Path $venv "Scripts\python.exe"
if (-not (Test-Path $py)) {
    python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw "venv creation failed" }
}
& $py -m pip install --quiet --upgrade pip
& $py -m pip install --quiet -r (Join-Path $llama "requirements\requirements-convert_hf_to_gguf.txt") huggingface_hub
$pip = $LASTEXITCODE
"pip exit: $pip"
if ($pip -ne 0) { throw "pip install failed" }

# A local checkpoint (our fine-tunes) is converted in place; otherwise
# download the exact revision.
if ($model.source.local) {
    $hfDir = Join-Path $repo $model.source.local
    if (-not (Test-Path (Join-Path $hfDir "config.json"))) { throw "missing checkpoint $hfDir; run training first" }
} else {
    $hfDir = Join-Path $repo "models\hf\$Name"
    $code = "import sys; from huggingface_hub import snapshot_download; snapshot_download(repo_id=sys.argv[1], revision=sys.argv[2], local_dir=sys.argv[3])"
    & $py -c $code $model.source.repo $model.source.revision $hfDir
    if ($LASTEXITCODE -ne 0) { throw "download failed" }
}

# Convert to f16 GGUF, then quantize the rest from it.
$ggufDir = Join-Path $repo "models\gguf"
New-Item -ItemType Directory -Force $ggufDir | Out-Null
$f16 = Join-Path $ggufDir "$Name-f16.gguf"
& $py (Join-Path $llama "convert_hf_to_gguf.py") $hfDir --outtype f16 --outfile $f16
$convert = $LASTEXITCODE
"convert exit: $convert"
if ($convert -ne 0) { throw "conversion failed" }

$files = [ordered]@{}
foreach ($q in $model.quantizations) {
    $path = Join-Path $ggufDir "$Name-$q.gguf"
    if ($q -ne "f16") {
        & $quantize $f16 $path $q.ToUpper()
        $qexit = $LASTEXITCODE
        "quantize $q exit: $qexit"
        if ($qexit -ne 0) { throw "quantize $q failed" }
    }
    $item = Get-Item $path
    $files[$q] = [ordered]@{
        path   = "models/gguf/$($item.Name)"
        bytes  = $item.Length
        sha256 = (Get-FileHash $path -Algorithm SHA256).Hash.ToLower()
    }
    "ok  $($item.Name)  $([math]::Round($item.Length / 1MB, 1)) MB"
}

$model.files = $files
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path $manifestPath -Encoding UTF8
"manifest updated: $manifestPath"
