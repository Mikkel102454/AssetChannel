param(
    [switch]$Serve,
    [switch]$SkipApi,
    [int]$Port = 8000,
    [string]$Python
)
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath($PSScriptRoot)
if (!$Python) {
    $command = Get-Command python -ErrorAction SilentlyContinue
    if ($command -and $command.Source -notlike '*WindowsApps*') {
        $Python = $command.Source
    } else {
        $runtimeRoot = Join-Path $env:LOCALAPPDATA 'Python'
        if (Test-Path -LiteralPath $runtimeRoot) {
            $runtimes = Get-ChildItem -LiteralPath $runtimeRoot -Directory |
                Where-Object { $_.Name -match '^pythoncore-(\d+\.\d+)-64$' } |
                Sort-Object { [version]($_.Name -replace '^pythoncore-|-64$', '') } -Descending
            foreach ($runtime in $runtimes) {
                $candidate = Join-Path $runtime.FullName 'python.exe'
                if (Test-Path -LiteralPath $candidate) { $Python = $candidate; break }
            }
        }
    }
}
if (!$Python) { throw 'Python was not found. Install Python or pass -Python <python.exe>.' }
Push-Location $projectPath
try {
    if (!$SkipApi) {
        & (Join-Path $projectPath 'gradlew.bat') stageApiDocs
        if ($LASTEXITCODE -ne 0) { throw 'API documentation generation failed.' }
    }
    & $Python -m mkdocs build --strict
    if ($LASTEXITCODE -ne 0) { throw 'MkDocs failed. Install docs-requirements.txt in this Python environment and check the reported links.' }
    if ($Serve) {
        & $Python -m http.server $Port --bind 127.0.0.1 --directory site
        if ($LASTEXITCODE -ne 0) { throw 'Documentation preview server failed.' }
    }
} finally {
    Pop-Location
}
