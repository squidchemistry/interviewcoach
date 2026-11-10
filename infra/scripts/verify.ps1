# Runs the full Phase verification: backend verify, frontend checks, docker compose up, smoke tests.
# Usage (from the repo root):  .\infra\scripts\verify.ps1  [-SkipDocker]
param([switch]$SkipDocker)
# Native tools (java, mvn, npm, docker) write progress to stderr; Windows PowerShell 5.1 would turn that into
# terminating errors under 'Stop'. Failures are detected from exit codes in Step instead.
$ErrorActionPreference = 'Continue'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')

function Step($name, [scriptblock]$block) {
    Write-Host "`n=== $name ===" -ForegroundColor Cyan
    & $block
    if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

# Java 25 is required for the backend build.
$javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1) -as [string]
if ($javaVersion -notmatch '"(\d+)') { throw 'Java not found. Install JDK 25 (e.g. winget install EclipseAdoptium.Temurin.25.JDK) and set JAVA_HOME.' }
if ([int]$Matches[1] -lt 25) { throw "JDK 25+ required, found: $javaVersion" }

Push-Location $root
try {
    Step 'Backend: mvnw verify (unit + integration + ArchUnit + coverage)' { Push-Location backend; .\mvnw.cmd -B -ntp verify; Pop-Location }
    Step 'Frontend: npm ci'      { Push-Location frontend; npm ci --no-audit --no-fund; Pop-Location }
    Step 'Frontend: lint'        { Push-Location frontend; npm run lint; Pop-Location }
    Step 'Frontend: typecheck'   { Push-Location frontend; npm run typecheck; Pop-Location }
    Step 'Frontend: test'        { Push-Location frontend; npm test; Pop-Location }
    Step 'Frontend: build'       { Push-Location frontend; npm run build; Pop-Location }

    if (-not $SkipDocker) {
        if (-not (Test-Path .env)) { Copy-Item .env.example .env; Write-Host 'Created .env from .env.example' }
        $envMap = @{}
        Get-Content .env | Where-Object { $_ -match '^\s*([A-Z0-9_]+)=(.*)$' } | ForEach-Object { $envMap[$Matches[1]] = $Matches[2] }
        $webPort = if ($envMap.WEB_PORT) { $envMap.WEB_PORT } else { '8088' }
        $apiPort = if ($envMap.API_PORT) { $envMap.API_PORT } else { '8080' }

        Step 'Docker: compose up --build --wait' { docker compose up --build -d --wait --wait-timeout 420 }
        Step 'Smoke: API health' {
            # curl.exe output can be several lines (an array in PowerShell); join before matching.
            $h = (curl.exe -fsS "http://127.0.0.1:$apiPort/api/v1/health") -join "`n"; Write-Host $h
            if ($h -notmatch '"status":"UP"') { throw 'API not UP' }
        }
        Step 'Smoke: web + /api proxy' {
            $p = (curl.exe -fsS "http://127.0.0.1:$webPort/api/v1/health") -join "`n"
            if ($p -notmatch '"status":"UP"') { throw 'nginx /api proxy not UP' }
            $html = (curl.exe -fsS "http://127.0.0.1:$webPort/") -join "`n"
            if ($html -notmatch 'id="root"') { throw 'SPA not served' }
            Write-Host "Web OK at http://localhost:$webPort"
        }
        if ($envMap.SEED_DEMO_DATA -eq 'true') {
            Step 'Smoke: demo login through nginx' {
                $body = (@{ email = 'demo@aicoach.dev'; password = $envMap.DEMO_USER_PASSWORD } | ConvertTo-Json -Compress)
                $tmp = New-TemporaryFile
                Set-Content -Path $tmp -Value $body -Encoding ascii
                $r = (curl.exe -fsS -X POST -H "Content-Type: application/json" --data-binary "@$tmp" "http://127.0.0.1:$webPort/api/v1/auth/login") -join "`n"
                Remove-Item $tmp
                if ($r -notmatch '"accessToken"') { throw 'Demo login failed' }
                Write-Host 'Demo login OK'
            }
        }
    }
    Write-Host "`nAll checks passed." -ForegroundColor Green
} finally {
    Pop-Location
}
