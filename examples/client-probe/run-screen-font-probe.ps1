param([string]$AssetsPath)
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!$AssetsPath) { $AssetsPath = Join-Path $projectPath 'examples/cinematic-pack/sources/fonts/ui/subtitle.ttf' }
$minecraftPath = Join-Path $env:APPDATA '.minecraft'
$clientJar = Join-Path $minecraftPath 'versions/26.3/26.3.jar'
$version = [IO.File]::ReadAllText((Join-Path $minecraftPath 'versions/26.3/26.3.json')) | ConvertFrom-Json
$jars = @($clientJar) + @($version.libraries | ForEach-Object {
    $artifact = $_.downloads.artifact.path
    if ($artifact -and $artifact -notmatch 'natives-(linux|macos)|arm64|natives-windows-x86') {
        $file = Join-Path $minecraftPath ('libraries/' + $artifact)
        if (Test-Path -LiteralPath $file) { $file }
    }
})
$classPath = $jars -join ';'
$classes = Join-Path $projectPath 'build/reports/native-shader-probe'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$jdk = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot/bin'
& (Join-Path $jdk 'javac.exe') -cp $classPath -d $classes (Join-Path $PSScriptRoot 'NativeScreenFontProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Native screen font probe compilation failed' }
& (Join-Path $jdk 'java.exe') --enable-native-access=ALL-UNNAMED -cp ($classPath + ';' + $classes) NativeScreenFontProbe $AssetsPath $clientJar
if ($LASTEXITCODE -ne 0) { throw 'Minecraft screen font probe failed' }
