$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot\frontend"
try {
    if (-not (Test-Path node_modules)) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' } }
    & npm.cmd start
    if ($LASTEXITCODE -ne 0) { throw 'Frontend exited with an error.' }
} finally { Pop-Location }
