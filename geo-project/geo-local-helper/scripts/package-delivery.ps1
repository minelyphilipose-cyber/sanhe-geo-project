param(
  [string]$OutputDir = "dist"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$packageJsonPath = Join-Path $root "package.json"
$packageJson = Get-Content $packageJsonPath -Raw | ConvertFrom-Json
$version = $packageJson.version
$packageName = "geo-local-helper-v$version"
$distRoot = Join-Path $root $OutputDir
$stageDir = Join-Path $distRoot $packageName
$zipPath = Join-Path $distRoot "$packageName.zip"

if (Test-Path $stageDir) {
  Remove-Item -LiteralPath $stageDir -Recurse -Force
}
if (Test-Path $zipPath) {
  Remove-Item -LiteralPath $zipPath -Force
}

New-Item -ItemType Directory -Force -Path $stageDir | Out-Null

$files = @(
  "package.json",
  "package-lock.json",
  "config.example.json",
  "README.md"
)

foreach ($file in $files) {
  Copy-Item -LiteralPath (Join-Path $root $file) -Destination (Join-Path $stageDir $file)
}

$directories = @(
  "public",
  "src"
)

foreach ($directory in $directories) {
  Copy-Item -LiteralPath (Join-Path $root $directory) -Destination (Join-Path $stageDir $directory) -Recurse
}

$scriptStageDir = Join-Path $stageDir "scripts"
New-Item -ItemType Directory -Force -Path $scriptStageDir | Out-Null
Copy-Item -LiteralPath (Join-Path $root "scripts/c2-security-check.mjs") -Destination (Join-Path $scriptStageDir "c2-security-check.mjs")

$deliveryReadme = @(
  "# GEO Local Helper Delivery",
  "",
  "This package intentionally excludes:",
  "",
  "- node_modules",
  "- runtime",
  "- config.local.json",
  "",
  "Install and start:",
  "",
  "````powershell",
  "npm install --omit=dev",
  "Copy-Item config.example.json config.local.json",
  "notepad config.local.json",
  "npm start",
  "````",
  "",
  "Then open:",
  "",
  "````text",
  "http://127.0.0.1:17891/",
  "````"
)

Set-Content -Path (Join-Path $stageDir "DELIVERY.md") -Value $deliveryReadme -Encoding UTF8

Compress-Archive -LiteralPath $stageDir -DestinationPath $zipPath -Force

Write-Host "DELIVERY_PACKAGE=$zipPath"
