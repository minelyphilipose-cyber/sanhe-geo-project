param(
    [switch]$FailOnCompatibleDiff
)

$legacy = [ordered]@{
    super_admin = @('*')
    manager = @(
        'user.manage','role.manage','permission.manage',
        'partner.read','partner.write','partner.create','partner.update','partner.status.update','partner.discount.update',
        'partner.account.read','partner.account.recharge.audit','partner.account.adjust','partner.account.txn.read',
        'company.read','company.write','company.create','company.update','company.delete',
        'brand.read','brand.create','brand.update','brand.delete',
        'project.read','project.write','project.create','project.update','project.start','project.pause','project.terminate','project.delete',
        'project.status.activate','project.status.close','project.report.read','project.report.export',
        'package.read','package.manage','activity_log.read','activity_log.finance.read',
        'question_pool.core.confirm','question_pool.core.delete','report.review',
        'dispatch.alert.resolve','dispatch.task.replay.dead_letter',
        'brand.statement.lock','keyword_group.read','keyword_group.write','keyword_affix.manage'
    )
    delivery_manager = @(
        'company.read','company.write','company.create','company.update',
        'brand.read','brand.create','brand.update',
        'project.read','project.write','project.create','project.update','project.start','project.pause','project.terminate',
        'partner.read','package.read','activity_log.read',
        'project.status.activate','project.status.close','project.report.read','project.report.export',
        'question_pool.core.confirm','question_pool.core.delete','report.review',
        'dispatch.alert.resolve','brand.statement.lock','keyword_group.read','keyword_group.write'
    )
    operator = @(
        'company.read','company.write','company.create','company.update',
        'brand.read','brand.create','brand.update',
        'project.read','project.write','project.create','project.update','project.start','project.pause','project.terminate',
        'partner.read','package.read','project.report.read','project.report.export',
        'keyword_group.read','keyword_group.write'
    )
    sales = @('company.read','brand.read','project.read','project.report.read','keyword_group.read')
    partner = @(
        'partner.read','partner.account.read','partner.account.recharge.apply','partner.account.txn.read','partner.staff.manage',
        'company.read','company.write','company.create','company.update',
        'brand.read','brand.create','brand.update',
        'project.read','project.write','project.create','project.update','project.start','project.report.read','project.report.export',
        'package.read'
    )
    partner_staff = @(
        'partner.read',
        'company.read','company.write','company.create','company.update',
        'brand.read','brand.create','brand.update',
        'project.read','project.create','project.update','project.report.read',
        'package.read'
    )
    partner_viewer = @('partner.read','company.read','brand.read','project.read','project.report.read','package.read')
}

$deprecated = @(
    'company.write',
    'project.write',
    'project.status.activate',
    'project.status.close',
    'project.status.update',
    'project.flow.update',
    'project.sign_and_deduct',
    'partner.write'
)

$dbActive = [ordered]@{}
foreach ($role in $legacy.Keys) {
    if ($role -eq 'super_admin') {
        $dbActive[$role] = @('*')
        continue
    }
    $dbActive[$role] = @($legacy[$role] | Where-Object { $deprecated -notcontains $_ })
}

$dbCompatible = $legacy

function Compare-PermissionSet([string]$Role, [string[]]$Left, [string[]]$Right, [string]$Mode) {
    $leftSet = @($Left | Sort-Object -Unique)
    $rightSet = @($Right | Sort-Object -Unique)
    $leftOnly = @($leftSet | Where-Object { $rightSet -notcontains $_ })
    $rightOnly = @($rightSet | Where-Object { $leftSet -notcontains $_ })
    [PSCustomObject]@{
        Mode = $Mode
        Role = $Role
        LeftCount = $leftSet.Count
        RightCount = $rightSet.Count
        LeftOnly = ($leftOnly -join ',')
        RightOnly = ($rightOnly -join ',')
    }
}

$rows = @()
foreach ($role in $legacy.Keys) {
    $rows += Compare-PermissionSet -Role $role -Left $legacy[$role] -Right $dbActive[$role] -Mode 'legacy_vs_db_active'
    $rows += Compare-PermissionSet -Role $role -Left $legacy[$role] -Right $dbCompatible[$role] -Mode 'legacy_vs_db_compatible'
}

$rows | Format-Table -AutoSize

$compatibleDiff = @($rows | Where-Object {
    $_.Mode -eq 'legacy_vs_db_compatible' -and ($_.LeftOnly -or $_.RightOnly)
})
if ($FailOnCompatibleDiff -and $compatibleDiff.Count -gt 0) {
    throw "Compatible DB grants differ from LEGACY_ROLE_PERMS"
}
