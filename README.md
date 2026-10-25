# Simple Calculator

[English](README.en.md) | **简体中文**

Android 计算器。使用 Kotlin 与 Jetpack Compose 编写。不申请任何权限。不联网。不收集数据。

## 功能

- 四种模式：简单、科学、符号、矩阵。
- 高精度：内部保留 50 位有效数字，界面显示 16 位。
- 精确分数：显示真分式。按 `S⇔D` 键切换小数与分数。
- 三角函数与其它科学键：sin、cos、tan、asin、acos、atan、ln、log、√、abs、n!、%、x²、xʸ、π、e、ans。
- 数值函数：gcd、lcm、comb、perm、min、max、clamp、sum、mean、median、stdev、cbrt、root、logb、atan2、deg、rad。支持多参数与嵌套调用。
- 符号模式：化简多项式。代入数值求值。
- 矩阵：2×2 至 6×6。做加、减、乘、行列式、逆、转置、秩。逆与行列式显示精确分数。
- 角度单位：弧度、角度、百分度。
- 显示模式：小数、工程记数、精确分数。
- 历史记录：保存、置顶、复用、清空。
- 主题：跟随系统、浅色、深色。
- 界面语言：中文、英文。

## 构建

需要 JDK 17 和 Android SDK 35。

1. 运行 `git clone <仓库地址>`。
2. 在仓库根目录运行 `./gradlew assembleDebug`。
3. 在 `app/build/outputs/apk/debug/` 取 `app-debug.apk`。

运行 `./gradlew test` 执行单元测试。

签名与发布步骤见 [docs/RELEASING.md](docs/RELEASING.md)。