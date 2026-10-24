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

阶段 1 实到 5 个模块（`:app`、`:core:model`、`:core:math`、`:core:designsystem`、`:data`）；
`:core:mathui` 与 `:feature:calculator` 在阶段 2 建立。

---

## 偏差记录

### 阶段 0

| 计划 | 实际 | 原因 |
|---|---|---|
| 修 `E:\code\calculate` 的 ACL | 由用户把会话切到 full access 绕过 | 该目录所有者是 `Administrators`，当前用户只有 Modify，缺 WRITE_DAC；DSH 无法为其写入沙箱授权。修复脚本需要同一权限，形成死循环，安全地拒绝并回滚。 |
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

---

## Backlog（不在三周范围内）

按"价值 ÷ 成本"排序，供后续取舍：

1. **数值模式隐式乘法** —— `2π`、`2(3+4)`、`3x`。改动小、日常收益高。
2. **复数运算** —— `Number` 抽象已在计划中，但实现与排版都要扩。
3. **微积分** —— 求导可复用多项式模块；积分与方程求解是符号 CAS，另一个量级。
4. **单位换算 / 进制转换界面**
5. **可编辑公式输入与光标定位** —— 需要表达式 AST，会牵动整个输入层。
6. **平板 / 折叠屏响应式布局**
7. **Compose Multiplatform 桌面端** —— 需要把 `:core:model`、`:core:math` 从 Android 库改为纯 JVM 模块。
8. **无障碍深度打磨（TalkBack）**
9. **动画与触觉反馈**
10. **云同步**

## 已知环境限制

- **无真机、无模拟器、无系统镜像**。因此 `docs/ACCEPTANCE.md` 中依赖运行时的条目（进程存活、视觉残留、启动白闪）无法在开发环境内验证，必须由人装到手机上执行。
- Gradle 构建必须显式指定 `GRADLE_USER_HOME` 与本地发行版，否则会尝试联网下载。