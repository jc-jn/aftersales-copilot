[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)][int]$WebPort = 8088,
    [ValidateRange(1024, 65535)][int]$MinioPort = 9000
)
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '.env.demo'
if (Test-Path -LiteralPath $configPath) {
    Write-Host 'Keeping existing deploy/.env.demo.'
    return
}
if ($WebPort -eq $MinioPort) { throw 'WebPort and MinioPort must be different.' }
function New-DemoSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}
$lines = @(
    '# Local demo only. Generated once; do not commit or print this file.'
    "WEB_PORT=$WebPort"
    "MINIO_PORT=$MinioPort"
    'MYSQL_DATABASE=aftersales'
    'MYSQL_USER=aftersales'
    "MYSQL_PASSWORD=$(New-DemoSecret)"
    "MYSQL_ROOT_PASSWORD=$(New-DemoSecret)"
    "REDIS_PASSWORD=$(New-DemoSecret)"
    'RABBITMQ_USER=aftersales'
    "RABBITMQ_PASSWORD=$(New-DemoSecret)"
    'MINIO_ROOT_USER=aftersales-demo'
    "MINIO_ROOT_PASSWORD=$(New-DemoSecret)"
    'MINIO_BUCKET=aftersales-private'
    "JWT_SECRET=$(New-DemoSecret)"
    "AI_INTERNAL_SECRET=$(New-DemoSecret)"
    "JAVA_INTERNAL_SECRET=$(New-DemoSecret)"
    "CORS_ALLOWED_ORIGINS=http://localhost:$WebPort"
    "MINIO_PUBLIC_ENDPOINT=http://localhost:$MinioPort"
)
$stream = [IO.File]::Open($configPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes(($lines -join "`n") + "`n")
    $stream.Write($bytes, 0, $bytes.Length)
} finally { $stream.Dispose() }
Write-Host 'Created deploy/.env.demo with independent random credentials.'
