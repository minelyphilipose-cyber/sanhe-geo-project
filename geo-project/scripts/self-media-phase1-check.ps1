param(
    [switch]$SkipMavenCheck
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $repoRoot 'geo-server'

if (-not (Test-Path $serverDir)) {
    throw "geo-server directory not found: $serverDir"
}

if (-not $SkipMavenCheck) {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -eq $mvn) {
        throw 'Maven is not available on PATH. Install Maven or run the equivalent mvn test command manually.'
    }
}

$tests = @(
    'com.huanjing.geo.module.content.douyin.DouyinImageTextAdapterTest',
    'com.huanjing.geo.module.content.douyin.DouyinMediaServiceTest',
    'com.huanjing.geo.module.content.douyin.DouyinTokenServiceTest',
    'com.huanjing.geo.module.content.douyin.client.RealDouyinClientTest',
    'com.huanjing.geo.module.content.douyin.client.DouyinErrorMapperTest',
    'com.huanjing.geo.module.content.douyin.client.DouyinDtoDeserializationTest',
    'com.huanjing.geo.module.content.service.adapter.WechatMpAdapterTest',
    'com.huanjing.geo.module.content.wechat.WechatApiErrorHandlerTest',
    'com.huanjing.geo.module.content.wechat.WechatTokenAwareExecutorTest',
    'com.huanjing.geo.module.content.service.SelfMediaPublishScheduleServiceTest',
    'com.huanjing.geo.module.content.service.DistributionTaskStatePolicyTest'
)

Write-Host 'Running self-media phase 1 verification tests...'
Write-Host 'These tests use mocks or local in-process HTTP servers only. They do not call Douyin or WeChat.'

Push-Location $serverDir
try {
    mvn "-Dtest=$($tests -join ',')" test
} finally {
    Pop-Location
}
