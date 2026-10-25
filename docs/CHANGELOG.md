# 更新日志

## 3.0（未发布）

相对 `v2.0-baseline` 的重写。分为三个阶段交付：模块化与错误模型（`v3.0-w1`）、呈现层重写（`v3.0-w2`）、能力与发布工程（本版）。

### 新增

- **数学排版引擎**（`:core:mathui`）：自绘基线对齐、真分式、上下标、根式与矩阵定界符。用 Compose `Canvas` 绘制，不引入 WebView、KaTeX 或任何排版库。
- **精确分数显示模式**：新增 `RationalEvaluator`，用 `BigInteger` 分子分母做加、减、乘、除与整数次幂，`1/3` 渲染为真分式。遇到 `sin`/`ln`/`√` 这类必然产生无理数的运算会自动退回小数，而不是给出假分数。
- **函数库扩充**：`gcd`、`lcm`、`comb`、`perm`、`min`、`max`、`clamp`、`sum`、`mean`、`median`、`stdev`、`cbrt`、`root`、`logb`、`atan2`、`deg`、`rad`，支持多参数与嵌套调用。
- **符号模式代入求值**：`变量 = 数值` 一条，代入值本身允许是表达式（`π/2`、`3+1`），结果同样进历史、同样刷新 `ans`。
- **设置界面**：主题（跟随系统/浅色/深色）、角度制（弧度/角度/百分度）、显示模式（小数/工程记数/精确分数），持久化在 DataStore。
- **自适应图标**：`mipmap-anydpi-v26` + Android 13 单色层。
- **任意尺寸矩阵输入**：左矩阵的行、列各自可调（2..6），右矩阵形状按运算推导——乘法 `A(m×n)·B(n×p)` 自动把 B 的行数锁成 n，只暴露 B 的列数；加减沿用 A 的形状。界面不会允许拼出无法计算的组合。
- **历史置顶与数据库升级**：`history` 升到 v2，新增 `pinned` 列与真实的 `Migration(1, 2)`（没有用破坏性迁移），置顶条目永远排在最前。
- **CI 与发布流程**：`.github/workflows/android.yml` 跑测试并产出 debug APK；推送 `v*` 标签时恢复签名材料、构建 release、用 `apksigner` 校验签名、附 SHA-256 发 Release。步骤见 `docs/RELEASING.md`。
- **发布工程**：R8 代码压缩与资源压缩、签名配置（`keystore.properties`，不入库）、`proguard-rules.pro`。
- **发布闸门**：`scripts/verify-release.ps1`（19 项）。本地与 CI 跑同一个脚本，检查包名、版本号、签名证书（能识破 debug 证书）、被 `isShrinkResources` 裁掉的文案、图标资源、manifest 里的 `Application` 与体积。

### 修复

- `1+`、`(` 这类不完整输入在 v2.0 会抛 `IllegalStateException` 直接崩溃；现在一律返回结构化错误。
- `(x+1)(x-1)` 化简结果为 `x^2 + 0x - 1`，多余的零系数项没有清理。
- 求秩用绝对阈值 `1e-40` 判定零，元素量级小于该值时整矩阵被判为秩 0；改为相对容差。
- 「工程记数」模式对 `12345678.9` 这类中等量级不生效——`BigDecimal.toEngineeringString()` 只在 adjusted exponent < -6 或 scale < 0 时才输出 `E`。
- 深色模式冷启动白闪：补上 `values-night` 的窗口底色与系统栏图标明暗。
- 应用内语言开关只驱动了一个布尔变量，并没有真正切换文案；已删除，改为完全跟随系统 locale。
- 错误提示里的英文技术片段（`Out of domain: factorial is limited to 170`）直接显示在中文界面上。现在英文 `detail` 只进日志，展示改为「错误类型 + 结构化原因（`MathIssue`）」映射到 `strings.xml`，中英文各 30 条：同样一句变成「阶乘最大支持 170」。

### 变更

- 拆分为 7 个 Gradle 模块：`:app`、`:core:model`、`:core:math`、`:core:mathui`、`:core:designsystem`、`:data`、`:feature:calculator`。
- `applicationId` 由 `com.example.calculator` 改为 `com.hpcalc.app`。**升级会装成另一个应用，旧历史不会迁移。**
- 界面重写为无状态 Compose：状态 `CalculatorUiState`、意图 `CalculatorUiEvent`、求值决策集中在 `CalculatorEngine`。
- 会话态用 `SavedStateHandle` 持久化，旋屏与进程回收后表达式、模式、`ans`、矩阵输入都不丢。

### 已知问题

- 排版引擎没有自动化视觉测试——需要设备或模拟器，本地环境不具备。相关验收条目必须人工执行。
- Room 的 `Migration(1, 2)` 里那句 SQL 已在 JVM 上用真实 SQLite 验证（老数据存活、新列非空且默认 0、置顶排序），但真机上的 identity-hash 校验仍需覆盖安装验证。
- 数值模式仍不支持隐式乘法（`2π`、`2(3+4)`），会报解析错误。
- 阶段 3 的代码已完成，但 `docs/ACCEPTANCE.md` 的手工验收尚未执行，因此 `v3.0` 还不算验收通过。