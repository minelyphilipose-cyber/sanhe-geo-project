param(
  [string]$BaseUrl = "http://localhost:8080/api",
  [string]$AdminUser = "admin",
  [string]$AdminPass = "admin123",
  [string]$PartnerUser = "",
  [string]$PartnerPass = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Login([string]$username, [string]$password) {
  $body = @{ username = $username; password = $password } | ConvertTo-Json
  $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $body
  if ($resp.code -ne 0) {
    throw "Login failed for $username: $($resp.message)"
  }
  return $resp.data
}

function CallApi([string]$method, [string]$path, [string]$token, $body = $null) {
  $headers = @{ Authorization = "Bearer $token" }
  $uri = "$BaseUrl$path"
  if ($null -eq $body) {
    return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers
  }
  $json = $body | ConvertTo-Json -Depth 6
  return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers -ContentType "application/json" -Body $json
}

Write-Host "== Phase2 RBAC smoke test ==" -ForegroundColor Cyan

$admin = Login -username $AdminUser -password $AdminPass
Write-Host "Admin login ok: $($admin.user.username)" -ForegroundColor Green

$adminMe = CallApi -method Get -path "/me" -token $admin.accessToken
Write-Host "Admin permissions count: $($adminMe.data.permissions.Count)"

$userList = CallApi -method Get -path "/admin/users?current=1&size=5" -token $admin.accessToken
Write-Host "Admin users API ok, total: $($userList.data.total)" -ForegroundColor Green

$partnerList = CallApi -method Get -path "/partners?current=1&size=5" -token $admin.accessToken
Write-Host "Admin partner API ok, total: $($partnerList.data.total)" -ForegroundColor Green

if ($PartnerUser -and $PartnerPass) {
  $partner = Login -username $PartnerUser -password $PartnerPass
  Write-Host "Partner login ok: $($partner.user.username)" -ForegroundColor Green

  $partnerMe = CallApi -method Get -path "/me" -token $partner.accessToken
  Write-Host "Partner permissions: $($partnerMe.data.permissions -join ',')"

  $myCompany = CallApi -method Get -path "/companies?current=1&size=5" -token $partner.accessToken
  Write-Host "Partner company API ok, total: $($myCompany.data.total)" -ForegroundColor Green

  try {
    $null = CallApi -method Get -path "/admin/users?current=1&size=5" -token $partner.accessToken
    Write-Host "Unexpected: partner can access /admin/users" -ForegroundColor Red
    exit 1
  } catch {
    Write-Host "Partner blocked from /admin/users as expected" -ForegroundColor Green
  }
}

Write-Host "Smoke test completed." -ForegroundColor Cyan
