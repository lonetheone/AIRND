# Asks the local model for one game decision, for quick manual testing.
#
#   .\scripts\ask.ps1 "The character is starving and food is nearby."
#   .\scripts\ask.ps1 "Exhausted and sleepy." -Quant f16
#   .\scripts\ask.ps1 "Full and bored." -Actions REST,EXPLORE,EAT
#   .\scripts\ask.ps1 "Full and bored." -Free        # no grammar, see raw text
#
# Requires scripts\build-llama.ps1 and scripts\get-model.ps1 to have been run.

param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Situation,
    [string[]]$Actions = @("EAT", "REST", "EXPLORE"),
    [ValidateSet("f16", "q8_0", "q4_k_m")][string]$Quant = "q4_k_m",
    [string]$Model = "smollm2-135m-instruct",
    [switch]$Free,
    [int]$Tokens = 0
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $repo "build\llama\bin\llama-completion.exe"
$gguf = Join-Path $repo "models\gguf\$Model-$Quant.gguf"
if (-not (Test-Path $exe)) { throw "run scripts\build-llama.ps1 first" }
if (-not (Test-Path $gguf)) { throw "missing $gguf; run scripts\get-model.ps1 first" }

# "-Actions REST,EXPLORE,EAT" arrives as one string under "powershell -File".
$Actions = @($Actions -split "," | ForEach-Object { $_.Trim().ToUpper() } | Where-Object { $_ })
if ($Actions.Count -lt 2) { throw "give at least two actions" }
$list = ($Actions[0..($Actions.Count - 2)] -join ", ") + ", or " + $Actions[-1]
$system = "You control a game character. Reply with exactly one word: $list."
$prompt = "<|im_start|>system`n$system<|im_end|>`n<|im_start|>user`n$Situation Which action?<|im_end|>`n<|im_start|>assistant`n"

$llamaArgs = @("-m", $gguf, "-p", $prompt, "-t", "4", "--temp", "0", "-no-cnv", "--no-display-prompt")
if ($Free) {
    $n = if ($Tokens -gt 0) { $Tokens } else { 32 }
} else {
    $n = if ($Tokens -gt 0) { $Tokens } else { 4 }
    # Passed as a file: Windows PowerShell 5.1 mangles embedded quotes in
    # native arguments (NEMO: windows-powershell-agent-gotchas.md).
    $grammar = "root ::= " + (($Actions | ForEach-Object { "`"$_`"" }) -join " | ")
    $grammarFile = [IO.Path]::GetTempFileName()
    [IO.File]::WriteAllText($grammarFile, $grammar, [Text.UTF8Encoding]::new($false))
    $llamaArgs += @("--grammar-file", $grammarFile)
}
$llamaArgs += @("-n", "$n")

# llama.cpp logs to stderr; Windows PowerShell 5.1 would turn that into an
# error under "Stop", so relax it for the native call only.
$ErrorActionPreference = "Continue"
$timer = [Diagnostics.Stopwatch]::StartNew()
$out = & $exe @llamaArgs 2>$null
$code = $LASTEXITCODE
$timer.Stop()
$ErrorActionPreference = "Stop"
if ($grammarFile) { Remove-Item $grammarFile -ErrorAction SilentlyContinue }
if ($code -ne 0) { throw "llama-completion failed (exit $code)" }

$answer = (($out -join " ") -replace "\[end of text\]", "").Trim()
"Model:     $Model ($Quant)"
"Options:   $list"
"Situation: $Situation"
"Answer:    $answer"
"Time:      $($timer.ElapsedMilliseconds) ms (includes model load)"
