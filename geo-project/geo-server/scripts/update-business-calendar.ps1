param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(2000, 2100)]
    [int] $Year,

    [string] $OutputDir = (Join-Path $PSScriptRoot "..\src\main\resources\calendar"),

    [switch] $AllowAdjustedWorkdays
)

$ErrorActionPreference = "Stop"

$sourceUrl = "https://timor.tech/api/holiday/year/$Year/?type=Y&week=Y"
$defaultWindows = @()
$defaultWindows += [ordered]@{
    name = "morning"
    start = "09:30"
    end = "11:30"
    preferredTime = "10:00"
}
$defaultWindows += [ordered]@{
    name = "afternoon"
    start = "14:00"
    end = "17:30"
    preferredTime = "15:00"
}

function Get-IsoWeekNumber {
    param([datetime] $Date)
    if ([int] $Date.DayOfWeek -eq 0) {
        return 7
    }
    return [int] $Date.DayOfWeek
}

function Get-DefaultDayType {
    param([int] $Week)
    if ($Week -ge 6) {
        return 1
    }
    return 0
}

function Get-DefaultDayName {
    param(
        [int] $Type,
        [int] $Week
    )
    if ($Type -eq 1) {
        if ($Week -eq 6) {
            return "Saturday"
        }
        return "Sunday"
    }
    return "Workday"
}

Write-Host "Fetching business calendar source: $sourceUrl"
$response = Invoke-RestMethod -Uri $sourceUrl
if ($response.code -ne 0) {
    throw "Holiday API returned code=$($response.code)"
}

$days = @()
$date = [datetime]::new($Year, 1, 1)
while ($date.Year -eq $Year) {
    $dateKey = $date.ToString("yyyy-MM-dd")
    $typeNode = $response.type.PSObject.Properties[$dateKey].Value
    $week = Get-IsoWeekNumber -Date $date

    if ($null -ne $typeNode) {
        $type = [int] $typeNode.type
        $name = [string] $typeNode.name
        if ($typeNode.PSObject.Properties.Name -contains "week") {
            $week = [int] $typeNode.week
        }
    } else {
        $type = Get-DefaultDayType -Week $week
        $name = Get-DefaultDayName -Type $type -Week $week
    }

    $isWeekend = $week -ge 6
    $isHoliday = $type -eq 2
    $isAdjustedWorkday = $type -eq 3
    $isWorkday = $type -eq 0 -or $type -eq 3
    $publishAllowed = $type -eq 0 -or ($AllowAdjustedWorkdays -and $type -eq 3)
    $publishWindows = New-Object System.Collections.ArrayList
    if ($publishAllowed) {
        foreach ($window in $defaultWindows) {
            [void] $publishWindows.Add($window)
        }
    }

    $days += [ordered]@{
        date = $dateKey
        type = $type
        name = $name
        week = $week
        isWorkday = $isWorkday
        isWeekend = $isWeekend
        isHoliday = $isHoliday
        isAdjustedWorkday = $isAdjustedWorkday
        publishAllowed = $publishAllowed
        publishWindows = @($publishWindows.ToArray())
    }

    $date = $date.AddDays(1)
}

$policy = if ($AllowAdjustedWorkdays) {
    "Normal workdays and adjusted workdays are publishable; weekends and statutory holidays are blocked"
} else {
    "Normal workdays are publishable; weekends, statutory holidays, and adjusted workdays are blocked by default"
}

$calendar = [ordered]@{
    year = $Year
    source = "timor.tech/api/holiday"
    sourceUrl = $sourceUrl
    policy = $policy
    updatedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz")
    days = $days
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$outputPath = Join-Path $OutputDir "business-calendar-$Year.json"
$calendar | ConvertTo-Json -Depth 8 | Set-Content -Path $outputPath -Encoding UTF8

Write-Host "Business calendar written: $outputPath"
