param(
    [string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$SdkDirectory = $env:ANDROID_HOME,
    [string]$JavaDirectory = ''
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $Program (exit $LASTEXITCODE)" }
}
if (-not $JavaDirectory) {
    $javaCandidates = @($env:JAVA_HOME)
    $jdkParent = 'C:\Program Files\Eclipse Adoptium'
    if (Test-Path -LiteralPath $jdkParent) {
        $javaCandidates += @(Get-ChildItem -LiteralPath $jdkParent -Directory | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
    }
    $JavaDirectory = $javaCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
}
if (-not $JavaDirectory) { throw 'JDK belum tersedia. Jalankan winget install --id EclipseAdoptium.Temurin.21.JDK -e, lalu ulangi script ini.' }
$env:JAVA_HOME = $JavaDirectory
$env:PATH = (Join-Path $JavaDirectory 'bin') + ';' + $env:PATH
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
if (-not $SdkDirectory) { $SdkDirectory = $env:ANDROID_SDK_ROOT }
if (-not $SdkDirectory) { throw 'Set ANDROID_HOME or pass -SdkDirectory.' }
$sdkDir = [IO.Path]::GetFullPath($SdkDirectory)
if (-not (Test-Path "$sdkDir/platforms/android-34/android.jar")) { throw 'Install platforms;android-34 and build-tools;34.0.0 with Android SDK Manager first.' }
New-Item -ItemType Directory -Force -Path "$ProjectRoot/.local", "$ProjectRoot/dist" | Out-Null
$buildTools = Join-Path $sdkDir 'build-tools\34.0.0'
$androidJar = Join-Path $sdkDir 'platforms\android-34\android.jar'
$launcherDir = Join-Path $ProjectRoot 'launcher'
$buildDir = Join-Path (Join-Path $ProjectRoot '.local') ('build-windows-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildDir,"$buildDir\gen","$buildDir\classes","$buildDir\dex" -Force | Out-Null
Push-Location $launcherDir
try {
    Run-Checked -Program "$buildTools\aapt2.exe" -Arguments @('compile','--dir','res','-o',"$buildDir\res.zip")
    Run-Checked -Program "$buildTools\aapt2.exe" -Arguments @('link','-o',"$buildDir\base.apk",'-I',$androidJar,'--manifest','AndroidManifest.xml','--java',"$buildDir\gen",'-A','assets','--min-sdk-version','26','--target-sdk-version','34',"$buildDir\res.zip")
    $javaSources = @(Get-ChildItem -LiteralPath 'src',"$buildDir\gen" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    $compileArguments = @('-parameters','-encoding','UTF-8','--release','11','-classpath',$androidJar,'-d',"$buildDir\classes") + $javaSources
    Run-Checked -Program "$JavaDirectory\bin\javac.exe" -Arguments $compileArguments
    $classJar = "$buildDir\classes.jar"
    Run-Checked -Program "$JavaDirectory\bin\jar.exe" -Arguments @('cf',$classJar,'-C',"$buildDir\classes",'.')
    Run-Checked -Program "$buildTools\d8.bat" -Arguments @('--min-api','26','--lib',$androidJar,'--output',"$buildDir\dex",$classJar)
    Copy-Item -LiteralPath "$buildDir\base.apk" -Destination "$buildDir\unsigned.apk"
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $apkZip = [System.IO.Compression.ZipFile]::Open("$buildDir\unsigned.apk", [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        # aapt2 on Windows can emit backslashes in nested asset entry names.
        # Android AssetManager requires forward slashes inside the APK.
        $badEntries = @($apkZip.Entries | Where-Object { $_.FullName.Contains('\') })
        foreach ($entry in $badEntries) {
            $normalized = $entry.FullName.Replace('\', '/')
            if ($apkZip.GetEntry($normalized)) { throw "Duplicate normalized APK entry: $normalized" }
            $replacement = $apkZip.CreateEntry($normalized)
            $inputStream = $entry.Open()
            $outputStream = $replacement.Open()
            try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
            $entry.Delete()
        }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkZip,"$buildDir\dex\classes.dex",'classes.dex') | Out-Null
    } finally { $apkZip.Dispose() }
    Run-Checked -Program "$buildTools\zipalign.exe" -Arguments @('-f','4',"$buildDir\unsigned.apk","$buildDir\aligned.apk")
    $keyFile = Join-Path $ProjectRoot '.local/debug.keystore'
    if (-not (Test-Path -LiteralPath $keyFile)) {
        Run-Checked -Program "$JavaDirectory\bin\keytool.exe" -Arguments @('-genkeypair','-keystore',$keyFile,'-storepass','android','-keypass','android','-alias','tvhome','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=TV Home Universal Development')
    }
    $resultApk = Join-Path $ProjectRoot 'dist/TV-Home-Universal-debug.apk'
    Run-Checked -Program "$buildTools\apksigner.bat" -Arguments @('sign','--ks',$keyFile,'--ks-pass','pass:android','--out',$resultApk,"$buildDir\aligned.apk")
    Run-Checked -Program "$buildTools\apksigner.bat" -Arguments @('verify','--verbose',$resultApk)
    Get-FileHash -LiteralPath $resultApk -Algorithm SHA256 | Format-List
    Write-Host "APK ready: $resultApk" -ForegroundColor Green
    Write-Host 'Build only. No TV changes. Keep .local/debug.keystore privately for future updates.'
} finally { Pop-Location }





