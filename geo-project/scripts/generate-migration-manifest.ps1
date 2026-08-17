[CmdletBinding()]
param(
    [string]$OutputPath,
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
} else {
    $projectRoot = (Resolve-Path $ProjectRoot).Path
}
$migrationRoot = Join-Path $projectRoot 'geo-server/src/main/resources/db/migration'
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $projectRoot 'docs/data-retention-migration-manifest-2026-08-17.csv'
} elseif (-not [System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $projectRoot $OutputPath
}

if (-not ('FlywayCompatibleChecksum' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;

public static class FlywayCompatibleChecksum
{
    private static readonly uint[] Table = BuildTable();

    public static int Calculate(string path)
    {
        uint crc = 0xffffffffu;
        using (var reader = new StreamReader(path, new UTF8Encoding(false, true), true, 4096))
        {
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                if (line.Length > 0 && line[0] == '\ufeff')
                {
                    line = line.Substring(1);
                }
                foreach (byte value in Encoding.UTF8.GetBytes(line))
                {
                    crc = Table[(crc ^ value) & 0xff] ^ (crc >> 8);
                }
            }
        }
        return unchecked((int)(crc ^ 0xffffffffu));
    }

    private static uint[] BuildTable()
    {
        var table = new uint[256];
        for (uint index = 0; index < table.Length; index++)
        {
            uint value = index;
            for (var bit = 0; bit < 8; bit++)
            {
                value = (value & 1) == 1 ? 0xedb88320u ^ (value >> 1) : value >> 1;
            }
            table[index] = value;
        }
        return table;
    }
}
'@
}

function Get-CanonicalMigrationBytes {
    param([Parameter(Mandatory = $true)][string]$Path)

    $raw = [System.IO.File]::ReadAllBytes($Path)
    $strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
    try {
        $text = $strictUtf8.GetString($raw)
    } catch {
        throw "Migration is not valid UTF-8: $Path"
    }
    $normalized = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    return ,([System.Text.UTF8Encoding]::new($false).GetBytes($normalized))
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return (($algorithm.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally {
        $algorithm.Dispose()
    }
}

$migrations = @(
    Get-ChildItem -LiteralPath $migrationRoot -File -Filter 'V*.sql' | ForEach-Object {
        if ($_.Name -notmatch '^V(?<version>[0-9]+(?:_[0-9]+)*)__.+\.sql$') {
            throw "Invalid versioned migration filename: $($_.Name)"
        }
        $version = $Matches.version.Replace('_', '.')
        $versionParts = @($version.Split('.') | ForEach-Object { [int]$_ })
        $sortKey = '{0:D10}.{1:D10}' -f $versionParts[0], $(
            if ($versionParts.Count -gt 1) { $versionParts[1] } else { 0 }
        )
        $description = $_.BaseName.Substring($_.BaseName.IndexOf('__') + 2)
        $migrationPath = $_.FullName
        if (-not $migrationPath.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Migration is outside the project root: $migrationPath"
        }
        $relativePath = $migrationPath.Substring($projectRoot.Length + 1).Replace('\', '/')
        $canonicalBytes = [byte[]](Get-CanonicalMigrationBytes -Path $_.FullName)
        [PSCustomObject]@{
            SortVersion = $sortKey
            Version = $version
            Description = $description
            Path = $relativePath
            Bytes = $canonicalBytes.Length
            Sha256 = Get-Sha256 -Bytes $canonicalBytes
            FlywayChecksum = [FlywayCompatibleChecksum]::Calculate($_.FullName)
        }
    } | Sort-Object SortVersion
)

if ($migrations.Count -ne 368) {
    throw "Expected 368 versioned migrations, found $($migrations.Count)"
}
$duplicates = @($migrations | Group-Object Version | Where-Object Count -ne 1)
if ($duplicates.Count -gt 0) {
    throw "Duplicate migration versions: $($duplicates.Name -join ', ')"
}
if ($migrations[-1].Version -ne '368') {
    throw "Expected highest migration V368, found V$($migrations[-1].Version)"
}

$outputDirectory = Split-Path -Parent $OutputPath
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$csvLines = @($migrations |
    Select-Object Version, Description, Path, Bytes, Sha256, FlywayChecksum |
    ConvertTo-Csv -NoTypeInformation)
[System.IO.File]::WriteAllText(
    $OutputPath,
    ($csvLines -join "`n") + "`n",
    [System.Text.UTF8Encoding]::new($false))

Write-Host "Wrote $($migrations.Count) migrations to $OutputPath"
