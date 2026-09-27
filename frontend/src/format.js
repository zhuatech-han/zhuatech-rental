// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** datetime-local 输入按浏览器所在时区生成，不错误截断 UTC 日期。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function inputDate(date) {
  const d = new Date(date);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
/** 转换输入时间并拒绝无效或夏令时跳跃产生的本地时间。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function instant(value) {
  const d = new Date(value);
  if (!Number.isFinite(d.getTime()) || inputDate(d) !== value)
    throw new Error("INVALID_PERIOD");
  return d.toISOString();
}
/** 显示给定公司时区的日期。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dateTime(value, language, timezone) {
  return value
    ? new Intl.DateTimeFormat(language === "en" ? "en-GB" : "zh-CN", {
        timeZone: timezone,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(new Date(value))
    : "—";
}
