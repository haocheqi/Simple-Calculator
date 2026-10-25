# Simple Calculator

**English** | [简体中文](README.md)

An Android calculator. Written in Kotlin and Jetpack Compose. It requests no permissions. It does not use the network. It does not collect data.

## Features

- Four modes: basic, scientific, symbolic, matrix.
- High precision: 50 significant digits internally. The display shows 16 digits.
- Exact fractions: shows true fractions. Press `S⇔D` to switch between a decimal and a fraction.
- Trigonometric and other scientific keys: sin, cos, tan, asin, acos, atan, ln, log, √, abs, n!, %, x², xʸ, π, e, ans.
- Numeric functions: gcd, lcm, comb, perm, min, max, clamp, sum, mean, median, stdev, cbrt, root, logb, atan2, deg, rad. Each function accepts multiple arguments and nesting.
- Symbolic mode: simplifies polynomials. Substitutes values for variables.
- Matrix: 2×2 to 6×6. Does addition, subtraction, multiplication, determinant, inverse, transpose and rank. The inverse and the determinant show exact fractions.
- Angle units: radians, degrees, gradians.
- Display modes: decimal, engineering, exact fraction.
- History: save, pin, reuse and clear.
- Themes: follow the system, light, dark.
- UI language: Chinese, English.

## Build

You need JDK 17 and Android SDK 35.

1. Run `git clone <repository URL>`.
2. Run `./gradlew assembleDebug` in the repository root.
3. Get `app-debug.apk` from `app/build/outputs/apk/debug/`.

Run `./gradlew test` to run the unit tests.

See [docs/RELEASING.md](docs/RELEASING.md) for signing and release steps.