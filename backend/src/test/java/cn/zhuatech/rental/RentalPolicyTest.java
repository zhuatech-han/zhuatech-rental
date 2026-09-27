// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 计费、区间与导出安全规则测试。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class RentalPolicyTest {
  final Instant start = Instant.parse("2026-10-01T10:00:00Z");

  @Test
  void exactTwoDays() {
    assertEquals(
        new BigDecimal("40.00"),
        RentalPolicy.rent(new BigDecimal("20"), start, start.plusSeconds(172800)));
  }

  @Test
  void extraSecondChargesNewDay() {
    assertEquals(3, RentalPolicy.days(start, start.plusSeconds(172801)));
  }

  @Test
  void sameMinuteStillOneDay() {
    assertEquals(1, RentalPolicy.days(start, start.plusSeconds(1)));
  }

  @Test
  void invalidPeriodRejected() {
    assertThrows(Problem.class, () -> RentalPolicy.days(start, start));
  }

  @Test
  void adjacentIntervalsDoNotOverlap() {
    assertFalse(
        RentalPolicy.overlap(
            start, start.plusSeconds(3600), start.plusSeconds(3600), start.plusSeconds(7200)));
  }

  @Test
  void crossingIntervalsOverlap() {
    assertTrue(
        RentalPolicy.overlap(
            start, start.plusSeconds(3600), start.plusSeconds(3599), start.plusSeconds(7200)));
  }

  @Test
  void onTimeHasNoLateFee() {
    assertEquals(new BigDecimal("0.00"), RentalPolicy.late(new BigDecimal("20"), start, start));
  }

  @Test
  void latePartDayIsCharged() {
    assertEquals(
        new BigDecimal("20.00"),
        RentalPolicy.late(new BigDecimal("20"), start, start.plusSeconds(1)));
  }

  @Test
  void rejectThirdDecimal() {
    assertThrows(Problem.class, () -> RentalPolicy.money(new BigDecimal("1.001")));
  }

  @Test
  void rejectNegativeMoney() {
    assertThrows(Problem.class, () -> RentalPolicy.money(new BigDecimal("-1")));
  }

  @Test
  void rejectOversizedMoney() {
    assertThrows(Problem.class, () -> RentalPolicy.money(new BigDecimal("99999999999")));
  }

  @Test
  void csvNeutralizesFormula() {
    assertEquals("\"'=SUM(1,2)\"", ApiController.csv("=SUM(1,2)"));
    assertEquals("\"a\"\"b\"", ApiController.csv("a\"b"));
  }
}
