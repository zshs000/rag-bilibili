# 字幕清洗评估实验室

独立的字幕清洗测试环境，用于评估和调优字幕清洗算法参数，**无需启动完整的 Spring Boot 后端**。

## 用途

| 场景 | 说明 |
|------|------|
| **参数调优** | 测试不同的正则、关键词、阈值组合，观察清洗效果 |
| **效果评估** | 对比清洗前后的字幕内容，生成评估报告 |
| **新规则验证** | 新增规则前先在这里验证，避免直接改正式项目 |
| **样本调试** | 针对特定视频调试字幕读取和清洗逻辑 |

## 核心参数

本实验室可独立调整以下参数，**验证效果后再同步到正式项目**：

```java
// 强模式正则 - 命中即删
strongPatterns: 10 条

// 弱关键词 - 单行命中 ≥ threshold 才删
weakKeywords: 22 个
weakKeywordThreshold: 2

// 可疑模式 - 用于滑动窗口扩判
suspiciousPatterns: 10 条

// 窗口配置
maxWindowSize: 3
conditionalExpandTo3: true
```

## 使用方法

### 1. 单视频测试

```powershell
# 使用 PowerShell 脚本
.\run-sample.ps1 -Url "https://www.bilibili.com/video/BV1xxx"
```

### 2. 批量评估

```powershell
# 编译
mvn -q compile

# 运行评估，输出报告
mvn -q exec:java "-Dexec.args=--url=<视频URL> --sessdata=<SESSDATA> --bili-jct=<JCT> --buvid3=<BUVID>"
```

### 3. Python 快速检查（偷懒工具）

不想等 Java 编译？只想快速看一眼某个视频的字幕接口返回了啥？

```bash
# 修改 inspect_subtitle.py 中的 BVID 和凭证，然后直接跑
python inspect_subtitle.py
```

**特点**：改一下 BVID，秒出结果，不用编译，不用等启动。适合「我就看一眼」的场景。

## 工作流

```
┌─────────────────────────────────────────────────────────────┐
│  1. 在本实验室调整参数                                        │
│     - 修改 SubtitleCleaningProperties 中的正则/关键词/阈值    │
│     - 运行测试，观察 report.txt 输出                         │
├─────────────────────────────────────────────────────────────┤
│  2. 评估清洗效果                                              │
│     - 检查 outputs/ 目录下的清洗结果                          │
│     - 确认误删/漏删情况                                       │
├─────────────────────────────────────────────────────────────┤
│  3. 同步到正式项目                                            │
│     - 将验证通过的参数写入 rag-bilibili-server 的配置文件      │
│     - 更新 SubtitleCleaningTransformer 相关代码               │
└─────────────────────────────────────────────────────────────┘
```

## 目录结构

```
subtitle-cleaning-eval-lab/
├── pom.xml                          # 独立 Maven 项目
├── run-sample.ps1                   # PowerShell 运行脚本
├── inspect_subtitle.py              # Python 字幕检查工具
├── report.txt                       # 评估报告输出
├── outputs/                         # 清洗结果输出
└── src/
    └── com/example/subtitleeval/
        ├── cleaning/
        │   ├── SubtitleCleaner.java           # 清洗逻辑
        │   ├── SubtitleCleaningProperties.java # 参数配置
        │   └── CleaningTrace.java             # 清洗轨迹
        ├── reader/
        │   ├── BilibiliDocumentReader.java    # 字幕读取
        │   ├── BilibiliCredentials.java       # 凭证
        │   └── BilibiliResource.java          # 资源
        └── runner/
            ├── SubtitleCleaningRunner.java    # 主运行器
            └── ConsoleReportFormatter.java    # 报告格式化
```

## 注意事项

- 本项目的参数配置与正式项目 **独立维护**
- 调整参数后需要 **重新编译** 才能生效
- 测试凭证（SESSDATA 等）会过期，需要定期更新
- `outputs/` 和 `report.txt` 已被 `.gitignore` 忽略
