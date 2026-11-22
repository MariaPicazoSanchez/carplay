param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [Parameter(Mandatory = $true)][string]$KeyStore
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$bt = Join-Path $SdkRoot 'build-tools\35.0.0'
$androidJar = Join-Path $SdkRoot 'platforms\android-35\android.jar'
if (-not (Test-Path $androidJar)) { throw 'Instala Android SDK Platform 35 y Build Tools 35.0.0 desde Android Studio.' }
if (-not (Test-Path $KeyStore)) { throw 'No se encuentra el almacén privado de firma.' }
$KeyStore = (Resolve-Path $KeyStore).Path
function Invoke-Checked([string]$File, [string[]]$ArgumentList) {
    & $File @ArgumentList
    if ($LASTEXITCODE -ne 0) { throw "Ha fallado $File (código $LASTEXITCODE)." }
}
$securePassword = Read-Host 'Contraseña del almacén de firma (alias ruta)' -AsSecureString
$handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:RUTA_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle)
    if (Test-Path 'build') { Remove-Item -Recurse -Force 'build' }
    New-Item -ItemType Directory -Force 'build\classes', 'build\dex' | Out-Null
    Invoke-Checked "$bt\aapt2.exe" @('compile','--dir','res','-o','build\resources.zip')
    Invoke-Checked "$bt\aapt2.exe" @('link','-o','build\base.apk','-I',$androidJar,'--manifest','AndroidManifest.xml','--java','build\generated','build\resources.zip')
    $sourceLines = Get-ChildItem 'src','build\generated' -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'build\sources.txt'), [string[]]$sourceLines, (New-Object Text.UTF8Encoding($false)))
    Invoke-Checked 'java' @('com.sun.tools.javac.Main','-source','8','-target','8','-encoding','UTF-8','-bootclasspath',"$androidJar;$bt\core-lambda-stubs.jar",'-d','build\classes','@build/sources.txt')
    Invoke-Checked 'java' @('sun.tools.jar.Main','--create','--file','build\classes.jar','-C','build\classes','.')
    Invoke-Checked "$bt\d8.bat" @('--min-api','26','--lib',$androidJar,'--output','build\dex','build\classes.jar')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open((Join-Path $PSScriptRoot 'build\base.apk'), [IO.Compression.ZipArchiveMode]::Update)
    try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $PSScriptRoot 'build\dex\classes.dex'), 'classes.dex') | Out-Null } finally { $zip.Dispose() }
    Invoke-Checked "$bt\zipalign.exe" @('-f','-p','4','build\base.apk','build\aligned.apk')
    Invoke-Checked "$bt\apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass','env:RUTA_KEY_PASSWORD','--ks-key-alias','ruta','--out','build\Ruta-Panel-1.0.0.apk','build\aligned.apk')
    Invoke-Checked "$bt\apksigner.bat" @('verify','--verbose','build\Ruta-Panel-1.0.0.apk')
    Write-Output 'APK compilada: build\Ruta-Panel-1.0.0.apk'
} finally {
    Remove-Item Env:\RUTA_KEY_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle)
}
