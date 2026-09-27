// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 租赁单；固定币种与计费规则快照。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "booking")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "number", nullable = false, length = 60)
  public String number;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "start_at", nullable = false)
  public Instant startAt;

  @Column(name = "end_at", nullable = false)
  public Instant endAt;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "currency", nullable = false, length = 3)
  public String currency;

  @Column(name = "turnaround_hours", nullable = false)
  public int turnaroundHours;

  @Column(name = "notes", nullable = false, length = 1000)
  public String notes;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy;
}
