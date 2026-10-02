param(
    [long]$FirstUserId = 900000,
    [int]$Count = 200,
    [long]$FlightIdA = 25,
    [long]$FlightIdB = 26,
    [string]$OutputCsv = "target/performance-results/booking-users.csv",
    [string]$AppContainer = "hakimi-airline-local-app-1"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$targetDir = Join-Path $repoRoot "target"
$classDir = Join-Path $targetDir "jmeter-generator-classes"
$outputFullPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutputCsv))
$outputParent = Split-Path -Parent $outputFullPath
$sourceFile = Join-Path $PSScriptRoot "GenerateJMeterUsers.java"
$projectClasses = Join-Path $targetDir "classes"
$profile = [Environment]::GetFolderPath("UserProfile")
$m2Root = Join-Path $profile ".m2\repository"
$jjwtRoot = Join-Path $m2Root "io\jsonwebtoken\jjwt"
$jjwtJar = Get-ChildItem -LiteralPath $jjwtRoot -Filter "jjwt-*.jar" -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
$jacksonRoot = Join-Path $m2Root "com\fasterxml\jackson\core"
$jacksonJars = foreach ($artifact in @("jackson-core", "jackson-databind", "jackson-annotations")) {
    $artifactRoot = Join-Path $jacksonRoot $artifact
    $versionDir = Get-ChildItem -LiteralPath $artifactRoot -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if (-not $versionDir) { throw "Could not find $artifact in the local Maven repository." }
    Get-ChildItem -LiteralPath $versionDir.FullName -Filter "$artifact-*.jar" |
        Select-Object -First 1 -ExpandProperty FullName
}
$jaxbRoot = Join-Path $m2Root "javax\xml\bind\jaxb-api"
$jaxbJar = Get-ChildItem -LiteralPath $jaxbRoot -Filter "jaxb-api-*.jar" -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
$activationRoot = Join-Path $m2Root "javax\activation\javax.activation-api"
$activationJar = Get-ChildItem -LiteralPath $activationRoot -Filter "javax.activation-api-*.jar" -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName

if (-not (Test-Path -LiteralPath $projectClasses)) {
    throw "Build the project first so target/classes exists."
}
if (-not $jjwtJar) {
    throw "Could not find the project's jjwt jar in the local Maven repository."
}
if (-not $jaxbJar -or -not $activationJar) {
    throw "Could not find the javax JAXB compatibility jars required by jjwt 0.7.0."
}

New-Item -ItemType Directory -Force -Path $classDir, $outputParent | Out-Null
$compileClasspath = (@($projectClasses, $jjwtJar, $jaxbJar, $activationJar) + $jacksonJars) -join ";"
& javac -encoding UTF-8 -cp $compileClasspath -d $classDir $sourceFile
if ($LASTEXITCODE -ne 0) { throw "Could not compile the JMeter user generator." }

$oldJwtSecret = [Environment]::GetEnvironmentVariable("HAKIMI_JWT_SECRET", "Process")
$hadOldJwtSecret = $null -ne $oldJwtSecret
try {
    $containerEnvJson = docker inspect --format '{{json .Config.Env}}' $AppContainer
    if ($LASTEXITCODE -ne 0) { throw "Could not read app container configuration." }
    $jwtSetting = @($containerEnvJson | ConvertFrom-Json) |
        Where-Object { $_ -like "HAKIMI_JWT_SECRET=*" } |
        Select-Object -First 1
    if ($jwtSetting) {
        $env:HAKIMI_JWT_SECRET = $jwtSetting.Substring("HAKIMI_JWT_SECRET=".Length)
    }

    $runtimeClasspath = (@($projectClasses, $classDir, $jjwtJar, $jaxbJar, $activationJar) + $jacksonJars) -join ";"
    & java -cp $runtimeClasspath GenerateJMeterUsers $FirstUserId $Count $FlightIdA $FlightIdB $outputFullPath
    if ($LASTEXITCODE -ne 0) { throw "Could not generate JMeter users." }
}
finally {
    if ($hadOldJwtSecret) {
        $env:HAKIMI_JWT_SECRET = $oldJwtSecret
    } else {
        Remove-Item Env:HAKIMI_JWT_SECRET -ErrorAction SilentlyContinue
    }
    $oldJwtSecret = $null
}
