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
    $env:ANDROID_HOME = $SdkRoot
    Invoke-Checked 'gradle' @('--no-daemon','assembleRelease')
    $unsigned = @(Get-ChildItem 'build/outputs/apk/release/*-unsigned.apk' -File)
    if ($unsigned.Count -ne 1) { throw 'No se encontró una única APK de lanzamiento sin firmar.' }
    $apk = 'build/Ruta-Panel-1.2.0.apk'
    Invoke-Checked "$bt\apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass','env:RUTA_KEY_PASSWORD','--ks-key-alias','ruta','--out',$apk,$unsigned[0].FullName)
    Invoke-Checked "$bt\apksigner.bat" @('verify','--verbose',$apk)
    Write-Output "APK compilada: $apk"
} finally {
    Remove-Item Env:\RUTA_KEY_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle)
}
