// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import java.math.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次启动创建权限、管理员与可选虚构演示主数据；重启不重置密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;
  final boolean demo;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${rental.admin-username}") String username,
      @Value("${rental.admin-password}") String password,
      @Value("${rental.seed-demo}") boolean demo) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.demo = demo;
  }

  /** 幂等初始化，不覆盖已有业务数据或凭证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
    var department = new Department();
    department.name = "总部 / Main office";
    db.save(department);
    var names =
        Map.ofEntries(
            Map.entry("dashboard", "工作台 / Overview"),
            Map.entry("customer.read", "查看客户 / View customers"),
            Map.entry("customer.write", "编辑客户 / Edit customers"),
            Map.entry("asset.read", "查看器材 / View equipment"),
            Map.entry("asset.write", "编辑器材 / Edit equipment"),
            Map.entry("booking.read", "查看租赁单 / View rentals"),
            Map.entry("booking.write", "预约与交接 / Manage rentals"),
            Map.entry("finance", "收款与结算 / Finance"),
            Map.entry("report", "经营报表 / Reports"),
            Map.entry("audit", "审计记录 / Audit"),
            Map.entry("admin", "系统管理 / Administration"));
    for (var e : new TreeMap<>(names).entrySet()) {
      var r = new Permission();
      r.code = e.getKey();
      r.name = e.getValue();
      db.save(r);
    }
    var admin = new AccessRole();
    admin.name = "管理员 / Administrator";
    admin.scope = "ALL";
    admin.permissions = new HashSet<>(names.keySet());
    db.save(admin);
    var staff = new AccessRole();
    staff.name = "租赁专员 / Rental operator";
    staff.scope = "DEPARTMENT";
    staff.permissions = new HashSet<>(names.keySet());
    staff.permissions.remove("admin");
    db.save(staff);
    var a = new Account();
    a.username = username;
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = department.id;
    a.roleId = admin.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "工作台", "Overview", "dashboard"},
      {"bookings", "租赁订单", "Rentals", "booking.read"},
      {"availability", "排期与可用性", "Availability", "asset.read"},
      {"assets", "器材台账", "Equipment", "asset.read"},
      {"customers", "客户", "Customers", "customer.read"},
      {"categories", "器材分类", "Categories", "asset.read"},
      {"reports", "经营报表", "Reports", "report"},
      {"audit", "操作审计", "Audit trail", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles & permissions", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of(
                "currency",
                "CNY",
                "timezone",
                "Asia/Shanghai",
                "turnaroundHours",
                "2",
                "companyName",
                "知华科技设备租赁")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    for (var item :
        new String[][] {
          {"body", "机身状态", "Body condition"},
          {"accessories", "附件核对", "Accessories checked"},
          {"function", "功能检测", "Function test"}
        }) {
      var d = new DictionaryEntry();
      d.type = "inspection";
      d.code = item[0];
      d.name = item[1];
      d.nameEn = item[2];
      db.save(d);
    }
    if (demo) {
      var c = new Customer();
      c.name = "示例客户 / Demo Studio";
      c.contact = "demo@example.invalid";
      c.notes = "虚构学习数据 / Fictional learning data";
      c.departmentId = department.id;
      db.save(c);
      String[][] cats = {{"摄影器材", "Cameras"}, {"灯光设备", "Lighting"}, {"音响设备", "Audio"}};
      for (int i = 0; i < cats.length; i++) {
        var cat = new Category();
        cat.name = cats[i][0];
        cat.nameEn = cats[i][1];
        db.save(cat);
        for (int j = 1; j <= 3; j++) {
          var eq = new Asset();
          eq.code = (i == 0 ? "CAM" : i == 1 ? "LGT" : "AUD") + "-00" + j;
          eq.name = (i == 0 ? "摄影机套件" : i == 1 ? "灯光套件" : "音响套件") + " " + j;
          eq.categoryId = cat.id;
          eq.departmentId = department.id;
          eq.dailyRate = BigDecimal.valueOf(i == 0 ? 200 : i == 1 ? 80 : 120).setScale(2);
          eq.deposit = BigDecimal.valueOf(i == 0 ? 1000 : 500).setScale(2);
          eq.accessories = "电源、连接线、运输箱 / Power supply, cables, case";
          eq.maintenanceNote = "";
          db.save(eq);
        }
      }
    }
  }
}
