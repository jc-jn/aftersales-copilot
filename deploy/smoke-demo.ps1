[CmdletBinding()]
param([string]$BaseUrl)
$ErrorActionPreference = 'Stop'
if (-not $BaseUrl) {
    $configPath = Join-Path $PSScriptRoot '.env.demo'
    if (-not (Test-Path -LiteralPath $configPath)) { throw 'Run deploy/demo.ps1 up first.' }
    $portLine = Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^WEB_PORT=' } | Select-Object -First 1
    $BaseUrl = 'http://localhost:' + ($portLine -split '=', 2)[1]
}
$BaseUrl = $BaseUrl.TrimEnd('/')
function Assert-Status([string]$Path, [int]$Expected, [hashtable]$Headers = @{}) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 15
        $status = [int]$response.StatusCode
    } catch {
        if (-not $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
    }
    if ($status -ne $Expected) { throw "$Path returned $status, expected $Expected." }
    Write-Host "PASS $Path ($status)"
}
Assert-Status '/' 200
Assert-Status '/healthz' 200
Assert-Status '/api/v1/ping' 200
Assert-Status '/internal/v1/metrics' 404
Assert-Status '/actuator/prometheus' 404
Assert-Status '/api/v1/auth/me' 401
foreach ($account in @('customer01', 'agent01', 'admin01')) {
    $body = @{username=$account; password='Demo@123456'} | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 15
    if (-not $login.data.accessToken) { throw "Login failed: $account" }
    $headers = @{Authorization='Bearer ' + $login.data.accessToken}
    Assert-Status '/api/v1/auth/me' 200 $headers
    if ($account -eq 'admin01') { Assert-Status '/api/v1/admin/dashboard/overview' 200 $headers }
    else { Assert-Status '/api/v1/admin/dashboard/overview' 403 $headers }
    if ($account -eq 'agent01') {
        $queue = Invoke-RestMethod -Uri "$BaseUrl/api/v1/tickets" -Headers $headers -TimeoutSec 15
        foreach ($ticket in $queue.data) {
            if ($ticket.id -isnot [string]) { throw 'Ticket ID must be a JSON string to preserve browser precision.' }
            foreach ($suffix in @('', '/proposals', '/timeline')) {
                Assert-Status ("/api/v1/tickets/" + $ticket.id + $suffix) 200 $headers
            }
        }
    }
    Write-Host "PASS login $account"
}
Write-Host 'Demo smoke checks passed.'
