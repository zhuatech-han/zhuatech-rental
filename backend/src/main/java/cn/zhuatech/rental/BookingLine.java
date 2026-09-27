// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单台器材价格快照与逐台归还验收。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "booking_line")
public class BookingLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "booking_id", nullable = false)
  public Long bookingId;

  @Column(name = "asset_id", nullable = false)
  public Long assetId;

  @Column(name = "asset_code", nullable = false, length = 60)
  public String assetCode;

  @Column(name = "asset_name", nullable = false, length = 120)
  public String assetName;

  @Column(name = "accessories", nullable = false, length = 1000)
  public String accessories;

  @Column(name = "daily_rate", nullable = false, precision = 14, scale = 2)
  public BigDecimal dailyRate;

  @Column(name = "deposit", nullable = false, precision = 14, scale = 2)
  public BigDecimal deposit;

  @Column(name = "returned_at", nullable = true)
  public Instant returnedAt;

  @Column(name = "late_fee", nullable = false, precision = 14, scale = 2)
  public BigDecimal lateFee;

  @Column(name = "damage_fee", nullable = false, precision = 14, scale = 2)
  public BigDecimal damageFee;

  @Column(name = "inspection", nullable = false, length = 1000)
  public String inspection;
}
