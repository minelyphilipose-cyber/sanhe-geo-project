$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{username='admin';password='admin123'} | ConvertTo-Json)
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
function Get-Api($path) { Invoke-RestMethod -Method Get -Uri "$base$path" -Headers $headers }
function Post-Api($path, $body) {
  $res = Invoke-RestMethod -Method Post -Uri "$base$path" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ($body | ConvertTo-Json -Depth 20)
  if ($res.code -ne 0) {
    throw "$($res.code): $($res.message)"
  }
  return $res
}
function Word($text, $source='system', $order=10) { @{ wordText=$text; source=$source; sortOrder=$order } }
function Items($texts, $source='system') {
  $i=0
  $items = @($texts | ForEach-Object { $i++; Word $_ $source ($i*10) })
  return ,$items
}
function Preview($name, $type, $columns, $extra=@{}) {
  $payload = @{ companyId=4; name=$name; type=$type; count=1000; columns=$columns }
  foreach ($k in $extra.Keys) { $payload[$k] = $extra[$k] }
  try { (Post-Api '/keyword-groups/preview' $payload).data }
  catch { @{ error = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message } } }
}
function Take-Words($arr, $n) { @($arr | Select-Object -First $n | ForEach-Object { $_.wordText }) }

$typeConfigs = (Get-Api '/keyword-groups/type-configs').data
$options = @{}
foreach ($t in @('brand','decision','transaction','comparison','qa')) { $options[$t] = (Get-Api "/keyword-affix-words/options?type=$t").data }
$options['function_appliance'] = (Get-Api '/keyword-affix-words/options?type=function&industryTag=appliance').data
$options['function_door'] = (Get-Api '/keyword-affix-words/options?type=function&industryTag=door_window').data

$results = [ordered]@{}
$results.typeConfigCount = $typeConfigs.Count
$results.typeConfigTypes = @($typeConfigs | ForEach-Object { $_.type }) -join ','
$results.functionAppliancePrefixCount = $options['function_appliance'].prefixWords.Count
$results.functionDoorPrefixCount = $options['function_door'].prefixWords.Count

# case 1 brand
$brandPrefix = Take-Words $options.brand.prefixWords 5
$brandIndustry = Take-Words $options.brand.industryWords 5
$brandSuffix = Take-Words $options.brand.suffixWords 5
$r1 = Preview 'it-brand' 'brand' @{ prefixWords=Items $brandPrefix; coreWords=Items @('小米') 'custom'; industryWords=Items $brandIndustry; suffixWords=Items $brandSuffix }
$results.case1 = @{ expected=125; total=$r1.totalAvailable; len=$r1.keywords.Count; sample=$r1.keywords[0] }

# case 2 decision with area
$decPrefix = Take-Words $options.decision.prefixWords 3
$decIndustry = Take-Words $options.decision.industryWords 3
$decSuffix = Take-Words $options.decision.suffixWords 3
$r2 = Preview 'it-decision-area' 'decision' @{ areaWords=Items @('北京','上海','深圳') 'custom'; prefixWords=Items $decPrefix; coreWords=Items @('小米') 'custom'; industryWords=Items $decIndustry; suffixWords=Items $decSuffix } @{ areaEnabled=$true }
$results.case2 = @{ expected=81; total=$r2.totalAvailable; len=$r2.keywords.Count; sample=$r2.keywords[0] }

# case 3 transaction suffix only
$transSuffix = Take-Words $options.transaction.suffixWords 12
$r3 = Preview 'it-transaction-suffix' 'transaction' @{ coreWords=Items @('钉钉') 'custom'; suffixWords=Items $transSuffix }
$results.case3 = @{ expected=12; total=$r3.totalAvailable; len=$r3.keywords.Count; samples=@($r3.keywords | Select-Object -First 3) }

# case 4 comparison normal
$compareWords = Take-Words $options.comparison.compareWords 6
$compareSuffix = Take-Words $options.comparison.suffixWords 5
$r4 = Preview 'it-comparison-normal' 'comparison' @{ coreWordsA=Items @('小米','华为') 'custom'; compareWords=Items $compareWords; coreWordsB=Items @('苹果','三星') 'custom'; suffixWords=Items $compareSuffix }
$results.case4 = @{ expected=120; total=$r4.totalAvailable; len=$r4.keywords.Count; samples=@($r4.keywords | Select-Object -First 4) }

# case 5 comparison > 1000 backend OOM defense
$core8a = Items @('A1','A2','A3','A4','A5','A6','A7','A8') 'custom'
$core8b = Items @('B1','B2','B3','B4','B5','B6','B7','B8') 'custom'
$r5 = Preview 'it-comparison-over' 'comparison' @{ coreWordsA=$core8a; compareWords=Items $compareWords; coreWordsB=$core8b; suffixWords=Items $compareSuffix }
$results.case5 = @{ expected=1920; total=$r5.totalAvailable; len=$r5.keywords.Count; error=$r5.error }

# case 6 qa
$qaPrefix = Take-Words $options.qa.prefixWords 5
$qaSuffix = Take-Words $options.qa.suffixWords 8
$r6 = Preview 'it-qa' 'qa' @{ prefixWords=Items $qaPrefix; coreWords=Items @('双开门冰箱') 'custom'; suffixWords=Items $qaSuffix }
$results.case6 = @{ expected=40; total=$r6.totalAvailable; len=$r6.keywords.Count; samples=@($r6.keywords | Select-Object -First 5) }

# case 7 function appliance
$funcPrefix = Take-Words $options.function_appliance.prefixWords 5
$funcSuffix = Take-Words $options.function_appliance.suffixWords 5
$r7 = Preview 'it-function-appliance' 'function' @{ prefixWords=Items $funcPrefix; coreWords=Items @('双开门冰箱','洗衣机','烤箱','微波炉','吸油烟机') 'custom'; suffixWords=Items $funcSuffix } @{ areaEnabled=$true; functionIndustryTag='appliance' }
$applianceBad = @($options.function_appliance.prefixWords | Where-Object { $_.industryTag -and $_.industryTag -ne 'common' -and $_.industryTag -ne 'appliance' } | ForEach-Object { $_.wordText })
$results.case7 = @{ expected=125; total=$r7.totalAvailable; len=$r7.keywords.Count; prefixCount=$options.function_appliance.prefixWords.Count; badIndustryWords=$applianceBad; samples=@($r7.keywords | Select-Object -First 4) }

# case 8 options basis for industry switch
$common = @($options.function_appliance.prefixWords | Where-Object { !$_.industryTag -or $_.industryTag -eq 'common' } | ForEach-Object { $_.wordText })
$applianceOnly = @($options.function_appliance.prefixWords | Where-Object { $_.industryTag -eq 'appliance' } | ForEach-Object { $_.wordText })
$doorOnly = @($options.function_door.prefixWords | Where-Object { $_.industryTag -eq 'door_window' } | ForEach-Object { $_.wordText })
$selectedBefore = @(
  @($options.function_appliance.prefixWords | Where-Object { !$_.industryTag -or $_.industryTag -eq 'common' } | Select-Object -First 2 | ForEach-Object { $_.wordText })
  @($options.function_appliance.prefixWords | Where-Object { $_.industryTag -eq 'appliance' } | Select-Object -First 3 | ForEach-Object { $_.wordText })
)
$doorAvailableCommon = [System.Collections.Generic.HashSet[string]]::new()
$options.function_door.prefixWords | Where-Object { !$_.industryTag -or $_.industryTag -eq 'common' } | ForEach-Object { [void]$doorAvailableCommon.Add($_.wordText) }
$selectedAfter = @($selectedBefore | Where-Object { $doorAvailableCommon.Contains($_) })
$results.case8 = @{ before=$selectedBefore; after=$selectedAfter; applianceOnly=$applianceOnly; doorOnly=$doorOnly; doorPrefixCount=$options.function_door.prefixWords.Count }

# case 10 legacy search detail existence
$page = (Get-Api '/keyword-groups?current=1&size=20&type=search').data
$searchId = if ($page.records.Count -gt 0) { $page.records[0].id } else { $null }
if ($searchId) { $detail = (Get-Api "/keyword-groups/$searchId").data; $results.case10 = @{ searchId=$searchId; type=$detail.type; legacyType=$detail.legacyType; typeLabel=$detail.typeLabel } } else { $results.case10 = @{ searchId=$null } }

# case 11 structured/prefixed error code fallback: missing coreB
$r11 = Preview 'it-error-code' 'comparison' @{ coreWordsA=Items @('小米') 'custom'; compareWords=Items @('和'); suffixWords=Items @('哪个更好') }
$results.case11 = @{ error=$r11.error }

$results | ConvertTo-Json -Depth 10
