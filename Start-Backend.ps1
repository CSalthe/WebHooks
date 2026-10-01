$ErrorActionPreference = 'Stop'
$taskJdk = Get-ChildItem "$PSScriptRoot\.tools" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName; $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install JDK 21+ and set JAVA_HOME, then run this script again.' }
Push-Location "$PSScriptRoot\backend"
try { & .\mvnw.cmd spring-boot:run; if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error.' } } finally { Pop-Location }
