$nodePath = "C:\dev\runtimes\node22"

if (-not (Test-Path "$nodePath\node.exe")) {
    throw "Node 22 not found at $nodePath"
}

$env:Path = "$nodePath;$env:Path"

Write-Host "Node: $(node -v)"
Write-Host "npm:  $(npm -v)"