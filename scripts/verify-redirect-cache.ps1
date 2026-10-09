param([string]$BaseUrl = 'http://localhost:8080')

# Requires an already built and running Compose stack with redirect caching enabled.
# Briefly recreates the backend and pauses/stops Redis. No database volumes are removed.
# Leaves one synthetic account and two synthetic links in the development database.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(15)

function Invoke-Docker {
  param([string[]]$Arguments)
  $output = & docker @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Docker failed: $($Arguments -join ' ')" }
  return $output
}

function Invoke-Api {
  param([string]$Method, [string]$Path, [int]$Status, [object]$Body = $null, [string]$Token = '')
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
  if ($null -ne $Body) {
    $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json')
  }
  if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
  try {
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    try {
      $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      if ([int]$response.StatusCode -ne $Status) { throw "$Method $Path returned $([int]$response.StatusCode), expected $Status" }
      return @{ Content = $content; Location = [string]$response.Headers.Location; CacheControl = [string]$response.Headers.CacheControl }
    } finally { $response.Dispose() }
  } finally { $request.Dispose() }
}

function Get-CacheKeys {
  param([string]$Code)
  return @(Invoke-Docker -Arguments @('compose', 'exec', '-T', 'redis', 'redis-cli', '--scan', '--pattern', "shortener:redirect:v1:*:$Code"))
}

function Assert-Redirect {
  param([string]$Code, [string]$Destination)
  $response = Invoke-Api -Method GET -Path "/r/$Code" -Status 302
  if ($response.Location -ne $Destination -or $response.CacheControl -ne 'no-store') { throw 'Redirect headers changed' }
}

$redisPaused = $false
$redisStopped = $false
try {
  $id = [Guid]::NewGuid().ToString('N').Substring(0, 12)
  $account = @{ email = "cache-$id@ex.test"; password = 'cache-test-2026' }
  Invoke-Api -Method POST -Path '/api/v1/auth/register' -Status 201 -Body $account | Out-Null
  $login = (Invoke-Api -Method POST -Path '/api/v1/auth/login' -Status 200 -Body $account).Content | ConvertFrom-Json
  $personal = (Invoke-Api -Method POST -Path '/api/v1/links' -Status 201 -Token $login.accessToken -Body @{
    originalUrl = 'https://example.com/cache-old'; alias = "Cache-$id"
  }).Content | ConvertFrom-Json
  Assert-Redirect $personal.shortCode $personal.originalUrl
  $oldKeys = @(Get-CacheKeys $personal.shortCode)
  if ($oldKeys.Count -ne 1) { throw 'First redirect did not populate Redis' }
  $ttl = [long](Invoke-Docker -Arguments @('compose', 'exec', '-T', 'redis', 'redis-cli', 'PTTL', $oldKeys[0]))
  if ($ttl -le 0) { throw 'Cache entry has no positive TTL' }

  Invoke-Docker -Arguments @('compose', 'pause', 'redis') | Out-Null
  $redisPaused = $true
  Invoke-Api -Method DELETE -Path "/api/v1/links/$($personal.shortCode)" -Status 204 -Token $login.accessToken | Out-Null
  Invoke-Api -Method GET -Path "/r/$($personal.shortCode)" -Status 404 | Out-Null
  Invoke-Docker -Arguments @('compose', 'unpause', 'redis') | Out-Null
  $redisPaused = $false
  # A timed-out Redis DEL may still execute after unpause; either result is safe.
  Invoke-Api -Method GET -Path "/r/$($personal.shortCode)" -Status 404 | Out-Null
  Invoke-Api -Method POST -Path '/api/v1/links' -Status 201 -Token $login.accessToken -Body @{
    originalUrl = 'https://example.com/cache-new'; alias = $personal.shortCode
  } | Out-Null
  Assert-Redirect $personal.shortCode 'https://example.com/cache-new'
  $reusedKeys = @(Get-CacheKeys $personal.shortCode)
  if (@($reusedKeys | Where-Object { $_ -notin $oldKeys }).Count -lt 1) { throw 'Outage did not abandon the old namespace' }

  $anonymous = (Invoke-Api -Method POST -Path '/api/v1/links' -Status 201 -Body @{
    originalUrl = 'https://example.com/cache-startup'
  }).Content | ConvertFrom-Json
  Assert-Redirect $anonymous.shortCode $anonymous.originalUrl
  $beforeRestart = @(Get-CacheKeys $anonymous.shortCode)
  Invoke-Docker -Arguments @('compose', 'up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '180', 'backend') | Out-Null
  # Refresh Nginx's backend address after container recreation.
  Invoke-Docker -Arguments @('compose', 'exec', '-T', 'frontend', 'nginx', '-s', 'reload') | Out-Null
  Assert-Redirect $anonymous.shortCode $anonymous.originalUrl
  $afterRestart = @(Get-CacheKeys $anonymous.shortCode)
  if ($afterRestart.Count -le $beforeRestart.Count) { throw 'Backend restart reused the old namespace' }

  Invoke-Docker -Arguments @('compose', 'stop', 'redis') | Out-Null
  $redisStopped = $true
  Invoke-Docker -Arguments @('compose', 'up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '180', 'backend') | Out-Null
  Invoke-Docker -Arguments @('compose', 'exec', '-T', 'frontend', 'nginx', '-s', 'reload') | Out-Null
  Assert-Redirect $anonymous.shortCode $anonymous.originalUrl
  Invoke-Docker -Arguments @('compose', 'exec', '-T', 'backend', 'curl', '--fail', '--silent', 'http://localhost:8080/actuator/health') | Out-Null
  Invoke-Docker -Arguments @('compose', 'start', 'redis') | Out-Null
  $redisStopped = $false
  Assert-Redirect $anonymous.shortCode $anonymous.originalUrl
  Write-Output 'Redis TTL, outage deletion, alias reuse, backend restart and startup without Redis passed through Nginx.'
} finally {
  if ($redisPaused) { Invoke-Docker -Arguments @('compose', 'unpause', 'redis') | Out-Null }
  if ($redisStopped) { Invoke-Docker -Arguments @('compose', 'start', 'redis') | Out-Null }
  $client.Dispose()
  $handler.Dispose()
}
