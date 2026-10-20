param(
    [string[]]$EvidenceNames=@('cell-selected','cell-region','editor-first-pass','editor-model-pass','editor-core-pass','editor-region','editor-drag-pass','editor-drag-methods','editor-attachment-pass','editor-focus-pass','cell-typed-pass','editor-message-pass'),
    [string]$ManifestName='editor-cell-manifest.csv'
)
$ErrorActionPreference='Stop'
$manifestRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$selectedRecords=[ordered]@{}
$unknownRecords=@()
foreach($evidenceName in $EvidenceNames) {
    if($evidenceName -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Evidence name must be a directory name.'}
    $evidenceDirectory=Join-Path $manifestRoot "docs/research/evidence/$evidenceName"
    $identity=Get-Content -LiteralPath (Join-Path $evidenceDirectory 'identity.txt')
    if($identity -notcontains 'md5=32aad7cad752048c9686ff9eb924263d'){throw "Unexpected binary identity in $evidenceName"}
    foreach($record in Import-Csv -LiteralPath (Join-Path $evidenceDirectory 'decompile-status.tsv') -Delimiter "`t") {
        if($record.status -ne 'OK') {
            $unknownRecords += [PSCustomObject]@{address=$record.address;evidence=$evidenceName;status=$record.status;reason=$record.message}
            continue
        }
        $hashes=@{}
        foreach($extension in @('.c','.asm','-links.tsv')) {
            $artifact=Join-Path $evidenceDirectory ($record.address+$extension)
            if(!(Test-Path -LiteralPath $artifact) -or (Get-Item -LiteralPath $artifact).Length -eq 0){throw "Missing artifact $artifact"}
            $hashes[$extension]=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
        }
        $selectedRecords[$record.address]=[PSCustomObject]@{
            address=$record.address;label=$record.label;status=$record.status;evidence=$evidenceName
            c_sha256=$hashes['.c'];asm_sha256=$hashes['.asm'];links_sha256=$hashes['-links.tsv']
        }
    }
}
if($ManifestName -notmatch '^[a-zA-Z0-9_-]+\.csv$'){throw 'ManifestName must be a CSV file name.'}
$selectedRecords.Values | Sort-Object address | Export-Csv -LiteralPath (Join-Path $manifestRoot "docs/research/$ManifestName") -NoTypeInformation -Encoding utf8
$unknownRecords=@($unknownRecords | Where-Object {!$selectedRecords.Contains($_.address)})
if($unknownRecords.Count){$unknownRecords | Export-Csv -LiteralPath (Join-Path $manifestRoot 'docs/research/decompilation-unknowns.csv') -NoTypeInformation -Encoding utf8}
[PSCustomObject]@{unique_successful_functions=$selectedRecords.Count;verified_sidecars=$selectedRecords.Count*3;unknown_requests=$unknownRecords.Count;manifest=$ManifestName}
