// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 租赁客户、分类与序列号设备；按部门限制读写。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MasterService {
  final Store db;
  final AccessService access;

  public MasterService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 主数据编辑输入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String name,
      String nameEn,
      String code,
      String contact,
      String notes,
      Long categoryId,
      Long departmentId,
      BigDecimal dailyRate,
      BigDecimal deposit,
      String accessories,
      Boolean maintenance,
      String maintenanceNote) {}

  /** 返回当前数据范围中的主数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> list(String type) {
    access.require(type.equals("customers") ? "customer.read" : "asset.read");
    return switch (type) {
      case "customers" ->
          db.all(Customer.class).stream().filter(v -> access.visible(v.departmentId)).toList();
      case "assets" ->
          db.all(Asset.class).stream().filter(v -> access.visible(v.departmentId)).toList();
      case "categories" -> db.all(Category.class);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 保存主数据；被占用设备不允许跨部门迁移，报价快照不受新价格影响。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    access.require(type.equals("customers") ? "customer.write" : "asset.write");
    Long dept = v.departmentId == null ? access.current().departmentId : v.departmentId;
    access.department(dept);
    db.get(Department.class, dept);
    Object result;
    switch (type) {
      case "customers" -> {
        var r = id == null ? new Customer() : db.get(Customer.class, id);
        if (id != null) {
          access.department(r.departmentId);
          if (!r.departmentId.equals(dept)) throw new Problem(409, "DEPARTMENT_IMMUTABLE");
        }
        r.name = AdminService.text(v.name, 120);
        r.contact = optional(v.contact, 200);
        r.notes = optional(v.notes, 1000);
        r.departmentId = dept;
        result = id == null ? db.save(r) : r;
      }
      case "categories" -> {
        var r = id == null ? new Category() : db.get(Category.class, id);
        r.name = AdminService.text(v.name, 120);
        r.nameEn = AdminService.text(v.nameEn, 120);
        result = id == null ? db.save(r) : r;
      }
      case "assets" -> {
        var r = id == null ? new Asset() : db.lock(Asset.class, id);
        if (id != null) {
          access.department(r.departmentId);
          if (!r.departmentId.equals(dept)) throw new Problem(409, "DEPARTMENT_IMMUTABLE");
        }
        r.name = AdminService.text(v.name, 120);
        r.code = AdminService.text(v.code, 60);
        r.categoryId = db.get(Category.class, v.categoryId).id;
        r.departmentId = dept;
        r.dailyRate = RentalPolicy.money(v.dailyRate);
        r.deposit = RentalPolicy.money(v.deposit);
        r.accessories = optional(v.accessories, 1000);
        if (id == null) {
          r.maintenance = false;
          r.maintenanceNote = "";
        }
        result = id == null ? db.save(r) : r;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("MASTER_UPDATE_" + type, id == null ? "NEW" : id, dept);
    return result;
  }

  /** 删除未被引用的主数据，禁止破坏历史单据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require(type.equals("customers") ? "customer.write" : "asset.write");
    Object record =
        switch (type) {
          case "customers" -> db.get(Customer.class, id);
          case "categories" -> db.get(Category.class, id);
          case "assets" -> db.lock(Asset.class, id);
          default -> throw new Problem(404, "NOT_FOUND");
        };
    if (record instanceof Customer r) access.department(r.departmentId);
    if (record instanceof Asset r) access.department(r.departmentId);
    db.delete(record);
    access.audit("MASTER_DELETE_" + type, id, access.current().departmentId);
  }

  /** 验收维修设备后释放；在租设备不能手工释放。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Asset maintenance(Long id, boolean blocked, String note) {
    access.require("asset.write");
    var a = db.lock(Asset.class, id);
    access.department(a.departmentId);
    if (!blocked
        && !db.query(
                BookingLine.class,
                "from BookingLine l where l.assetId=?1 and l.returnedAt is null and l.bookingId in (select b.id from Booking b where b.status in ('OUT','PARTIAL'))",
                id)
            .isEmpty()) throw new Problem(409, "ASSET_STILL_OUT");
    a.maintenance = blocked;
    a.maintenanceNote = AdminService.text(note, 1000);
    access.audit(blocked ? "MAINTENANCE_HOLD" : "MAINTENANCE_RELEASE", id, a.departmentId);
    return a;
  }

  /** 可选文本校验，避免超长或 null 进入数据库。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(String value, int max) {
    if (value == null) return "";
    if (value.length() > max) throw new Problem(400, "INVALID_INPUT");
    return value.trim();
  }
}
