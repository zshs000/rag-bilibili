# AGENTS.md - 字幕清洗评估实验室

本文档适用于 `subtitle-cleaning-eval-lab/`。

## 用途

这是一个独立的 Java 17 实验室，用来在不启动完整 Spring Boot 后端的情况下测试字幕清洗规则。

## 规则

- 把这里视为评估沙盒，不要当成生产代码的唯一事实来源。
- 如果某条清洗规则在这里验证通过，并且需要影响正式系统，要在明确的后续改动中同步到 `rag-bilibili-server/src/main/resources/application.yml` 或相关后端代码。
- 除非用户要求，不要提交生成的报告和 `outputs/`。
- 不要把 B 站凭证写入已提交文件。
- 如果实验室行为和后端行为不一致，先说明差异，再决定是否复制规则。

## 验证

```bash
mvn -q compile
mvn -q exec:java "-Dexec.args=--url=<视频URL> --sessdata=<SESSDATA> --bili-jct=<JCT> --buvid3=<BUVID>"
```

PowerShell 辅助脚本：

```powershell
.\run-sample.ps1 -Url "https://www.bilibili.com/video/BV1xxx"
```

