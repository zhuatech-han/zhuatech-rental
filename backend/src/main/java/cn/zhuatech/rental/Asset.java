// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 按序列号识别的设备及验收周转时间。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "asset")
public class Asset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "category_id", nullable = false)
  public Long categoryId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "daily_rate", nullable = false, precision = 14, scale = 2)
  public BigDecimal dailyRate;

  @Column(name = "deposit", nullable = false, precision = 14, scale = 2)
  public BigDecimal deposit;

  @Column(name = "accessories", nullable = false, length = 1000)
  public String accessories;

  @Column(name = "maintenance", nullable = false)
  public boolean maintenance;

  @Column(name = "ready_at", nullable = true)
  public Instant readyAt;

  @Column(name = "maintenance_note", nullable = false, length = 1000)
  public String maintenanceNote;
}
