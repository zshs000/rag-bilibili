import fs from "node:fs";
import { chromium } from "playwright-core";

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const current = argv[i];
    if (!current.startsWith("--")) {
      continue;
    }
    const key = current.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      args[key] = "true";
      continue;
    }
    args[key] = next;
    i += 1;
  }
  return args;
}

function printJson(payload) {
  process.stdout.write(`${JSON.stringify(payload)}\n`);
}

function resolveExecutablePath() {
  const candidates = [
    process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
    process.env.CHROME_PATH,
    process.env.EDGE_PATH,
    process.env.PROGRAMFILES && `${process.env.PROGRAMFILES}\\Microsoft\\Edge\\Application\\msedge.exe`,
    process.env["PROGRAMFILES(X86)"] && `${process.env["PROGRAMFILES(X86)"]}\\Microsoft\\Edge\\Application\\msedge.exe`,
    process.env.PROGRAMFILES && `${process.env.PROGRAMFILES}\\Google\\Chrome\\Application\\chrome.exe`,
    process.env["PROGRAMFILES(X86)"] && `${process.env["PROGRAMFILES(X86)"]}\\Google\\Chrome\\Application\\chrome.exe`,
    process.env.LOCALAPPDATA && `${process.env.LOCALAPPDATA}\\Google\\Chrome\\Application\\chrome.exe`,
    "/usr/bin/microsoft-edge",
    "/usr/bin/microsoft-edge-stable",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
  ].filter(Boolean);

  return candidates.find((candidate) => fs.existsSync(candidate)) || null;
}

function buildCookies() {
  const cookies = [];
  const pairs = [
    ["SESSDATA", process.env.BILIBILI_SESSDATA],
    ["bili_jct", process.env.BILIBILI_BILI_JCT],
    ["buvid3", process.env.BILIBILI_BUVID3],
  ];

  for (const [name, value] of pairs) {
    if (!value) {
      continue;
    }
    cookies.push({
      name,
      value,
      domain: ".bilibili.com",
      path: "/",
      httpOnly: false,
      secure: true,
    });
  }
  return cookies;
}

async function detectSubtitleButton(page, timeoutMs) {
  const selector = ".bpx-player-ctrl-btn.bpx-player-ctrl-subtitle";
  const controlAreaSelector = ".bpx-player-control-bottom-right";
  const playerSelector = "#bilibili-player";
  const videoAreaSelector = ".bpx-player-video-area";
  const deadline = Date.now() + timeoutMs;
  let controlAreaSeen = false;

  while (Date.now() < deadline) {
    try {
      const videoArea = page.locator(videoAreaSelector).first();
      if (await videoArea.count()) {
        await videoArea.hover({ force: true }).catch(() => {});
      }
    } catch {
      // ignore hover errors
    }

    const state = await page.evaluate(
      ({ subtitleSelector, controlsSelector, playerSelectorInPage }) => ({
        hasSubtitleButton: !!document.querySelector(subtitleSelector),
        hasControlArea: !!document.querySelector(controlsSelector),
        hasPlayer: !!document.querySelector(playerSelectorInPage),
      }),
      {
        subtitleSelector: selector,
        controlsSelector: controlAreaSelector,
        playerSelectorInPage: playerSelector,
      },
    );

    if (state.hasSubtitleButton) {
      return {
        status: "HAS_SUBTITLE_BUTTON",
        reason: "subtitle button detected in player controls",
      };
    }

    if (state.hasPlayer && state.hasControlArea) {
      controlAreaSeen = true;
    }

    await page.waitForTimeout(350);
  }

  if (controlAreaSeen) {
    return {
      status: "NO_SUBTITLE_BUTTON",
      reason: "player controls loaded but subtitle button not found",
    };
  }

  return {
    status: "UNKNOWN",
    reason: "player controls not fully ready before timeout",
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const url = args.url;
  const timeoutMs = Number(args["timeout-ms"] || "10000");

  if (!url) {
    printJson({ status: "UNKNOWN", reason: "missing --url argument" });
    process.exit(1);
  }

  const executablePath = resolveExecutablePath();
  if (!executablePath) {
    printJson({ status: "UNKNOWN", reason: "no Chromium/Chrome/Edge executable found" });
    process.exit(1);
  }

  let browser;
  try {
    browser = await chromium.launch({
      headless: true,
      executablePath,
    });

    const context = await browser.newContext({
      userAgent:
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      viewport: { width: 1440, height: 900 },
    });

    const cookies = buildCookies();
    if (cookies.length > 0) {
      await context.addCookies(cookies);
    }

    const page = await context.newPage();
    await page.goto(url, {
      waitUntil: "domcontentloaded",
      timeout: timeoutMs,
    });

    const result = await detectSubtitleButton(page, timeoutMs);
    printJson(result);
  } catch (error) {
    printJson({
      status: "UNKNOWN",
      reason: error?.message || "probe failed",
    });
    process.exit(1);
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
}

await main();
