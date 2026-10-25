<#
.SYNOPSIS
    校验 release APK 的打包结果，作为发版闸门。

.DESCRIPTION
    这些都是「构建成功但包是坏的」才会暴露的问题，人眼看 log 看不到：
      - versionCode / versionName 与预期不符（忘记 +1 导致无法覆盖安装）
      - 没有签名，或签的不是自己的证书
      - `isShrinkResources` 把只在代码里按 id 引用的字符串裁掉了
      - 自适应图标资源被裁掉，桌面上显示空白
      - Application 子类没进 manifest（启动即崩）
      - 测试期依赖泄漏进运行期

    任何一项失败都以非 0 退出，可以直接放进 CI。

.EXAMPLE
    pwsh ./scripts/verify-release.ps1
    pwsh ./scripts/verify-release.ps1 -Apk app/build/outputs/apk/release/app-release.apk -ExpectedVersion 3.0
#>
[CmdletBinding()]
param(
    [string]$Apk = 'app/build/outputs/apk/release/app-release.apk',
    [string]$ExpectedVersion,
    [string]$ExpectedVersionCode
)

$ErrorActionPreference = 'Stop'
$failures = [System.Collections.Generic.List[string]]::new()
$checks = 0

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    $script:checks++
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
    } else {
        Write-Host "  FAIL  $name" -ForegroundColor Red
        if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
        $script:failures.Add($name)
    }
}

function Resolve-RepoRoot {
    $dir = $PSScriptRoot
    while ($dir -and -not (Test-Path (Join-Path $dir 'settings.gradle.kts'))) {
        $dir = Split-Path $dir -Parent
    }
    if (-not $dir) { throw '找不到仓库根目录（没有 settings.gradle.kts）' }
    return $dir
}

function Resolve-BuildTools {
    $root = Resolve-RepoRoot
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { $candidates += $env:ANDROID_HOME }
    $localProperties = Join-Path $root 'local.properties'
    if (Test-Path $localProperties) {
        $line = (Get-Content $localProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1)
        if ($line) {
            $candidates += ($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
        }
    }
    $candidates += (Join-Path $root 'android-sdk')

    foreach ($sdk in $candidates) {
        $buildTools = Join-Path $sdk 'build-tools'
        if (-not (Test-Path $buildTools)) { continue }
        $dir = Get-ChildItem $buildTools -Directory |
            Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending |
            Select-Object -First 1
        if ($dir) { return $dir.FullName }
    }
    throw '找不到 Android build-tools，请设置 ANDROID_SDK_ROOT'
}

function Tool([string]$dir, [string]$name) {
    # aapt2 是 .exe，apksigner 在 Windows 上是 .bat、其它平台是无后缀脚本。
    $suffixes = if ($IsWindows -or $PSVersionTable.PSVersion.Major -lt 6) {
        @('.exe', '.bat', '.cmd', '')
    } else {
        @('', '.sh')
    }
    foreach ($suffix in $suffixes) {
        $path = Join-Path $dir "$name$suffix"
        if (Test-Path $path) { return $path }
    }
    throw "缺少工具：$dir/$name"
}

$root = Resolve-RepoRoot
if (-not [System.IO.Path]::IsPathRooted($Apk)) { $Apk = Join-Path $root $Apk }
if (-not (Test-Path $Apk)) { throw "找不到 APK：$Apk" }

Write-Host "`n校验 $Apk`n" -ForegroundColor Cyan
$buildTools = Resolve-BuildTools
$aapt2 = Tool $buildTools 'aapt2'
$apksigner = Tool $buildTools 'apksigner'

# 1. 包身份
$badging = & $aapt2 dump badging $Apk
$package = ($badging | Where-Object { $_ -match "^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'" }) -join ''
if ($package -match "^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") {
    $pkgName, $versionCode, $versionName = $Matches[1], $Matches[2], $Matches[3]
    Check '包名' ($pkgName -eq 'com.hpcalc.app') $pkgName
    if ($ExpectedVersion) {
        Check 'versionName 与预期一致' ($versionName -eq $ExpectedVersion) "实际 $versionName，预期 $ExpectedVersion"
    } else {
        Check 'versionName 非空' (-not [string]::IsNullOrWhiteSpace($versionName)) $versionName
    }
    if ($ExpectedVersionCode) {
        Check 'versionCode 与预期一致' ($versionCode -eq $ExpectedVersionCode) "实际 $versionCode，预期 $ExpectedVersionCode"
    } else {
        Check 'versionCode 是正整数' ([int]$versionCode -gt 0) $versionCode
    }
} else {
    $firstLines = ($badging | Select-Object -First 3) -join "`n"
    Check '能读出 package 信息' $false $firstLines
}

# 2. 签名。debug 包也是签过名的，所以只校验「已签名」不够：
#    发出去一个 debug 证书签的包，下个版本换正式证书就必须让用户卸载重装。
$signerOutput = & $apksigner verify --print-certs $Apk 2>&1
$signerCode = $LASTEXITCODE
$signerDn = ($signerOutput | Where-Object { $_ -match 'certificate DN' }) -join ''
Check 'APK 已签名且校验通过' ($signerCode -eq 0 -and $signerDn) $signerDn.Trim()
Check '签名证书不是 debug 证书' ($signerDn -and $signerDn -notmatch 'CN=Android Debug') $signerDn.Trim()

# 3. 资源：两种语言的错误文案都要在包里（isShrinkResources 的风险点）
$resources = & $aapt2 dump resources $Apk
$zhStrings = ($resources | Select-String -Pattern '\(zh-rCN\)' -AllMatches).Count
Check '中文资源未被裁掉' ($zhStrings -gt 0) "zh-rCN 条目 $zhStrings 个"

$requiredStrings = @(
    'string/error_division_by_zero',
    'string/error_factorial_limit',
    'string/unsupported_polynomial_division',
    'string/display_exact_fraction',
    'string/substitute_action',
    'string/history_pin'
)
foreach ($entry in $requiredStrings) {
    $present = ($resources | Select-String -Pattern ([regex]::Escape($entry)) -SimpleMatch).Count -gt 0
    Check "资源存在：$entry" $present
}

# 4. 自适应图标
foreach ($icon in @('mipmap/ic_launcher', 'mipmap/ic_launcher_round', 'drawable/ic_launcher_foreground', 'color/ic_launcher_background')) {
    $present = ($resources | Select-String -Pattern ([regex]::Escape($icon)) -SimpleMatch).Count -gt 0
    Check "图标资源存在：$icon" $present
}

# 5. manifest：Application 子类必须注册，否则进程一起来就崩
$manifest = & $aapt2 dump xmltree --file AndroidManifest.xml $Apk
$manifestText = $manifest -join "`n"
Check 'manifest 注册了 CalculatorApplication' ($manifestText -match 'CalculatorApplication')
$targetLine = ($badging | Where-Object { $_ -match 'targetSdkVersion' }) -join ''
Check 'targetSdk 是 35' ($targetLine -match "targetSdkVersion:'35'") $targetLine.Trim()

# 6. 体积（R8 + 资源压缩之后不该是几十 MB）
$sizeMb = [math]::Round((Get-Item $Apk).Length / 1MB, 2)
Check 'release 体积在合理范围（<10 MB）' ($sizeMb -lt 10) "$sizeMb MB"

Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host "失败 $($failures.Count)/$checks 项：" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}
Write-Host "全部 $checks 项通过" -ForegroundColor Green
exit 0