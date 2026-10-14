[CmdletBinding()]
param(
    [Parameter(Position = 0)][ValidateSet('up', 'status', 'logs', 'stop', 'down')][string]$Action = 'up',
    [ValidateSet('mysql', 'redis', 'rabbitmq', 'qdrant', 'minio', 'aftersales-server', 'ai-service', 'ai-consumer', 'web')][string]$Service
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install and start Docker Desktop first.' }
if ($Action -eq 'up') { & (Join-Path $PSScriptRoot 'init-demo.ps1') }
$configPath = Join-Path $PSScriptRoot '.env.demo'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Run deploy/init-demo.ps1 first.' }
$composeArgs = @('compose', '--project-name', 'aftersales-demo', '--env-file', $configPath,
    '-f', (Join-Path $PSScriptRoot 'compose.yml'), '-f', (Join-Path $PSScriptRoot 'compose.demo.yml'))
& docker @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed. Compose 2.24.4+ is required.' }
switch ($Action) {
    'up' { & docker @composeArgs up -d --build --wait --wait-timeout 300 }
    'status' { & docker @composeArgs ps --all }
    'logs' {
        if ($Service) { & docker @composeArgs logs --tail 100 -f $Service }
        else { & docker @composeArgs logs --tail 100 -f }
    }
    'stop' { & docker @composeArgs stop }
    'down' { & docker @composeArgs down }
}
if ($LASTEXITCODE -ne 0) { throw "Docker Compose '$Action' failed; inspect status and logs." }
if ($Action -eq 'up') {
    & docker @composeArgs ps
    $portLine = Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^WEB_PORT=' } | Select-Object -First 1
    $port = ($portLine -split '=', 2)[1]
    Write-Host "Demo ready: http://localhost:$port"
    Write-Host 'Accounts: customer01 / agent01 / admin01; password: Demo@123456'
}
