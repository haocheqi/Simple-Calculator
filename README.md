# Calculator

一个面向 Android 10（API 29）及以上的高精度多模式计算器。

## 功能

- 普通和科学计算：50 位内部 `BigDecimal` 精度，默认显示 16 位有效数字。
- 三角、反三角、双曲、对数、指数、阶乘、排列和组合。
- 多项式展开、合并同类项、化简、隐式乘法和变量代入。
- 最多 6×6 实数矩阵：加减乘、数乘、转置、行列式、逆矩阵和秩。
- Room 历史记录、DataStore 设置、Material 3 深浅主题。
- 简体中文/English 资源可继续扩展；当前主界面采用英文数学术语。

## 构建

项目、Gradle 分发缓存、Android SDK 和构建产物均可放在 E 盘。工程默认使用 `E:\code\calculate\android-sdk` 中的 SDK；其他机器请修改 `local.properties` 的 `sdk.dir`。

```powershell
./gradlew test
./gradlew assembleDebug
```

Debug APK 输出在：

`app/build/outputs/apk/debug/app-debug.apk`

## 工程结构

- `domain/`：高精度数值解析、多项式归一化和矩阵算法。
- `data/`：Room 历史记录和 DataStore 设置。
- `ui/`：Compose 模式切换、表达式键盘和矩阵网格。

## 依赖许可

超越函数使用 `ch.obermuhlner:big-math`（MIT）；AndroidX、Compose、Room 和 DataStore 依照各自 Apache 2.0 许可分发。
