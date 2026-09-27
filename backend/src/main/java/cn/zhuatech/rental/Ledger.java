// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 人工核实收付款台账；押金与收入分账。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "ledger")
public class Ledger {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "booking_id", nullable = false)
  public Long bookingId;

  @Column(name = "kind", nullable = false, length = 30)
  public String kind;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  public BigDecimal amount;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "note", nullable = false, length = 500)
  public String note;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy;
}
