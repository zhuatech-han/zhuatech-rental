// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { inputDate, instant, dateTime } from "./format.js";
import { translate, words, errors } from "./i18n.js";
test("local datetime round-trip preserves time across month boundary", () => {
  const d = new Date(2026, 9, 1, 10, 30);
  assert.equal(instant(inputDate(d)), d.toISOString());
});
test("invalid local datetime is rejected", () =>
  assert.throws(() => instant("not-a-date")));
test("company timezone formats independent of browser zone", () => {
  assert.match(
    dateTime("2026-10-01T00:00:00Z", "en", "Asia/Shanghai"),
    /08:00/,
  );
});
test("all operation labels and errors have Chinese and English", () => {
  for (const values of Object.values({ ...words, ...errors })) {
    assert.equal(values.length, 2);
    assert.ok(values.every((x) => typeof x === "string" && x.length));
  }
  assert.equal(translate("return", "en"), "Check in");
});
