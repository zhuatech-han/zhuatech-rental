// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import jakarta.persistence.*;

/**
 * 租赁客户及部门归属。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
 */
@Entity
@Table(name = "customer")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "contact", nullable = false, length = 200)
  public String contact;

  @Column(name = "notes", nullable = false, length = 1000)
  public String notes;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;
}
