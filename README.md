# Calculator（高精度计算器）

面向 Android 10（API 29）及以上的高精度多模式计算器。内部 50 位 `BigDecimal` 精度，默认显示 16 位有效数字。

当前处于 **v3.0**（三个阶段代码均已完成，手工验收待执行）。长期计划见 [docs/ROADMAP.md](docs/ROADMAP.md)，验收清单见 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)，发布流程见 [docs/RELEASING.md](docs/RELEASING.md)，变更记录见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

## 功能

- 普通和科学计算：50 位内部精度，默认显示 16 位有效数字。
- 三角、反三角、双曲、对数、指数、阶乘、取模；另有 `gcd`、`lcm`、`comb`、`perm`、`min`、`max`、`clamp`、`sum`、`mean`、`median`、`stdev`、`cbrt`、`root`、`logb`、`atan2`、`deg`、`rad`，支持多参数与嵌套。
- 角度制：弧度 / 角度 / 百分度，设置里可改，反三角结果会还原到所选单位。
- 显示模式：小数、工程记数、**精确分数**（`1/3` 渲染成真分式；遇到必然产生无理数的运算自动退回小数，不伪造分数）。
- 多项式展开、合并同类项、化简、隐式乘法，以及**代入求值**（变量名 + 取值，取值可以是表达式如 `π/2`）。
- 最多 6×6 实数矩阵：加减乘、行列式、逆、转置、秩，**支持非方阵**（B 的形状按运算自动推导）。
- 公式排版用 Compose 自绘：基线对齐、真分式、上下标、根式、矩阵定界符。**不使用 WebView**。
- 错误提示按类型结构化并本地化（中英各 30 条），英文技术细节只进日志，不显示在界面上。
- Room 历史记录（可置顶）、DataStore 设置、Material 3 深浅主题（含 `values-night` 冷启动底色）。

## 构建

工程使用本地 Gradle 发行版与本地依赖缓存，不依赖 `gradlew` 的联网下载：

```powershell
$env:GRADLE_USER_HOME = 'E:\code\calculate\.gradle-home'
./.gradle-dist/gradle-8.10.2/bin/gradle.bat test assembleDebug
```

Debug APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`；release 走 `assembleRelease`（R8 + 签名），签名材料与发布步骤见 [docs/RELEASING.md](docs/RELEASING.md)。

发版前跑一遍打包闸门：pwsh ./scripts/verify-release.ps1 -ExpectedVersion <版本>——校验包名、版本号、发布证书、被资源压缩裁掉的中文文案、图标与 manifest 里的 Application。

Android SDK 位于 `E:\code\calculate\android-sdk`，由 `local.properties` 的 `sdk.dir` 指定；其他机器需修改该文件。

## 工程结构

```
:app                  应用壳：Application、依赖容器、MainActivity、NavHost
:core:model           领域模型：MathBlock / Matrix / AngleUnit / MathError / DisplayMode / ThemeMode
:core:math            CalculatorEngine（模式分派）、数值求值、多项式、矩阵、格式化
:core:mathui          数学排版引擎（自定义 Canvas 绘制，无 WebView/KaTeX）
:core:designsystem    主题、色板、尺寸 token、按键组件
:data                 Room 历史记录与 DataStore 设置
:feature:calculator   UiState / UiEvent / ViewModel 与四个界面
```

规则：`core` 模块不依赖 Android 框架；界面无状态，只接受 `CalculatorUiState` 并上抛 `CalculatorUiEvent`；模块间只通过 `:core:model` 的类型通信。

## 测试

```powershell
$env:GRADLE_USER_HOME = 'E:\code\calculate\.gradle-home'
./.gradle-dist/gradle-8.10.2/bin/gradle.bat test
```

**176 个 JVM 单元测试**：`core/math`（求值、多项式、矩阵、有理数、呈现树、引擎分派）、`feature/calculator`（矩阵形状推导、错误文案本地化完整性）、`data`（用真实 SQLite 跑迁移 SQL，断言老数据不丢、新列非空且默认为 0），逐条对应 `docs/ACCEPTANCE.md` 的崩溃红线与数学正确性条目。

排版引擎（`:core:mathui`）目前只有编译期保证，**没有自动化视觉测试**——它需要设备或模拟器。`docs/ACCEPTANCE.md` 的 H01/H02 必须人工验收。

## 依赖许可

超越函数使用 `ch.obermuhlner:big-math`（MIT）；AndroidX、Compose、Room 和 DataStore 依照各自 Apache 2.0 许可分发。运行时不引入任何数学排版库或 WebView。