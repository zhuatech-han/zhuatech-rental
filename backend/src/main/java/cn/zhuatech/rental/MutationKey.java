// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import jakarta.persistence.*;

/**
 * 业务操作幂等标识，防止重复收款或重试重复动作。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信
 * zhuatech / zhuatech2
 */
@Entity
@Table(name = "mutation_key")
public class MutationKey {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "booking_id", nullable = false)
  public Long bookingId;

  @Column(name = "request_key", nullable = false, length = 80)
  public String requestKey;

  @Column(name = "action", nullable = false, length = 120)
  public String action;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;
}
