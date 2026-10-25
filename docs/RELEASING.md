# 发布流程

本项目只在 GitHub Releases 分发，不上架应用商店。签名材料与密码**一律不入库**。

## 本地签名材料

| 文件 | 是否入库 | 说明 |
|---|---|---|
| `keystore/release.jks` | 否 | 4096 位 RSA，有效期 10000 天，别名 `hpcalc` |
| `keystore.properties` | 否 | 指向 keystore 并保存口令 |
| `app/proguard-rules.pro` | 是 | R8 规则，含 Room 反射所需的 keep |

两者都在 `.gitignore` 中。**换机器务必手动带走 `keystore/` 与 `keystore.properties`**——丢了就无法再给已安装的副本做升级签名，只能要求用户卸载重装。

`keystore.properties` 的内容：

```properties
storeFile=keystore/release.jks
storePassword=<见你本地的这个文件>
keyAlias=hpcalc
keyPassword=<同上>
```

`storeFile` 相对仓库根目录解析。没有这个文件时 release 仍能构建，只是产物未签名。

## 本地出包

```powershell
$env:GRADLE_USER_HOME = 'E:\code\calculate\.gradle-home'
./.gradle-dist/gradle-8.10.2/bin/gradle.bat assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`（R8 + 资源压缩 + 签名）。

**发版闸门**——跑一遍打包校验（19 项，失败即非 0 退出）：

```powershell
pwsh ./scripts/verify-release.ps1 -ExpectedVersion 3.0 -ExpectedVersionCode 5
```

它检查的是「构建成功但包是坏的」这类人眼看 log 看不到的问题：包名与版本号、
签名证书是否仍是发布证书（**debug 证书也会通过签名校验**，所以单独查）、
`isShrinkResources` 有没有把只在代码里按 id 引用的中文资源裁掉、自适应图标资源、
manifest 里的 `Application`、以及体积是否合理。CI 在发布标签上跑同一个脚本。

只查签名时也可以直接用：

```powershell
./android-sdk/build-tools/34.0.0/apksigner.bat verify --print-certs `
  app/build/outputs/apk/release/app-release.apk
```

## 版本号

`app/build.gradle.kts` 里的 `versionCode` 与 `versionName` 是手工维护的：

- 每次发布 **`versionCode` 必须 +1**，否则系统会拒绝覆盖安装；
- `versionName` 与 git tag 保持一致（`v3.0` → `3.0`）。

## 通过 CI 发布

`.github/workflows/android.yml` 在推送 `v*` 标签时构建 release 并创建 GitHub Release。首次使用前需要在仓库 Settings → Secrets and variables → Actions 里添加：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `keystore/release.jks` 的 Base64（`[Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore\release.jks'))`） |
| `KEYSTORE_PASSWORD` | `keystore.properties` 里的 `storePassword` |
| `KEY_ALIAS` | `hpcalc` |
| `KEY_PASSWORD` | `keystore.properties` 里的 `keyPassword` |

发布步骤：

```powershell
git tag v3.0
git push origin main --tags
```

CI 会把 `app-release.apk` 与 `app-release.apk.sha256` 挂到 Release 上。

## 安装注意

`applicationId` 是 `com.hpcalc.app`，与 v2.0 的 `com.example.calculator` 不同，是另一个应用，旧历史不会迁移。

debug 包与 release 包签名不同，**互相覆盖安装会报签名冲突**，切换前先卸载。

## 已知的验收欠账

本地环境没有真机、没有模拟器、没有系统镜像，所以：

- 排版引擎只有编译期保证，没有视觉证据；
- Room 的 `Migration(1, 2)` 无法在本地跑通（需要一台装过 v1 数据库的设备升级验证）；
- `docs/ACCEPTANCE.md` 里依赖运行时的条目必须人工执行。

发版前请先按 `docs/ACCEPTANCE.md` 过一遍。