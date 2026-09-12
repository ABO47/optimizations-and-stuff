param(
    [string]$ModsDir = "D:\Games\hytale game\game\UserData\Mods"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
if (-not $ProjectRoot) { $ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location $ProjectRoot

# Read mod_id from gradle.properties so the jar pattern stays correct after renaming.
$props = Get-Content "$ProjectRoot\gradle.properties"
$modId = ($props | Where-Object { $_ -match '^mod_id\s*=' } | Select-Object -First 1) -replace '^mod_id\s*=\s*',''
if (-not $modId) { Write-Error "Could not find mod_id in gradle.properties"; exit 1 }

# 1. Build
$env:JAVA_HOME = "C:\Users\Abdullah47\.jdks\temurin-25.0.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Write-Host "Building mod..." -ForegroundColor Cyan
& .\gradlew build -x test
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed"; exit $LASTEXITCODE }
Write-Host "Build OK" -ForegroundColor Green

# 2. Find built jar (exclude sources/javadoc jars)
$jar = Get-ChildItem -Path "$ProjectRoot\build\libs" -Filter "*.jar" -File |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Write-Error "No jar found in build/libs"; exit 1 }
Write-Host "Built: $($jar.Name)" -ForegroundColor Green

# 3. Ensure Mods dir exists
if (-not (Test-Path $ModsDir)) { New-Item -ItemType Directory -Path $ModsDir -Force | Out-Null }

# 4. Remove previous versions of this mod (match by mod_id)
$pattern = "*$modId*.jar"
Get-ChildItem -Path $ModsDir -Filter $pattern -File -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Name -eq $jar.Name) { return }
    Write-Host "Removing old: $($_.Name)" -ForegroundColor Yellow
    Remove-Item -LiteralPath $_.FullName -Force
}

# 5. Deploy
$dest = Join-Path $ModsDir $jar.Name
Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force
Write-Host "Deployed -> $dest" -ForegroundColor Green
Get-Item $dest | Format-List Name,Length,LastWriteTime | Out-String | Write-Host
