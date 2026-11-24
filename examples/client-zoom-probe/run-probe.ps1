$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'run-native-probe.ps1')
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$libraryPath = Join-Path $env:APPDATA '.minecraft/libraries/org/lwjgl'
$jars = foreach ($module in @('lwjgl','lwjgl-glfw','lwjgl-opengl')) {
    Get-ChildItem -LiteralPath (Join-Path $libraryPath ($module + '/3.3.3')) -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'arm64|x86' } | Select-Object -ExpandProperty FullName
}
$classPath = $jars -join ';'
$classes = Join-Path $projectPath 'build/reports/client-zoom-probe'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$jdk = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot/bin'
& (Join-Path $jdk 'javac.exe') -cp $classPath -d $classes (Join-Path $PSScriptRoot 'ClientZoomProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'GPU probe compilation failed' }
& (Join-Path $jdk 'java.exe') --enable-native-access=ALL-UNNAMED -cp ($classPath + ';' + $classes) ClientZoomProbe (Join-Path $projectPath 'examples/cinematic-pack/assets/cinematic/shaders/post') (Join-Path $env:APPDATA '.minecraft/versions/26.3/26.3.jar')
if ($LASTEXITCODE -ne 0) { throw 'GPU probe failed' }
