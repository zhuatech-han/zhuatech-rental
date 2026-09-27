// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import java.math.*;
import java.time.*;

/** 24 小时计费、金额和日期区间的独立业务规则。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class RentalPolicy {
  private RentalPolicy() {}

  /** 租期按持续时长计费，跨夏令时也不按日历日混算。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long days(Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start))
      throw new Problem(400, "INVALID_PERIOD");
    var duration = Duration.between(start, end);
    long seconds = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
    return Math.max(1, (seconds + 86399) / 86400);
  }

  /** 计算两位小数租金，不使用浮点。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal rent(BigDecimal rate, Instant start, Instant end) {
    return money(rate).multiply(BigDecimal.valueOf(days(start, end)));
  }

  /** 验证输入金额非负、两位小数且在数据库容量内。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal v) {
    if (v == null
        || v.signum() < 0
        || v.scale() > 2
        || v.compareTo(new BigDecimal("9999999999.99")) > 0)
      throw new Problem(400, "INVALID_AMOUNT");
    return v.setScale(2);
  }

  /** 左闭右开区间，前单周转结束时允许后单开始。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overlap(Instant start, Instant end, Instant otherStart, Instant otherEnd) {
    return start.isBefore(otherEnd) && otherStart.isBefore(end);
  }

  /** 逾期不足一天按一天；准时归还费用为零。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal late(BigDecimal rate, Instant due, Instant actual) {
    return actual.isAfter(due) ? rent(rate, due, actual) : BigDecimal.ZERO.setScale(2);
  }
}
