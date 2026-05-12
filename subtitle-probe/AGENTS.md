# AGENTS.md - 字幕探测工具

本文档适用于 `subtitle-probe/`。

## 用途

这是一个 Node.js + Playwright 辅助工具，由 Java 后端通过 `ProcessBuilder` 调用。当常规字幕 API 读取失败时，它用于探测 B 站页面是否存在字幕按钮。

## 规则

- 保持 stdout 的 JSON 结构稳定，后端解析依赖它。
- 诊断日志写到 stderr，不要写到 stdout。
- 除非目标行为确实需要登录态，否则基础探测不要强制要求凭证。
- 环境变量名称要和 README、后端配置保持兼容。
- 不要提交生成的 debug 截图，除非用户明确要求保留为样本或排查材料。

## 验证

```bash
npm install
node probe.mjs --url "https://www.bilibili.com/video/BV1xxx" --timeout-ms 8000
```

如果缺少 Chromium：

```bash
npx playwright install chromium
```

