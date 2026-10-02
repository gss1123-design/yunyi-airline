param(
    [long]$FirstUserId = 900000,
    [int]$Count = 200,
    [string]$UsersCsv = "target/performance-results/booking-users.csv",
    [string]$OutputCsv = "target/performance-results/cancel-users.csv",
    [string]$MysqlContainer = "hakimi-airline-local-mysql-1"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$usersPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $UsersCsv))
$outputPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutputCsv))
$outputParent = Split-Path -Parent $outputPath
$lastUserId = $FirstUserId + $Count - 1

if (-not (Test-Path -LiteralPath $usersPath)) {
    throw "Generated booking users file not found: $usersPath"
}

$userRows = @(Import-Csv -LiteralPath $usersPath -Header userId, token, flightId)
$tokenByUser = @{}
foreach ($row in $userRows) {
    $tokenByUser[[long]$row.userId] = $row.token
}

$statusExpr = "CHAR(85,78,80,65,73,68)"
$sql = "SELECT user_id, id FROM ticket_order WHERE user_id BETWEEN $FirstUserId AND $lastUserId AND status=$statusExpr ORDER BY user_id"
$mysqlCommand = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B hajimi_aviation -e "' + $sql + '"'
$orderLines = @(docker exec $MysqlContainer sh -lc $mysqlCommand)
if ($LASTEXITCODE -ne 0) {
    throw "Could not read generated unpaid orders from MySQL container $MysqlContainer."
}

$cancelRows = [System.Collections.Generic.List[string]]::new()
foreach ($line in $orderLines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $parts = $line -split "`t"
    if ($parts.Count -ne 2) { throw "Unexpected MySQL order row format." }

    $userId = [long]$parts[0]
    if (-not $tokenByUser.ContainsKey($userId)) {
        throw "Order user $userId does not have a matching generated JWT."
    }
    $cancelRows.Add("$userId,$($tokenByUser[$userId]),$($parts[1])")
}

if ($cancelRows.Count -eq 0) {
    throw "No unpaid generated orders found for user IDs $FirstUserId-$lastUserId."
}

New-Item -ItemType Directory -Force -Path $outputParent | Out-Null
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($outputPath, $cancelRows, $utf8WithoutBom)
Write-Output "Generated $($cancelRows.Count) cancellation rows."
