[CmdletBinding()]
param(
    [switch]$IncludeCos
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$serverRoot = Join-Path $projectRoot 'geo-server'
$webRoot = Join-Path $projectRoot 'geo-web'

function Require-EnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable is missing: $Name"
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    Write-Host "Running $Name"
    Push-Location $WorkingDirectory
    try {
        & $Command
        if ($LASTEXITCODE -ne 0) {
            throw "$Name failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

@(
    'RETENTION_MYSQL_IT_SERVER_URL',
    'RETENTION_REDIS_IT_HOST',
    'RETENTION_MINIO_IT_ENDPOINT',
    'RETENTION_MINIO_IT_ACCESS_KEY',
    'RETENTION_MINIO_IT_SECRET_KEY'
) | ForEach-Object { Require-EnvironmentValue $_ }

if ($IncludeCos) {
    @(
        'RETENTION_COS_IT_REGION',
        'RETENTION_COS_IT_BUCKET',
        'RETENTION_COS_IT_SECRET_ID',
        'RETENTION_COS_IT_SECRET_KEY',
        'RETENTION_COS_IT_PREFIX'
    ) | ForEach-Object { Require-EnvironmentValue $_ }
}

Invoke-Checked 'backend package' $serverRoot { mvn -DskipTests package }

$profiles = @(
    'retention-v133-it',
    'retention-switch-it',
    'retention-coordinator-it',
    'retention-website-it',
    'retention-poll-it',
    'retention-mysql-it',
    'retention-article-minio-it'
)
if ($IncludeCos) {
    $profiles += 'retention-article-cos-it'
} else {
    Write-Warning 'COS Required profile was not requested; production Gate 0 remains open.'
}

$profiles | ForEach-Object {
    $profile = $_
    Invoke-Checked $profile $serverRoot { mvn "-P$profile" test }
}

Invoke-Checked 'frontend production build' $webRoot { npm run build }
Invoke-Checked 'git diff check' $projectRoot { git diff --check }

$untrackedRetentionFiles = @(
    git -C $projectRoot ls-files --others --exclude-standard |
        Where-Object {
            $_ -match '(?i)(data[_-]retention|dataRetention|retention[/\\]|V(354|359|364|366|367|368)__)'
        }
)

$formatErrors = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $untrackedRetentionFiles) {
    $absolutePath = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
        continue
    }
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($absolutePath)) {
        $lineNumber++
        if ($line -match '[\t ]+$') {
            $formatErrors.Add("$relativePath`:$lineNumber trailing whitespace")
        }
    }
}

if ($formatErrors.Count -gt 0) {
    throw ($formatErrors -join [Environment]::NewLine)
}

if ($IncludeCos) {
    Write-Host 'All data retention Gate 0 Required checks, including COS, passed.'
} else {
    Write-Host 'All local data retention Required checks passed; COS was not included.'
}
