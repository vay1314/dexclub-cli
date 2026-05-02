# dexclub-cli

`dexclub-cli` 是一个面向 `dex / apk / Android 资源` 的 Kotlin 多模块项目。
它提供一套围绕工作区的 CLI，用来做 dex 查询、方法检查、代码导出以及 Android 资源解析。

当前仓库包含三个核心模块：

- `cli`
  命令行入口，负责参数解析、命令分发、结果渲染与退出码
- `core`
  稳定能力边界，负责 workspace、dex 查询/导出、resource 解析
- `dexkit`
  面向 KMP 的 DexKit 包装层

## 当前能力

- 初始化并管理 `.dexclub` 工作区
- 在同一工作区内列出并切换 active target
- 输出工作区状态与 active target 摘要
- 按 JSON 条件查找类、方法、字段
- 按字符串批量条件查找类和方法
- 查看单个方法的一层关系详情
- 导出单类 dex、smali、Java
- 导出单方法 dex、smali、Java
- 解析 `AndroidManifest.xml`
- 读取 `resources.arsc`
- 解码二进制 XML
- 列出、解析、搜索资源条目

## 仓库结构

- `cli/`
  当前命令行模块
- `core/`
  当前能力库模块
- `dexkit/`
  KMP DexKit 包装层
- `dexkit/vendor/DexKit/`
  vendored 上游 DexKit
- `dexkit/vendor/libcxx-prefab/`
  Android 构建链依赖的本地 `libcxx` prefab 仓库
- `.docs/v3/`
  命令面、对象模型、执行流与存储结构设计文档

## 环境要求

开发和本地构建默认要求：

- JDK 21
- Android SDK
- Android NDK `28.2.13676358`
- `cmake`
- `ninja`

说明：

- Android SDK / NDK 主要服务于 DexKit native 构建和测试样本生成
- `core` 的资源命令运行时不要求额外安装 Android SDK
- 桌面端 DexKit 运行前提可参考上游文档：
  [DexKit 桌面端运行说明](https://luckypray.org/DexKit/zh-cn/guide/run-on-desktop.html)

## 初始化仓库

首次拉取后先初始化 submodule：

```bash
git submodule update --init --recursive
```

如果本地还没有发布 `libcxx`，先执行：

```bash
cd dexkit/vendor/libcxx-prefab
./gradlew :cxx:publishToMavenLocal
```

## 构建与验证

编译 `dexkit`：

```bash
./gradlew :dexkit:compileKotlinJvm
./gradlew :dexkit:assembleAndroidMain
```

编译 `core` 和 `cli`：

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin
```

运行测试：

```bash
./gradlew :core:jvmTest
./gradlew :cli:test
```

打包 fat jar：

```bash
./gradlew :cli:fatJar
```

生成带启动脚本的分发目录与 zip：

```bash
./gradlew :cli:installShadowDist :cli:shadowDistZip
```

## 运行 CLI

fat jar 入口：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar <command> [args]
```

Windows PowerShell 下推荐优先使用分发脚本：

```powershell
& .\cli\build\install\cli-shadow\bin\cli.ps1 <command> [args]
```

查看帮助：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar --help
java -jar cli/build/libs/dexclub-cli-all.jar help
java -jar cli/build/libs/dexclub-cli-all.jar help find-method
```

Windows PowerShell 如果要传复杂 `--query-json`，优先使用：

```powershell
& .\cli\build\install\cli-shadow\bin\cli.ps1 find-class E:\path\to\workdir --query-json '{"matcher":{"className":{"value":"Sample","matchType":"Contains","ignoreCase":true}}}'
```

说明：

- `cli.ps1` 会比 `cli.bat` 更稳定地保留 PowerShell 参数边界
- 对复杂 JSON 查询，也可以优先使用 `--query-file`

DexKit native 运行提示：

- fat jar 分发场景下，通常只需把 DexKit native 动态库放在 `dexclub-cli-all.jar` 同目录
- 如果不是通过 fat jar 运行，则可以通过 native 文件路径或 native 目录显式指定，也可以通过环境变量传入，或确保其位于 `java.library.path` 可见范围内

## 工作区模型

当前命令面遵循两条规则：

- `init` 必须显式传入单文件输入路径
- 其余命令统一在已初始化工作区上执行，`[workdir]` 可省略；省略时默认使用当前目录

当前工作区允许存在多个已初始化 target，但任意时刻只有一个 active target：

- `init <input>`：为该输入创建或刷新 target，并将其设为当前 active target
- `switch <input>`：只在当前工作区内切换到已经初始化过的 target，不创建新 target
- `targets`：列出当前工作区已有 target，并标出当前 active target

受管工作区固定写入：

```text
<workdir>/.dexclub/
```

## 常用命令

初始化工作区：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar init /path/to/app.apk
java -jar cli/build/libs/dexclub-cli-all.jar init /path/to/classes.dex
java -jar cli/build/libs/dexclub-cli-all.jar init /path/to/AndroidManifest.xml
```

查看工作区状态：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar status /path/to/workdir
java -jar cli/build/libs/dexclub-cli-all.jar inspect /path/to/workdir --json
java -jar cli/build/libs/dexclub-cli-all.jar gc /path/to/workdir
```

切换和列出 target：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar switch a.apk
java -jar cli/build/libs/dexclub-cli-all.jar targets /path/to/workdir
java -jar cli/build/libs/dexclub-cli-all.jar targets /path/to/workdir --json
```

Dex 查询：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-class /path/to/workdir \
  --query-json '{"matcher":{"className":{"value":"Sample","matchType":"Contains","ignoreCase":true}}}' \
  --json
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-method-using-strings /path/to/workdir \
  --query-file /path/to/query.json
```

方法详情：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar inspect-method /path/to/workdir \
  --descriptor 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --include using-fields,callers,invokes \
  --json
```

代码导出：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-class-smali /path/to/workdir \
  --class Lcom/example/Sample; \
  --output /tmp/Sample.smali
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-method-smali /path/to/workdir \
  --method 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --output /tmp/Sample_method.smali
```

资源命令：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar manifest /path/to/workdir
java -jar cli/build/libs/dexclub-cli-all.jar res-table /path/to/workdir --json
java -jar cli/build/libs/dexclub-cli-all.jar decode-xml /path/to/workdir --path res/layout/activity_main.xml
java -jar cli/build/libs/dexclub-cli-all.jar resolve-res /path/to/workdir --id 0x7f0a0001
```

## CI

仓库当前提供：

- [.github/workflows/build-cli.yml](.github/workflows/build-cli.yml)

用途是按平台构建 CLI 分发产物，并在 tag 构建时上传发布附件。
