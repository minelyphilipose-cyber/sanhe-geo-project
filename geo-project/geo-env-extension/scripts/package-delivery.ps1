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
$manifestPath = Join-Path $root "manifest.json"
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$version = $manifest.version
$packageName = "geo-env-extension-$Environment-v$version"
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
  "content-script.js",
  "fill-result.js",
  "identity-policy.js",
  "manifest.json",
  "platform-baijiahao.js",
  "platform-douyin.js",
  "platform-toutiao.js",
  "platform-xiaohongshu.js",
  "platform-zhihu.js",
  "popup.html",
  "popup.js",
  "README.md",
  "service-worker.js"
)

foreach ($file in $files) {
  Copy-Item -LiteralPath (Join-Path $root $file) -Destination (Join-Path $stageDir $file)
}

$profileMap = @{
  prod = @{
    profileLabel = "Production"
    apiBase = "https://www.huanjingaigeo.com"
    helperBase = "http://127.0.0.1:17891"
    manifestName = $manifest.name
  }
  dev = @{
    profileLabel = "Development"
    apiBase = "http://127.0.0.1:8080"
    helperBase = "http://127.0.0.1:17891"
    manifestName = "GEO Media Helper DEV"
  }
}

$profile = $profileMap[$Environment]
$envConfig = @(
  "globalThis.GEO_ENV_BUILD_CONFIG = {",
  "  profileKey: '$Environment',",
  "  profileLabel: '$($profile.profileLabel)',",
  "  apiBase: '$($profile.apiBase)',",
  "  helperBase: '$($profile.helperBase)',",
  "}"
)
Write-Utf8NoBom -Path (Join-Path $stageDir "env-config.js") -Value ($envConfig -join [Environment]::NewLine)

$manifest.name = $profile.manifestName
Write-Utf8NoBom -Path (Join-Path $stageDir "manifest.json") -Value ($manifest | ConvertTo-Json -Depth 8)

$deliveryReadme = @(
  "# GEO Env Extension Delivery",
  "",
  "Environment: $Environment",
  "",
  "Load this directory in AdsPower/Chrome extension manager:",
  "",
  "````text",
  $stageDir,
  "````",
  "",
  "The extension environment is fixed by env-config.js in this package."
)
Write-Utf8NoBom -Path (Join-Path $stageDir "DELIVERY.md") -Value ($deliveryReadme -join [Environment]::NewLine)

Compress-Archive -LiteralPath $stageDir -DestinationPath $zipPath -Force

Write-Host "DELIVERY_PACKAGE=$zipPath"
