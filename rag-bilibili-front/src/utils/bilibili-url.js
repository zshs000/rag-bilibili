export function isTrustedBilibiliJumpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "www.bilibili.com";
  } catch {
    return false;
  }
}
