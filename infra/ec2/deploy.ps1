# Deploy from the laptop: build the five jars here, copy them to the server, rebuild and restart there.
#   .\infra\ec2\deploy.ps1 -Server 3.26.113.232 [-Key $HOME\.ssh\visionfit1.pem] [-SkipBuild]
# The server only needs Docker and a clone of this repo (infra/ec2/setup-server.sh does both).
param(
    [Parameter(Mandatory = $true)] [string] $Server,
    [string] $Key = "$HOME\.ssh\visionfit1.pem",
    [switch] $SkipBuild
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $root
$services = 'identity-service', 'profile-service', 'meal-service', 'analysis-service', 'gateway-service'

if (-not $SkipBuild) {
    Write-Host "== Building jars"
    & .\gradlew.bat -q bootJar
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
}
New-Item -ItemType Directory -Force dist | Out-Null
foreach ($s in $services) {
    Copy-Item "$s\build\libs\$s-0.0.1-SNAPSHOT.jar" "dist\$s.jar" -Force
}

Write-Host "== Copying jars to $Server"
ssh -i $Key "ubuntu@$Server" 'mkdir -p ~/visionfit-backend/dist'
scp -i $Key -q (Get-ChildItem dist\*.jar).FullName "ubuntu@${Server}:~/visionfit-backend/dist/"

Write-Host "== Updating and restarting on the server"
ssh -i $Key "ubuntu@$Server" 'cd ~/visionfit-backend && git pull -q --ff-only && docker compose -f docker-compose.prod.yml up -d --build --remove-orphans && docker image prune -f > /dev/null && docker compose -f docker-compose.prod.yml ps'
