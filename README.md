# frpc Android Client

这是一个最小 Java Android App：把官方 frp 的 `frpc` 打包进 APK，由前台服务通过
`ProcessBuilder` 执行 `frpc -c frpc.toml`。它支持 XTCP visitor，也可以把手机上的
本地 TCP 服务通过 frps 暴露出去。不实现 VPNService、不重写 frp 协议，也不需要 root。

App 一次运行一种模式：

- `XTCP 访问端`：手机监听 `127.0.0.1:bindPort`，访问其他 frpc 发布的 XTCP 服务。
- `TCP 暴露服务`：frps 在公网 `remotePort` 监听，转发到手机的 `localIP:localPort`。
- `XTCP 暴露服务`：手机发布 XTCP proxy，由另一台 frpc visitor 通过 secretKey 访问。

当前版本为 `0.2.0`。三个模式共用同一个前台服务和 frpc 子进程，切换模式前应先点击
“停止”，修改配置后再点击“启动”。

## 环境

- Android `minSdk 23`
- `targetSdk 36`
- `compileSdk 36.1`
- Android Gradle Plugin 9.2.0、Gradle 9.4.1、JDK 17
- 支持 `arm64-v8a` 真机和 `x86_64` Android 模拟器

在 Arch Linux 上建议使用：

```bash
sudo pacman -S jdk17-openjdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME="$HOME/Android/Sdk"
```

通过 Android Studio SDK Manager 安装 Android SDK Platform 36.1 和 Build Tools 36.x。

## 已包含的 frpc

仓库已包含 frp 官方 `v0.61.0` Android arm64 发布包中的 `frpc`：

```text
app/src/main/assets/bin/frpc-arm64-v8a
SHA-256: 619c5fb5619aaffeda7d87bab49ee645eb599aff9afadbb1e1f75e0a56f8ccef
```

对应 Apache 2.0 许可证位于
`app/src/main/assets/licenses/frp-LICENSE`。`v0.61.0` 支持本 MVP 使用的 TOML XTCP
visitor 和 `keepTunnelOpen`。

frp `v0.69.x` 的兼容策略保证 `v0.69.x` frps 与 `v0.61.x` frpc 互通。混用版本时，
应先升级 frps。生产部署仍建议让 VPS、被访问端和 Android visitor 使用同一兼容版本。

## 编译 frpc

在 [fatedier/frp](https://github.com/fatedier/frp) 源码根目录执行：

```bash
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags "-s -w" \
  -tags "frpc,noweb" \
  -o frpc-arm64-v8a \
  ./cmd/frpc
```

将新产物覆盖到：

```text
app/src/main/assets/bin/frpc-arm64-v8a
```

然后构建：

```bash
chmod +x app/src/main/assets/bin/frpc-arm64-v8a
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

截至 2026 年 6 月 6 日，frp `v0.69.1` 源码在 Android 目标上会因间接依赖
`github.com/wlynxg/anet` 引用 `net.zoneCache` 而链接失败，而且该版本不再发布官方
Android 资产。因此本 MVP 固定使用最后仍有官方 Android arm64 发行包且满足功能要求的
`v0.61.0`。上面的命令按需求保留，适用于可正常构建 Android 目标的 frp 版本；升级前
应先实际执行该命令并测试 XTCP。

仓库还包含从同一 `v0.61.0` 标签源码用 Go 1.22.12 构建的纯 Go 静态 x86_64
二进制，供 Android Studio 的 x86_64 模拟器调试：

```bash
GOTOOLCHAIN=go1.22.12 GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags "-s -w" \
  -tags "frpc,noweb" \
  -o frpc-x86_64 \
  ./cmd/frpc
```

当前 `frpc-x86_64` 的 SHA-256：
`728f72c3d04705f6775ab906c58abe811d7381ff82b6d312e3f61ddce474190b`。

构建任务会把两个二进制分别以
`lib/arm64-v8a/libfrpc.so` 和 `lib/x86_64/libfrpc.so` 打包。这样做不是把 frpc
改造成 JNI 库；它仍由 `ProcessBuilder` 作为独立进程启动。x86_64 产物仅用于模拟器；
发布给手机的 APK 可以通过 ABI split 排除它以减小体积。

纯 Go Linux x86_64 二进制不会调用 Android netd。App 检测到 x86_64 Android
Emulator 时，会在生成的配置中自动加入 `dnsServer = "10.0.2.3"`；arm64 真机生成的
配置仍严格保持前述格式。

## Android 10+ 的可执行文件限制

Android 10（API 29）开始禁止 App 对可写私有目录中的文件调用 `execve()`。因此，
单纯把 assets 复制到 `filesDir/bin/frpc` 后执行，在现代 Android 上无法工作。

本项目仍会按 MVP 要求把 assets 复制到 `filesDir/bin/frpc` 并设置执行权限，但：

- Android 6 到 Android 9 从 `filesDir/bin/frpc` 执行。
- Android 10 及以上从安装时提取的只读 APK 原生库目录执行 `libfrpc.so`。
- 配置始终位于 `filesDir/frp/frpc.toml`，不会从外部存储执行任何内容。

参考：[Android 10 行为变更：移除 App 主目录执行权限](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)。

## App 生成的 visitor 配置

```toml
serverAddr = "用户输入"
serverPort = 7000

auth.method = "token"
auth.token = "用户输入"

[[visitors]]
name = "phone_xtcp_visitor"
type = "xtcp"
serverName = "home_ssh"
secretKey = "用户输入"
bindAddr = "127.0.0.1"
bindPort = 6000
keepTunnelOpen = false
```

token 和 secretKey 会写入 App 私有目录的配置文件。界面日志会对这两个值做精确替换
脱敏，App 本身不向 Logcat 输出配置或命令行。

## TCP 暴露手机服务

例如手机上有一个 HTTP 服务监听 `127.0.0.1:8080`，在 App 中选择
`TCP 暴露服务`：

```text
proxyName: phone_web
localIP: 127.0.0.1
localPort: 8080
remotePort: 18080
```

App 生成：

```toml
serverAddr = "公网 frps 地址"
serverPort = 7000

auth.method = "token"
auth.token = "同一个 token"

[[proxies]]
name = "phone_web"
type = "tcp"
localIP = "127.0.0.1"
localPort = 8080
remotePort = 18080
```

启动成功后，外部设备访问：

```text
http://公网 VPS 地址:18080
```

需要同时满足：

- VPS 防火墙和云安全组允许 TCP 18080。
- frps 没有通过 `allowPorts` 禁止该端口。
- 手机上的目标 App 确实在 `localIP:localPort` 监听。
- Android 可能限制其他 App 仅绑定自身进程可见的接口；普通 TCP loopback 服务通常可用。

TCP 暴露是公网直连，任何能访问该端口的人都能尝试连接。应由目标服务自行提供认证和
TLS，或者使用 XTCP 暴露模式。

## XTCP 暴露手机服务

例如手机本地服务监听 `127.0.0.1:8022`，选择 `XTCP 暴露服务`：

```text
proxyName: phone_ssh
secretKey: 自定义密钥
localIP: 127.0.0.1
localPort: 8022
```

另一台机器使用以下 visitor：

```toml
serverAddr = "公网 frps 地址"
serverPort = 7000

auth.method = "token"
auth.token = "同一个 token"

[[visitors]]
name = "phone_ssh_visitor"
type = "xtcp"
serverName = "phone_ssh"
secretKey = "同一个 secretKey"
bindAddr = "127.0.0.1"
bindPort = 6000
```

然后在另一台机器访问 `127.0.0.1:6000`。

## 被访问端 Linux frpc.toml

```toml
serverAddr = "公网 frps 地址"
serverPort = 7000

auth.method = "token"
auth.token = "同一个 token"

[[proxies]]
name = "home_ssh"
type = "xtcp"
secretKey = "同一个 secretKey"
localIP = "127.0.0.1"
localPort = 22
```

VPS 上的 frps、Linux 被访问端 frpc、Android visitor 应使用兼容的 frp 版本。

## 手动测试

1. 编译 frpc 并放入 assets，执行 `./gradlew assembleDebug`。
2. 执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。
3. 启动 VPS 上的 frps；测试 visitor 时，再启动被访问端 Linux 机器上的 frpc。
4. 打开 App，选择运行模式并填写对应参数。
5. Android 13 及以上允许通知权限，然后点击“启动”。
6. 确认通知栏显示“frpc 正在运行”，界面日志显示已连接 frps。
7. visitor 模式下，在手机客户端连接 `127.0.0.1:6000`。
8. TCP 暴露模式下，从外部连接 `VPS:remotePort`；XTCP 暴露模式下从另一台 visitor 连接。
9. 点击 App 或通知中的“停止”，确认通知消失且再次连接
   `127.0.0.1:6000` 失败。
10. 可用 `adb shell dumpsys activity services com.example.frpcvisitor` 检查服务状态。

如果日志提示 APK 中缺少 frpc，说明二进制是在 APK 构建完成后才放入 assets；需要重新
执行 `assembleDebug` 并重装 APK。

## 前台服务说明

Android 14 及以上要求声明前台服务类型。本项目使用 `specialUse` 及
`FOREGROUND_SERVICE_SPECIAL_USE`，用途说明已写入 Manifest。若发布到 Google Play，
该用途还需要通过 Play Console 审核。

参考：[Android 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)。
