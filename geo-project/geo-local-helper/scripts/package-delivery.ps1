param(
  [ValidateSet("prod", "dev")]
  [string]$Environment = "prod",
  [string]$OutputDir = "dist"
)

$ErrorActionPreference = "Stop"

function Write-Utf8NoBom {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [Parameter(Mandatory = $true)]
    [string]$Value
  )
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($Path, $Value, $utf8NoBom)
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$packageJsonPath = Join-Path $root "package.json"
$packageJson = Get-Content $packageJsonPath -Raw | ConvertFrom-Json
$version = $packageJson.version
$packageName = "geo-local-helper-$Environment-v$version"
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

$profileMap = @{
  prod = @{
    label = "Production"
    trustedBackendBase = "https://www.huanjingaigeo.com"
    allowedOrigins = @(
      "http://127.0.0.1:17891",
      "http://localhost:17891",
      "https://www.huanjingaigeo.com"
    )
  }
  dev = @{
    label = "Development"
    trustedBackendBase = "http://127.0.0.1:8080"
    allowedOrigins = @(
      "http://127.0.0.1:17891",
      "http://localhost:17891",
      "http://127.0.0.1:8080",
      "http://localhost:8080",
      "http://127.0.0.1:5173",
      "http://localhost:5173"
    )
  }
}

$selectedProfile = $profileMap[$Environment]
$profiles = [ordered]@{}
$profiles[$Environment] = [ordered]@{
  label = $selectedProfile.label
  trustedBackendBase = $selectedProfile.trustedBackendBase
  allowedOrigins = $selectedProfile.allowedOrigins
}
$config = [ordered]@{
  host = "127.0.0.1"
  port = 17891
  activeProfile = $Environment
  profiles = $profiles
  enableLegacyBackendTokenRoutes = $false
  enableStaticHelperToken = $false
  helperToken = ""
  adspower = [ordered]@{
    apiBase = "http://localhost:50325"
  }
}
Write-Utf8NoBom -Path (Join-Path $stageDir "config.example.json") -Value ($config | ConvertTo-Json -Depth 8)

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
  "Environment: $Environment",
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
  "npm start",
  "````",
  "",
  "Then open:",
  "",
  "````text",
  "http://127.0.0.1:17891/",
  "````"
)

Write-Utf8NoBom -Path (Join-Path $stageDir "DELIVERY.md") -Value ($deliveryReadme -join [Environment]::NewLine)

Compress-Archive -LiteralPath $stageDir -DestinationPath $zipPath -Force

Write-Host "DELIVERY_PACKAGE=$zipPath"
