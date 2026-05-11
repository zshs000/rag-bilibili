# 字幕按钮探测工具

基于 Playwright 的 B 站字幕按钮探测服务，**由后端 Java 通过 ProcessBuilder 调用**。

## 用途

当 `BilibiliDocumentReader` 首轮读取字幕失败时，后端会调用此工具探测页面是否存在字幕按钮，从而区分：

| 探测结果 | 含义 | 后续处理 |
|----------|------|----------|
| `HAS_SUBTITLE_BUTTON` | 视频有字幕，但接口暂未返回 | 触发有限重试 |
| `NO_SUBTITLE_BUTTON` | 视频未开通字幕 | 直接提示不支持 |
| `UNKNOWN` | 探测超时或异常 | 返回失败 |

## 工作原理

```
Java 后端 (VideoServiceImpl)
    │
    │ ProcessBuilder
    ▼
Node 子进程 (probe.mjs)
    │
    │ Playwright
    ▼
Chromium 无头浏览器
    │
    │ 检测 .bpx-player-ctrl-subtitle 元素
    ▼
stdout 输出 JSON 结果
```

## 使用方法

### 命令行调用

```bash
node probe.mjs --url "https://www.bilibili.com/video/BV1xxx" --timeout-ms 8000
```

### 输出格式

```json
{
  "status": "HAS_SUBTITLE_BUTTON",
  "reason": "subtitle button detected in player controls"
}
```

### 环境变量

| 变量 | 说明 |
|------|------|
| `BILIBILI_SESSDATA` | B 站登录凭证 |
| `BILIBILI_BILI_JCT` | B 站 CSRF Token |
| `BILIBILI_BUVID3` | B 站设备标识 |
| `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` | 指定 Chromium 路径 |
| `CHROME_PATH` | Chrome 路径 |
| `EDGE_PATH` | Edge 路径 |

## 与后端的集成

后端 `SubtitleProbeService` 通过 `ProcessBuilder` 调用此工具：

```java
// 伪代码
ProcessBuilder pb = new ProcessBuilder(
    "node", "subtitle-probe/probe.mjs",
    "--url", videoUrl,
    "--timeout-ms", "8000"
);
pb.environment().put("BILIBILI_SESSDATA", sessdata);
// ... 设置其他凭证
Process process = pb.start();
// 异步消费 stdout/stderr，避免管道阻塞
```

## 注意事项

- 需要安装 Node.js 和 Playwright 依赖：`npm install`
- 需要安装 Chromium：`npx playwright install chromium`，或使用系统已安装的 Chrome/Edge
- 凭证通过环境变量传入，后端调用时需设置 `BILIBILI_SESSDATA`、`BILIBILI_BILI_JCT`、`BILIBILI_BUVID3`

## 相关文档

- [字幕导入稳定性问题复盘](../docs/blog/从偶发无字幕到补偿探测链路：一次 B 站字幕导入问题的完整收敛过程.md)
- [字幕读取稳定性样本库](../docs/backend/字幕读取稳定性样本库.md)
