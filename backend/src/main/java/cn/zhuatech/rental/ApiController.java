// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 分页业务接口、管理员接口及同范围经营导出。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Store db;
  final AccessService access;
  final MasterService masters;
  final AdminService admin;
  final RentalService rentals;
  final Clock clock;

  public ApiController(
      Store db,
      AccessService access,
      MasterService masters,
      AdminService admin,
      RentalService rentals,
      Clock clock) {
    this.db = db;
    this.access = access;
    this.masters = masters;
    this.admin = admin;
    this.rentals = rentals;
    this.clock = clock;
  }

  /** 分页、搜索、状态过滤及排序；仅返回权限范围中的行。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lists/{resource}")
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "false") boolean desc,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 100 || search.length() > 200)
      throw new Problem(400, "INVALID_PAGE");
    List<?> rows =
        switch (resource) {
          case "assets", "categories", "customers" -> masters.list(resource);
          case "bookings" -> rentals.list();
          case "audit" -> {
            access.require("audit");
            yield db.all(AuditEvent.class).stream()
                .filter(a -> access.visible(a.departmentId))
                .toList();
          }
          default -> admin.list(resource);
        };
    String q = search.toLowerCase(Locale.ROOT);
    var filtered =
        rows.stream()
            .filter(r -> searchText(r).toLowerCase(Locale.ROOT).contains(q))
            .filter(
                r ->
                    status.isEmpty()
                        || r instanceof Booking b && b.status.equals(status)
                        || r instanceof Asset a
                            && status.equals(a.maintenance ? "MAINTENANCE" : "READY"))
            .sorted(
                (a, b) -> {
                  int n =
                      sort.equals("id")
                          ? Long.compare(id(a), id(b))
                          : sortText(a, sort).compareTo(sortText(b, sort));
                  return desc ? -n : n;
                })
            .toList();
    return Map.of(
        "items",
        filtered.stream().skip((long) page * size).limit(size).toList(),
        "total",
        filtered.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 操作端参考数据和受控非秘密参数。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    access.current();
    return Map.of(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "categories",
        db.all(Category.class),
        "settings",
        db.all(SystemSetting.class),
        "dictionaries",
        db.all(DictionaryEntry.class));
  }

  /** 新建主数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object masterCreate(@PathVariable String type, @RequestBody MasterService.Input v) {
    return masters.save(type, null, v);
  }

  /** 修改主数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object masterUpdate(
      @PathVariable String type, @PathVariable Long id, @RequestBody MasterService.Input v) {
    return masters.save(type, id, v);
  }

  /** 删除未引用主数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Map<String, Boolean> masterDelete(@PathVariable String type, @PathVariable Long id) {
    masters.delete(type, id);
    return Map.of("ok", true);
  }

  /** 维修占用或验收释放。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/assets/{id}/maintenance")
  public Object maintenance(@PathVariable Long id, @RequestBody Map<String, String> v) {
    if (!Set.of("true", "false").contains(v.get("blocked")))
      throw new Problem(400, "INVALID_INPUT");
    return masters.maintenance(id, Boolean.parseBoolean(v.get("blocked")), v.get("note"));
  }

  /** 新建管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminUpdate(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除未引用管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Map<String, Boolean> adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }

  /** 读取租赁单详细信息。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/bookings/{id}")
  public Object detail(@PathVariable Long id) {
    return rentals.detail(id);
  }

  /** 创建不占用库存的报价草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/bookings")
  public Object create(@RequestBody RentalService.Draft v) {
    return rentals.draft(null, v);
  }

  /** 仅修改未发生收款的草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/bookings/{id}")
  public Object update(@PathVariable Long id, @RequestBody RentalService.Draft v) {
    return rentals.draft(id, v);
  }

  /** 删除草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/bookings/{id}")
  public Map<String, Boolean> delete(@PathVariable Long id) {
    rentals.delete(id);
    return Map.of("ok", true);
  }

  /** 原子执行预约、出库、归还、续租、退款或结单。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/bookings/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody RentalService.Action v) {
    return rentals.action(id, action, v);
  }

  /** 按所选租期获取真实可用设备。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/availability")
  public Object availability(@RequestParam Instant start, @RequestParam Instant end) {
    return rentals.availability(start, end);
  }

  /** 按币种分开显示收入台账、押金和未收余额，避免混合币种求和。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Map<String, Object> dashboard() {
    if (!access.role().permissions.contains("dashboard")) access.require("report");
    var bookings =
        db.all(Booking.class).stream().filter(b -> access.visible(b.departmentId)).toList();
    var assets = db.all(Asset.class).stream().filter(a -> access.visible(a.departmentId)).toList();
    var money = new TreeMap<String, Map<String, BigDecimal>>();
    for (var b : bookings) {
      if (Set.of("DRAFT", "CANCELLED").contains(b.status)) continue;
      var totals = rentals.totals(b);
      var group = money.computeIfAbsent(b.currency, k -> new TreeMap<>());
      for (String key : List.of("rentPaid", "depositHeld", "rentBalance", "netCash"))
        group.merge(key, totals.get(key), BigDecimal::add);
    }
    var overdue =
        bookings.stream()
            .filter(
                b ->
                    Set.of("OUT", "PARTIAL").contains(b.status)
                        && b.endAt.isBefore(clock.instant()))
            .toList();
    return Map.of(
        "assetCount",
        assets.size(),
        "maintenanceCount",
        assets.stream().filter(a -> a.maintenance).count(),
        "openRentals",
        bookings.stream()
            .filter(b -> Set.of("CONFIRMED", "OUT", "PARTIAL", "RETURNED").contains(b.status))
            .count(),
        "overdue",
        overdue,
        "money",
        money,
        "recent",
        bookings.stream()
            .sorted(Comparator.comparing((Booking b) -> b.createdAt).reversed())
            .limit(8)
            .toList());
  }

  /** 导出租赁结算报告，保留数据范围并防止表格公式注入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  @Transactional
  public ResponseEntity<byte[]> export() {
    access.require("report");
    var out =
        new StringBuilder(
            "\ufeffRental,Customer,Status,Currency,Start UTC,End UTC,Rent,Late fee,Damage fee,Rent paid,Deposit held,Rent balance,Net cash\r\n");
    for (var b : db.all(Booking.class)) {
      if (!access.visible(b.departmentId)) continue;
      var t = rentals.totals(b);
      var values =
          new ArrayList<Object>(
              List.of(
                  b.number,
                  db.get(Customer.class, b.customerId).name,
                  b.status,
                  b.currency,
                  b.startAt,
                  b.endAt));
      for (String key :
          List.of(
              "rent", "lateFee", "damageFee", "rentPaid", "depositHeld", "rentBalance", "netCash"))
        values.add(t.get(key));
      out.append(
              values.stream()
                  .map(ApiController::csv)
                  .collect(java.util.stream.Collectors.joining(",")))
          .append("\r\n");
    }
    access.audit("REPORT_EXPORT", "RENTALS", access.current().departmentId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rentals.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(out.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** 转义 CSV 文本并屏蔽 Excel 公式前缀。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object value) {
    String s = String.valueOf(value);
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private long id(Object v) {
    try {
      return ((Number) v.getClass().getField("id").get(v)).longValue();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private String searchText(Object v) {
    if (v instanceof Account a) return a.username + " " + a.displayName;
    if (v instanceof Booking b)
      return b.number + " " + b.status + " " + db.get(Customer.class, b.customerId).name;
    if (v instanceof Asset a) return a.code + " " + a.name;
    if (v instanceof Customer c) return c.name + " " + c.contact;
    if (v instanceof AuditEvent a) return a.actor + " " + a.action + " " + a.objectId;
    return sortText(v, "name") + " " + sortText(v, "code");
  }

  private String sortText(Object v, String field) {
    if (!Set.of("name", "code", "number", "createdAt").contains(field))
      throw new Problem(400, "INVALID_SORT");
    try {
      return String.valueOf(v.getClass().getField(field).get(v));
    } catch (NoSuchFieldException e) {
      return "";
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
