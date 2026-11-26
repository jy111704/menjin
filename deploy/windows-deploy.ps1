param(
    [string]$AppDir = "C:\campus-vehicle",
    [int]$Port = 80888,
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

    Write-Log "downloading $($asset.name) ($($asset.size) bytes)"
    $downloadHeaders = @{
        "User-Agent" = "campus-vehicle-deploy"
        "Accept"     = "application/octet-stream"
    }
    Invoke-WebRequest -UseBasicParsing `
        -Uri "https://api.github.com/repos/$repo/releases/assets/$($asset.id)" `
        -Headers $downloadHeaders -OutFile $newJar -TimeoutSec 900
    Write-Log "downloaded to $newJar"
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
& schtasks.exe /End /TN $taskName 2>$null | Out-Null
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

# Open the application port for external access.
try {
    if (-not (Get-NetFirewallRule -DisplayName "CampusVehicle $Port" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "CampusVehicle $Port" -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow | Out-Null
        Write-Log "firewall rule created for port $Port"
    }
} catch {
    Write-Log "firewall rule skipped: $($_.Exception.Message)"
}

# Prefer a scheduled task so the backend survives SSH disconnect and reboot.
$taskCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File $startPs1"
$taskOk = $false
try {
    & schtasks.exe /Create /TN $taskName /TR $taskCommand /SC ONSTART /RU SYSTEM /RL HIGHEST /F 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $taskOk = $true
    }
} catch {
    Write-Log "scheduled task creation failed: $($_.Exception.Message)"
}

if ($taskOk) {
    Write-Log "starting via scheduled task $taskName"
    & schtasks.exe /Run /TN $taskName 2>&1 | Out-Null
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
