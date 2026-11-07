[CmdletBinding()]
param(
  [string]$Repo='E:\XD\NV1\reportDevelopingOOPsoftware-test',
  [switch]$IncludeLatestAudit
)
$ErrorActionPreference='Stop'
$branch='test-2026-11-07-latest'
Set-Location $Repo
if((git branch --show-current).Trim() -ne $branch){throw "Sai branch: expected $branch"}
$root=Split-Path -Parent $PSScriptRoot
$paths=@('.github/workflows/scentstation-integration-ci.yml','scripts/CHECK-ALL-1107.ps1','scripts/PUBLISH-CHECK-1107.ps1','RUN-CHECK-ALL.cmd','PUBLISH-CHECK-1107.cmd','docs/qa/README-TEST-1107.md')
$state = @(git status --porcelain --untracked-files=no)
if($state.Count -gt 0){throw 'Co thay doi tracked chua commit. Khong publish tu dong.'}
$mergeHead=(git rev-parse --git-path MERGE_HEAD).Trim()
if(Test-Path $mergeHead){throw 'Con merge conflict; khong publish.'}
# Check remote has not advanced before updating to avoid overwriting others.
git fetch origin --prune
if($LASTEXITCODE -ne 0){throw 'Git fetch failed'}
$localBefore=(git rev-parse HEAD).Trim()
$remoteBefore=(git rev-parse "origin/$branch").Trim()
if($localBefore -ne $remoteBefore){throw "Remote changed! Local: $localBefore, Remote: $remoteBefore"}
foreach($p in $paths){
    $src=Join-Path $root $p
    $dest=Join-Path $Repo $p
    if(-not(Test-Path $src)){throw "Missing package file: $src"}
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    Copy-Item -LiteralPath $src -Destination $dest -Force
}
if($IncludeLatestAudit){
    $audit=Get-ChildItem 'E:\XD\ScentStation\reports' -Directory -ErrorAction SilentlyContinue |
        Where-Object {$_.Name -like 'audit-1107-*'} | Sort-Object Name -Descending | Select-Object -First 1
    if($audit -and (Test-Path (Join-Path $audit.FullName 'SUMMARY.md'))){
       $rel='docs/qa/latest-audit-1107.md'
       Copy-Item -LiteralPath (Join-Path $audit.FullName 'SUMMARY.md') -Destination (Join-Path $Repo $rel) -Force
       $paths += $rel
    } else { throw 'Khong tim thay SUMMARY.md. Chay RUN-CHECK-ALL.cmd truoc hoac bo -IncludeLatestAudit.' }
}
foreach($p in $paths){ git add -- $p; if($LASTEXITCODE -ne 0){throw "git add failed: $p"}}
$staged=@(git diff --cached --name-only)
if($staged.Count -eq 0){ Write-Host 'No new changes to publish.' -ForegroundColor Yellow;exit 0 }
$extra=@($staged | Where-Object {$_ -notin $paths})
if($extra.Count -gt 0){throw "Unexpected staged files: $($extra -join ', ')"}
Write-Host 'Files to commit:' -ForegroundColor Cyan
$staged | ForEach-Object {Write-Host " - $_"}
git commit -m 'ci(test): add reproducible integration audit and GitHub Actions checks'
if($LASTEXITCODE -ne 0){throw 'Commit failed'}
git push origin "HEAD:refs/heads/$branch"
if($LASTEXITCODE -ne 0){throw 'Push failed'}
$new=(git rev-parse HEAD).Trim()
$sha=((& git ls-remote origin "refs/heads/$branch") -split '\s+')[0]
if($new -ne $sha){throw "Push verification mismatch: $new vs $sha"}
Write-Host "PUBLISHED SUCCESSFULLY: $branch @ $new" -ForegroundColor Green
Write-Host "https://github.com/phuoc523/reportDevelopingOOPsoftware/actions" -ForegroundColor Cyan
