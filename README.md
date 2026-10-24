# Calculator（高精度计算器）

面向 Android 10（API 29）及以上的高精度多模式计算器。内部 50 位 `BigDecimal` 精度，默认显示 16 位有效数字。

当前处于 **v3.0 重写阶段 1**（模块化 + 正确性）。长期计划见 [docs/ROADMAP.md](docs/ROADMAP.md)，验收清单见 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)。

## 功能

- 普通和科学计算：50 位内部精度，默认显示 16 位有效数字。
- 三角、反三角、双曲、对数、指数、阶乘、取模。
- 角度制：弧度 / 角度 / 百分度（计算层已支持并测试，设置界面在阶段 3 接通）。
- 多项式展开、合并同类项、化简、隐式乘法和变量代入。
- 最多 6×6 实数矩阵（矩形运算在领域层已支持，界面在阶段 3 解锁）。
- Room 历史记录、DataStore 设置、Material 3 深浅主题。

## 构建

工程使用本地 Gradle 发行版与本地依赖缓存，不依赖 `gradlew` 的联网下载：

```powershell
$env:GRADLE_USER_HOME = 'E:\code\calculate\.gradle-home'
./.gradle-dist/gradle-8.10.2/bin/gradle.bat test assembleDebug
```

Debug APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

Android SDK 位于 `E:\code\calculate\android-sdk`，由 `local.properties` 的 `sdk.dir` 指定；其他机器需修改该文件。

## 工程结构

```
:app                  应用壳、Activity、ViewModel、界面
:core:model           领域模型：MathBlock / Matrix / AngleUnit / MathError / CalculatorSession
:core:math            数值求值、多项式、矩阵、格式化
:core:designsystem    主题与设计系统
:data                 Room 历史记录与 DataStore 设置
```

`:core:mathui` 与 `:feature:calculator` 将在阶段 2 引入。

规则：模块间只通过 `:core:model` 的类型通信；`domain` 层不依赖 Android 框架。

## 测试

```powershell
$env:GRADLE_USER_HOME = 'E:\code\calculate\.gradle-home'
./.gradle-dist/gradle-8.10.2/bin/gradle.bat test
```

共 67 个 JVM 单元测试，集中在 `core/math/src/test`，逐条对应 `docs/ACCEPTANCE.md` 的崩溃红线与数学正确性条目。

## 依赖许可

超越函数使用 `ch.obermuhlner:big-math`（MIT）；AndroidX、Compose、Room 和 DataStore 依照各自 Apache 2.0 许可分发。