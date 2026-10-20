param(
    [ValidateSet('Import','Index','Decompile','Xrefs','Repair','Data','Define','Signatures')][string]$Mode='Index',
    [string]$RequestFile,
    [string]$OutputName='index',
    [string]$SporeExecutable='D:/games/SPORE/SporebinEP1/SporeApp.exe',
    [string]$GhidraDirectory='D:/Tools/ghidra_12.1.4_PUBLIC',
    [string]$JavaExecutable='C:/Program Files/Java/jdk-21/bin/java.exe'
)
$ErrorActionPreference='Stop'
$researchProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path.Replace('\','/')
$researchDatabase="$researchProjectRoot/research/ghidra"
$researchEvidence="$researchProjectRoot/docs/research/evidence/$OutputName"
$researchSettings="$researchProjectRoot/.analysis-home"
New-Item -ItemType Directory -Force $researchDatabase,$researchEvidence | Out-Null
$researchArgs=@('-Xmx3G','-Djava.system.class.loader=ghidra.GhidraClassLoader','-Dfile.encoding=UTF8',
    "-Dapplication.settingsdir=$researchSettings/config","-Dapplication.cachedir=$researchSettings/cache","-Dapplication.tempdir=$researchSettings/tmp",
    '-cp',"$GhidraDirectory/Ghidra/Framework/Utility/lib/Utility.jar",'ghidra.Ghidra','ghidra.app.util.headless.AnalyzeHeadless',
    $researchDatabase,'SporeCell','-scriptPath',"$researchProjectRoot/tools/ghidra",'-max-cpu','4',
    '-log',"$researchEvidence/analysis.log",'-scriptlog',"$researchEvidence/script.log")
if($Mode -eq 'Import') {
    if(Test-Path -LiteralPath "$researchDatabase/SporeCell.gpr") { throw 'Persistent project already exists. Use Index, Decompile or Xrefs to reuse it.' }
    Get-FileHash -LiteralPath $SporeExecutable -Algorithm SHA256
    $researchArgs+=@('-import',$SporeExecutable,'-preScript','SporeFastAnalysis.java',"$researchEvidence/analyzers.tsv",'-analysisTimeoutPerFile','600',
        '-postScript','SporeResearchExport.java',$researchEvidence,'index')
} else {
    if(!(Test-Path -LiteralPath "$researchDatabase/SporeCell.gpr")) { throw 'Run -Mode Import once to create the persistent project.' }
    $researchArgs+=@('-process','SporeApp.exe','-noanalysis','-postScript')
    if($Mode -eq 'Signatures') {
        $researchArgs+=@('SporeVerifiedCellSignatures.java',"$researchEvidence/signatures.tsv")
    }
    elseif($Mode -eq 'Define') {
        if(!$RequestFile) { throw 'RequestFile is required for Define.' }
        $researchArgs+=@('SporeDefineReferencedFunctions.java',"$researchEvidence/defined-functions.tsv",(Resolve-Path -LiteralPath $RequestFile).Path)
    }
    elseif($Mode -eq 'Repair') {
        if(!$RequestFile) { throw 'RequestFile is required for Repair.' }
        $researchArgs+=@('SporeRepairStackPurge.java',"$researchEvidence/stack-purge.tsv",(Resolve-Path -LiteralPath $RequestFile).Path)
    }
    else {
        $researchArgs+=@('SporeResearchExport.java',$researchEvidence)
        if($Mode -eq 'Index') { $researchArgs+='index' }
        else {
            if(!$RequestFile) { throw 'RequestFile is required for Decompile/Xrefs.' }
            $researchArgs+=@($(if($Mode -eq 'Decompile'){'decompile'}elseif($Mode -eq 'Data'){'data'}else{'xref'}),(Resolve-Path -LiteralPath $RequestFile).Path)
        }
    }
}
& $JavaExecutable @researchArgs
if($LASTEXITCODE -ne 0) { throw "Ghidra failed with exit $LASTEXITCODE" }
if(Select-String -LiteralPath "$researchEvidence/analysis.log" -Pattern 'Analysis timed out' -Quiet) {
    Write-Warning 'Auto-analysis is partial. Exported functions remain usable with the recorded limitations.'
}
