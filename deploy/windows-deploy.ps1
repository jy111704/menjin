param(
    [string]$AppDir = "C:\campus-vehicle",
    [int]$Port = 8088,
    [string]$ReleaseTag = ""
)

$ErrorActionPreference = "Stop"
$taskName = "CampusVehicle"
$AppDir = [System.IO.Path]::GetFullPath($AppDir)
$jar = [System.IO.Path]::GetFullPath((Join-Path $AppDir "campus-vehicle.jar"))
$newJar = [System.IO.Path]::GetFullPath((Join-Path $AppDir "campus-vehicle.jar.new"))
$stdout = [System.IO.Path]::GetFullPath((Join-Path $AppDir "backend.out.log"))
$stderr = [System.IO.Path]::GetFullPath((Join-Path $AppDir "backend.err.log"))
$startPs1 = [System.IO.Path]::GetFullPath((Join-Path $AppDir "start-campus.ps1"))

function Write-Log([string]$Message) {
    Write-Output ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message)
}

function Invoke-Schtasks([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & schtasks.exe @Arguments 2>&1 | Out-Null
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Assert-JarFile([string]$Path, [long]$ExpectedSize, [string]$ExpectedHash) {
    if (-not (Test-Path $Path)) {
        throw "file not found: $Path"
    }
    $actualSize = (Get-Item $Path).Length
    if ($ExpectedSize -gt 0 -and $actualSize -ne $ExpectedSize) {
        throw "size mismatch: $actualSize / $ExpectedSize"
    }
    if ($ExpectedHash) {
        $actualHash = (Get-FileHash -Path $Path -Algorithm SHA256).Hash.ToLower()
        if ($actualHash -ne $ExpectedHash) {
            throw "sha256 mismatch"
        }
    }
}

New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $AppDir "logs") | Out-Null

if ($ReleaseTag) {
    $repo = "jy111704/menjin"
    $releaseUri = "https://api.github.com/repos/$repo/releases/tags/$ReleaseTag"
    $apiHeaders = @{
        "User-Agent" = "campus-vehicle-deploy"
        "Accept"     = "application/vnd.github+json"
    }
    Write-Log "fetching release $ReleaseTag"
    $release = Invoke-RestMethod -Uri $releaseUri -Headers $apiHeaders -TimeoutSec 60
    $asset = @($release.assets | Where-Object { $_.name -eq "campus-vehicle.jar" })[0]
    if (-not $asset) {
        throw "release asset campus-vehicle.jar not found in $ReleaseTag"
    }

    $expectedSize = [long]$asset.size
    $expectedHash = ""
    if ($asset.digest -and $asset.digest.StartsWith("sha256:")) {
        $expectedHash = $asset.digest.Substring(7).ToLower()
    }

    # The server is in China; direct GitHub downloads are very slow. Try a
    # public GitHub proxy first, then fall back to the API asset endpoint.
    $proxyUrls = @(
        "https://gh-proxy.com/$($asset.browser_download_url)",
        "https://ghfast.top/$($asset.browser_download_url)"
    )
    $downloaded = $false
    foreach ($url in $proxyUrls) {
        try {
            if (Test-Path $newJar) {
                Remove-Item $newJar -Force -ErrorAction SilentlyContinue
            }
            Write-Log "downloading $($asset.name) via $url"
            & curl.exe -L --fail --retry 2 --connect-timeout 15 --max-time 600 -sS -o $newJar $url
            if ($LASTEXITCODE -ne 0) {
                throw "curl exit code $LASTEXITCODE"
            }
            Assert-JarFile -Path $newJar -ExpectedSize $expectedSize -ExpectedHash $expectedHash
            $downloaded = $true
            Write-Log "downloaded and verified $newJar"
            break
        } catch {
            Write-Log "download failed: $($_.Exception.Message)"
        }
    }

    if (-not $downloaded) {
        Write-Log "proxy download failed, falling back to GitHub API asset"
        $downloadHeaders = @{
            "User-Agent" = "campus-vehicle-deploy"
            "Accept"     = "application/octet-stream"
        }
        if (Test-Path $newJar) {
            Remove-Item $newJar -Force -ErrorAction SilentlyContinue
        }
        Invoke-WebRequest -UseBasicParsing `
            -Uri "https://api.github.com/repos/$repo/releases/assets/$($asset.id)" `
            -Headers $downloadHeaders -OutFile $newJar -TimeoutSec 600
        Assert-JarFile -Path $newJar -ExpectedSize $expectedSize -ExpectedHash $expectedHash
        $downloaded = $true
        Write-Log "downloaded and verified $newJar"
    }

    if (-not $downloaded) {
        throw "release download failed"
    }
}

if (-not (Test-Path $newJar)) {
    throw "new jar not found: $newJar"
}

# Optional server-side overrides, one KEY=VALUE per line.
# Example: SPRING_DATASOURCE_PASSWORD=your-password
$envFile = Join-Path $AppDir "config.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), "Process")
        }
    }
    Write-Log "loaded config.env"
}

# Stop the scheduled task and any running campus-vehicle Java process.
$null = Invoke-Schtasks @("/End", "/TN", $taskName)
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*campus-vehicle.jar*" } |
    ForEach-Object {
        try {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
            Write-Log "stopped java pid $($_.ProcessId)"
        } catch {
            Write-Log "stop java pid $($_.ProcessId) failed: $($_.Exception.Message)"
        }
    }
Start-Sleep -Seconds 3

# Replace the jar, keeping one previous copy for rollback.
if (Test-Path $jar) {
    Copy-Item $jar "$jar.bak" -Force
    Remove-Item $jar -Force
}
Move-Item $newJar $jar -Force

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    throw "java not found in PATH; install JDK 17 on the server first"
}
$java = $javaCommand.Source

# Wrapper loaded by the scheduled task. It reads config.env itself so the
# values also apply when the task starts after a reboot.
$startContent = @"
`$ErrorActionPreference = "Stop"
`$AppDir = "$AppDir"
Set-Location `$AppDir
`$envFile = Join-Path `$AppDir "config.env"
if (Test-Path `$envFile) {
    Get-Content `$envFile | ForEach-Object {
        `$line = `$_.Trim()
        if (`$line -and -not `$line.StartsWith("#") -and `$line.Contains("=")) {
            `$key, `$value = `$line.Split("=", 2)
            [Environment]::SetEnvironmentVariable(`$key.Trim(), `$value.Trim(), "Process")
        }
    }
}
& "$java" -jar "$jar" --server.port=$Port *>> "$stdout"
"@
Set-Content -Path $startPs1 -Value $startContent -Encoding UTF8

# Open the application ports for external access.
$firewallPorts = @($Port)
if ($env:CAMPUS_HTTPS_ENABLED -eq "true" -and $env:CAMPUS_HTTPS_PORT) {
    try {
        $firewallPorts += [int]$env:CAMPUS_HTTPS_PORT
    } catch {
        Write-Log "invalid CAMPUS_HTTPS_PORT: $($env:CAMPUS_HTTPS_PORT)"
    }
}
foreach ($fp in ($firewallPorts | Select-Object -Unique)) {
    try {
        if (-not (Get-NetFirewallRule -DisplayName "CampusVehicle $fp" -ErrorAction SilentlyContinue)) {
            New-NetFirewallRule -DisplayName "CampusVehicle $fp" -Direction Inbound -Protocol TCP -LocalPort $fp -Action Allow | Out-Null
            Write-Log "firewall rule created for port $fp"
        }
    } catch {
        Write-Log "firewall rule skipped for port $fp : $($_.Exception.Message)"
    }
}

# Prefer a scheduled task so the backend survives SSH disconnect and reboot.
$taskCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File $startPs1"
$createCode = Invoke-Schtasks @("/Create", "/TN", $taskName, "/TR", $taskCommand, "/SC", "ONSTART", "/RU", "SYSTEM", "/RL", "HIGHEST", "/F")
$taskOk = ($createCode -eq 0)
if (-not $taskOk) {
    Write-Log "scheduled task creation failed with exit code $createCode"
}

if ($taskOk) {
    Write-Log "starting via scheduled task $taskName"
    $null = Invoke-Schtasks @("/Run", "/TN", $taskName)
} else {
    Write-Log "starting as a detached process"
    $javaArgs = @("-jar", $jar, "--server.port=$Port")
    Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $AppDir -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null
}

# Wait for the health endpoint.
$healthy = $false
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/api/health" -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            $healthy = $true
            break
        }
    } catch {
        # keep waiting
    }
}

if (-not $healthy) {
    Write-Log "health check failed on port $Port"
    if (Test-Path $stderr) {
        Write-Log "---- stderr ----"
        Get-Content $stderr -Tail 80
    }
    if (Test-Path $stdout) {
        Write-Log "---- stdout ----"
        Get-Content $stdout -Tail 80
    }
    exit 1
}

Write-Log "deploy ok: http://127.0.0.1:$Port/api/health"

if ($env:CAMPUS_HTTPS_ENABLED -eq "true" -and $env:CAMPUS_HTTPS_PORT) {
    $httpsPort = [int]$env:CAMPUS_HTTPS_PORT
    $httpsHealthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        $code = & curl.exe -k -sS -o NUL -w "%{http_code}" --max-time 3 "https://127.0.0.1:$httpsPort/api/health"
        if ("$code".Trim() -eq "200") {
            $httpsHealthy = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $httpsHealthy) {
        Write-Log "HTTPS health check failed on port $httpsPort"
        if (Test-Path $stderr) {
            Get-Content $stderr -Tail 80
        }
        exit 1
    }
    Write-Log "https ok: https://127.0.0.1:$httpsPort/api/health"
}
