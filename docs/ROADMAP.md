# 路线图

> 本文件是 v3.0 重写的长期计划与执行记录。计划经过一次批准，此后每次偏离都在「偏差记录」里留痕，不静默改动。

## 目标

把项目从「单模块 + 178 行巨型 composable + 硬编码文案 + 演示级原型」重写为
「多模块 + 单向数据流 MVVM + 资源化 + 公式排版」，以 GitHub Releases 分发。

**定位**：个人自用的高精度工具，不上架应用商店。因此跳过商店合规（隐私政策、商店素材、包名商标检查）。

## 三个阶段

| 阶段 | 主题 | 交付物 | 标签 |
|---|---|---|---|
| 1 | 地基重写与正确性 | 多模块骨架、domain 迁移、`MathError` 统一、`≥25` 回归测试 | `v3.0-w1` |
| 2 | 呈现层重写 | `MathBlock` 重塑 + 自定义排版、`UiState`/`UiEvent`/`SavedStateHandle`、`strings.xml` 全量迁移、新 UI | `v3.0-w2` |
| 3 | 能力扩展与发布 | 角度制、任意尺寸矩阵、函数库、精确分数、历史 v2、签名/minify/图标/CI | `v3.0` |

进度：三个阶段全部完成，`v3.0` 已打标签。

- 阶段 1、2 分别在 `v3.0-w1`、`v3.0-w2` 交付。
- 阶段 3 的 `docs/ACCEPTANCE.md`（50 条）在真机上分批验完：主体在 `3.0 (7)` 通过，
  之后两轮反馈各修了一处，最终 `K07`（逆矩阵真分式 + 函数条三行 + 不截断）与
  `K11`（矩阵单元格过滤）在 `3.0 (9)` 通过。验收记录按条目写在 `docs/ACCEPTANCE.md`。
- 仓库仍没有配置 git remote，GitHub Release 这一步需要用户在远端完成，步骤见 `docs/RELEASING.md`。

## 每阶段的完成定义

1. `gradle test assembleDebug` 全绿；
2. 产出可安装的 debug APK；
3. `docs/ACCEPTANCE.md` 中本阶段适用的条目全部通过；
4. 打 git 标签。

## 目标架构

```
:app                  应用壳、NavHost、Application、手写 DI
:core:model           纯 Kotlin：MathBlock / Matrix / AngleUnit / MathError / CalculatorMode / CalculatorSession
:core:math            数值求值、多项式、矩阵、Rational、格式化
:core:designsystem    主题、色板、尺寸 token、通用组件
:core:mathui          MathBlock 渲染器（自定义 Layout，阶段 2 引入）
:data                 Room + DataStore + Repository
:feature:calculator   四模式 Screen + ViewModel（阶段 2 引入）
```

阶段 1 实到 5 个模块；阶段 2 补齐 `:core:mathui` 与 `:feature:calculator`，共 7 个模块。

阶段 2 之后的实际分层：

```
:app                  Application / AppContainer / MainActivity / NavHost
:core:model           纯 Kotlin，无 Android 依赖
:core:math            CalculatorEngine（模式分派）+ 三个求值器 + 格式化
:core:mathui          自定义排版：MeasuredBlock 树 + Canvas 绘制，无 WebView
:core:designsystem    Theme / CalcTokens / CalcKey
:data                 Room 历史 + DataStore 设置
:feature:calculator   UiState / UiEvent / ViewModel / 四个界面
```

---

## 偏差记录

### 阶段 0

| 计划 | 实际 | 原因 |
|---|---|---|
| —— | `.gitignore` 补全 | 原 `.gitignore` 未覆盖根目录的 6 个 APK 与 13 份构建日志，首次 `git add` 会把 350 MB 产物入库。 |
| 用 `./gradlew` 构建 | 用 `.gradle-dist\gradle-8.10.2\bin\gradle.bat` | `.gradle-home` 下没有 wrapper 缓存，离线时 `./gradlew` 会去联网取发行版并失败。 |
| 假定环境离线 | 环境可联网 | 早先 `wrapper.log` 的失败是瞬时网络问题。确认可达后撤掉了临时绕过方案。 |

### 阶段 1

| 计划 | 实际 | 原因 |
|---|---|---|
| `build-logic/` convention 插件 | 不做，各模块各自声明约 20 行构建配置 | 只有 5 个库模块，重复量约 100 行；`build-logic` 子构建在当前离线条件下是多余的风险面。模块数超过 8 个时再做。 |
| jacoco 覆盖率 ≥ 80% 作为 S1 | 改为「≥ 25 个用例 + 逐条映射到验收红线」 | jacoco agent 不在离线缓存中。覆盖率百分比本身也是弱指标；用例到红线的映射更能表达"哪些行为被守住了"。实际交付 67 个用例。 |
| `MathBlock` 在阶段 1 定型 | 保持 v2.0 形状，阶段 2 一并重塑 | 阶段 1 的验收要求"旧 UI 零回归"；此时改 `MathBlock` 会让旧 UI 编译失败，且同一个类型要吃两次改造。 |
| 包名改到 `io.github.<user>.hpcalc` 放在阶段 3 | `applicationId` 与 Kotlin 包名在阶段 1 一并改为 `com.hpcalc` / `com.hpcalc.app` | 阶段 1 本来就要为模块迁移改动每个文件的 package 与 import，顺手改名的边际成本接近零；分两次做反而多一次全量改动。 |
| 数值模式不支持隐式乘法（未列入计划） | 本阶段仍未支持 | 超出已批准范围，未擅自添加。`2π`、`2(3+4)` 会报解析错误。见「Backlog」。 |

### 阶段 1 修复的额外缺陷

| 缺陷 | 说明 |
|---|---|
| `Poly.times` 不清理零系数 | `(x+1)(x-1)` 会渲染成 `x^2 + 0x - 1`。v2.0 同样存在，由本次新增测试 `E02` 捕获。 |
| 求秩的绝对阈值 | v2.0 用 `abs() < 1e-40` 判定零，元素量级本身小于该值时整矩阵被判为秩 0。改为相对容差。 |

### 阶段 2

| 计划 | 实际 | 原因 |
|---|---|---|
| 保留应用内语言开关 | 删除 `Settings.language`，改为完全跟随系统 locale | v2.0 那个开关只驱动了一个布尔变量，并没有切换任何字符串——是个假功能。`values` + `values-zh-rCN` 已经够用，系统语言一换、应用重进即整体生效。 |
| `DisplayMode` 作为会话态 | 存进 DataStore，与角度单位同级 | 显示格式是长期偏好，让它跟旋转一起丢不合理。 |
| 矩阵界面支持任意尺寸（阶段 3） | 阶段 2 只做 n×n（2..6） | 矩形矩阵的运算能力阶段 1 就有，但输入网格的尺寸联动（A 的列数决定 B 的行数）会牵动状态模型，放到阶段 3 与「任意矩阵」一起做，避免一次动排版＋状态＋矩阵三块。 |
| 排版用 `createComposeRule` 做组件测试 | 未做 | 组件测试要跑在设备上；本环境没有设备也没有模拟器。评估过引入 Robolectric 在 JVM 跑 Compose，但它需要 `android-all` 体积与 native 图形配置，对本项目的收益不足以抵消复杂度。**代价：排版只有编译期保证，没有视觉证据**，H01 必须靠人眼验收。 |
| 精确分数显示 | 推迟到阶段 3，与 `RationalEvaluator` 一起 | `Fraction` 排版节点已经实现并有测试，但没有任何生产者能产出它——硬造一个「看起来像分数」的字符串是假功能。`DisplayMode` 因此阶段 2 只有 `DECIMAL` / `ENGINEERING`。 |
| —— | 新增 `CalculatorEngine` | 原计划 ViewModel 直接调三个求值器。抽出来之后模式分派变成纯函数，可以在 JVM 上覆盖全部分支，ViewModel 只剩状态搬运。 |

### 阶段 2 修复的额外缺陷

| 缺陷 | 说明 |
|---|---|
| 工程记数法形同虚设 | `BigDecimal.toEngineeringString()` 只在 adjusted exponent < -6 或 scale < 0 时才输出 `E`；像 `12345678.9` 仍原样返回，导致「工程记数」和「小数」看起来一样。改为自己算 3 的倍数指数。由新增测试 `engineering mode always switches notation` 捕获。 |
| 深色模式冷启动白闪 | v2.0 没有 `values-night`，平台主题的 `windowBackground` 在深色模式下仍是浅色。补上 `values-night/colors.xml` 与 `bools.xml`（同时翻转系统栏图标明暗）。 |

### 阶段 3

| 计划 | 实际 | 原因 |
|---|---|---|
| `history` 表同时加 `pinned` 列与 `(mode, createdAt)` 索引 | 只加了 `pinned` 列 | 历史读取上限 100 行，索引对排序的收益可以忽略，却多一处迁移 DDL 出错的机会。迁移本身是真的 `Migration(1, 2)`，没有用 `fallbackToDestructiveMigration`。 |
| 生成 gradle wrapper 供 CI 使用 | 不生成 wrapper，CI 用 `setup-gradle` 固定 8.10.2 | 工程一直用本地固定版本发行版构建；再引入 wrapper 等于同时维护两条版本路径，容易漂移。CI 里钉同一个版本即可。 |
| 非方阵：A、B 各自提供行、列旋钮 | B 只提供列数旋钮，行数由 A 的列数推导 | 允许自由编辑 B 的行数就能拼出无法计算的组合；直接推导比事后报错体验更好。 |
| 精确分数对所有运算生效 | 只在能做精确运算时生效 | `sin`/`ln`/`√` 必然产生无理数，硬凑分数等于撒谎。引擎在这里返回 `Unsupported` 并自动退回小数。 |

### 阶段 3 修复的额外缺陷

| 缺陷 | 说明 |
|---|---|
| 符号模式的代入求值整条链路缺失 | 领域层 `substitute` 从 v2.0 起就存在且有测试，但 `CalculatorEngine` 与界面都没接——阶段 2 重写界面时把 v2.0 的「代入求值」面板漏掉了，而 README 仍宣称支持。已补回引擎方法、状态、事件与代入条，并让代入值本身支持表达式。 |
| `BigInteger(3)` 在 Kotlin 里不可用 | 只暴露了 `ByteArray` / `String` 构造器，测试里必须写 `BigInteger.valueOf(3)`。由编译期捕获。 |


### 阶段 3 的真机反馈修正（首轮 6 条）

第一次装到真机上反馈的 6 个问题，全部落在交互层，领域层只补了矩阵求逆。

| 问题 | 根因 | 处理 |
|---|---|---|
| 顶部操作栏太靠上，点不到设置 | Android 15 强制 edge-to-edge，三个界面都没有处理系统栏 inset | 三个界面统一 `windowInsetsPadding(WindowInsets.systemBars)` |
| 没有 DEL 键，且只能改末尾 | 退格键早已定义但从未放进 `mainKeypad`；`Append` 只做字符串拼接 | 键盘第一行改为 `AC ⌫ ( )`；状态新增 `caret`，插入/删除改成纯函数（`InputEditing.kt`）并随光标走；点击算式用 `getOffsetForPosition` 定位光标 |
| AC 不清结果 | `Clear` 只清 `expression` | 同时清 `result`、`exactResult` 与错误提示 |
| 矩阵逆是小数 | 求逆走 `BigDecimal` 高斯-约当消元 | 改用有理数（BigInteger 分数）消元，输出真分式；输入小数位数过深时退回小数 |
| 科学模式排版不合理 | 17 个函数塞在一条横向滚动条里，没有滚动提示 | 改成 `FlowRow` 折行，48dp 键宽在 360dp 屏上每行 6 个、共 3 行 |
| 缺小数/分数互化 | 求值只产出一种形态，事后再换回来不可能（`0.3333…` 反推不出 `1/3`） | `Outcome` 增加 `exact`，求值时同时保留精确分数；结果区出现 `S⇔D` 按钮切换，两种形态相同时不显示 |

| 第二轮自测：「精确分数」设置对求值无效 | 状态里存了 `displayMode` 副本，求值读副本；切设置只写 DataStore，副本永不更新 | 删掉 `displayMode`/`angleUnit` 两份副本，求值时现读 DataStore，并加测试断言状态里不再出现这两个字段 |
| 第二轮自测：旋转后结果丢失 | `MathBlock` 不可序列化，`SavedStateHandle` 只存了表达式与 `ans` | 启动时静默重算补回结果（不写历史、不动 `ans`） |

| 事件层此前没有任何测试 | 所有真机 bug（AC 不清结果、退格不跟光标、显示模式没送达求值层）都在 ViewModel 里，而它依赖 Android `Context`，JVM 上跑不起来 | 抽出 `SettingsStore` 接口 + 可注入求值调度器，补 15 条 ViewModel 测试；并用「故意改坏 → 测试必须报错」验证了它们确实能拦住这类回归 |

| K07 不过：逆矩阵是小数 | 引擎调用 `Matrix.toMathBlock()`，绕开了会优先使用 `MatrixResult.Success.exact` 的呈现扩展 | 改调 `MatrixResult.toMathBlock(displayMode)`；补引擎级与 ViewModel 级端到端测试（先改回去确认能失败） |
| K07 另一半：函数条不是 3 行 | `FlowRow` 里用定宽 48dp，360dp 屏上占满 328dp，稍窄就退化成 4 行 | 改为每行固定 6 个 + `weight(1f)` 均分，行尾补空位保持等宽 |
| 矩阵输入非法时静默无反应 | `toMatrix()` 返回 null 后 `?: return` | 状态层过滤非法字符；仍无法解析时给出本地化错误 |

| K07 收尾：分式矩阵显示不全 | 真分式让每格变成两行高，2×2 超出 160dp 的显示区；外层还套着横向滚动，只能看到一半 | `MathBlockView` 在有限约束下等比缩小（只缩不放，2% 余量）；显示区去掉横向滚动；矩阵模式基准 28sp、上限 220dp |

两处设计取舍记录在案：

- **`0` 不再横跨两格。** 4 列 × 5 行只有 20 格，要塞进退格就必须放弃 `0` 的宽度；`=` 因此移到第 5 行第 3 列、`+` 独占第 4 列，只为保持运算符成列。
- **精确幂提前止损。** 精确分数没有精度上限，`(1000^10000)^10000` 会要求几亿位数字；「每次求值先试一次精确计算」放大了这个风险，因此在 `Fraction.pow` 里按位长估算结果规模，超过 2000 位就退回小数路径（`ArgumentTooLarge` 不再对面报错，交给数值路径裁决）。

---

## Backlog（不在三周范围内）

按"价值 ÷ 成本"排序，供后续取舍：

1. **数值模式隐式乘法** —— `2π`、`2(3+4)`、`3x`。改动小、日常收益高。
3. **复数运算** —— `Number` 抽象已在计划中，但实现与排版都要扩。
4. **微积分** —— 求导可复用多项式模块；积分与方程求解是符号 CAS，另一个量级。
5. **单位换算 / 进制转换界面**
6. **可编辑公式输入与光标定位** —— 需要表达式 AST，会牵动整个输入层。
7. **排版引擎的自动化视觉测试** —— 需要 Robolectric 或真机，目前只有编译期保证。
8. **平板 / 折叠屏响应式布局**
9. **Compose Multiplatform 桌面端** —— 需要把 `:core:model`、`:core:math` 从 Android 库改为纯 JVM 模块。
10. **无障碍深度打磨（TalkBack）**
11. **动画与触觉反馈**
12. **云同步**

## 已知环境限制

- **无真机、无模拟器、无系统镜像**。因此 `docs/ACCEPTANCE.md` 中依赖运行时的条目（进程存活、视觉残留、启动白闪、图标、数据库升级）无法在开发环境内验证，必须由人装到手机上执行。**阶段 3 因此尚未算通过。**
- Room 的 `Migration(1, 2)` 里那句 SQL 已经在 JVM 上用真实 SQLite 验证过（`HistoryMigrationTest`：老行存活、新列 `NOT NULL DEFAULT 0`、置顶排序正确），但 Room 的 identity-hash 校验与真实升级路径仍只能在装了 v1 数据库的设备上验证。
- Gradle 构建必须显式指定 `GRADLE_USER_HOME` 与本地发行版，否则会尝试联网下载。
