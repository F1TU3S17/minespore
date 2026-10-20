param(
    [string]$SporeExecutable = 'D:/games/SPORE/SporebinEP1/SporeApp.exe',
    [string]$GhidraDirectory = 'D:/Tools/ghidra_12.1.4_PUBLIC',
    [string]$JavaExecutable = 'C:/Program Files/Java/jdk-21/bin/java.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path.Replace('\','/')
$analysisRoot = "$projectRoot/build/research-ghidra"
$settingsRoot = "$projectRoot/.analysis-home"
$evidenceRoot = "$projectRoot/docs/research/evidence"
New-Item -ItemType Directory -Force $analysisRoot,$settingsRoot,$evidenceRoot | Out-Null
Write-Output (Get-FileHash -LiteralPath $SporeExecutable -Algorithm SHA256)
# Uses the same Ghidra entry point as analyzeHeadless.bat. Explicit workspace settings
# avoid launch.bat writing java_home.save into the system user's roaming profile.
& $JavaExecutable '-Xmx2G' '-Djava.system.class.loader=ghidra.GhidraClassLoader' '-Dfile.encoding=UTF8' `
    "-Dapplication.settingsdir=$settingsRoot/config" "-Dapplication.cachedir=$settingsRoot/cache" `
    "-Dapplication.tempdir=$settingsRoot/tmp" `
    -cp "$GhidraDirectory/Ghidra/Framework/Utility/lib/Utility.jar" `
    ghidra.Ghidra ghidra.app.util.headless.AnalyzeHeadless $analysisRoot SporeCellProbe `
    -import $SporeExecutable -scriptPath "$projectRoot/tools/ghidra" `
    -postScript CellStageProbe.java "$evidenceRoot/ghidra-cell-probe.txt" `
    -analysisTimeoutPerFile 1200 -deleteProject -log "$analysisRoot/analysis.log" `
    -scriptlog "$analysisRoot/script.log"
if ($LASTEXITCODE -ne 0) { throw "Ghidra failed: exit $LASTEXITCODE. Inspect $analysisRoot/analysis.log" }
if (Select-String -LiteralPath "$analysisRoot/analysis.log" -Pattern 'Analysis timed out' -Quiet) {
    Write-Warning 'Auto-analysis reached its time limit. Treat recovered functions/xrefs as partial even if the post-script succeeded.'
}
