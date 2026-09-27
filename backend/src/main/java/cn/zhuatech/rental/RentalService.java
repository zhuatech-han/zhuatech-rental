// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 原子化预约、交接、逐台归还和押金结算；设备锁防止超售。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RentalService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public RentalService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 新建或修改未收款的草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long customerId, Instant startAt, Instant endAt, List<Long> assetIds, String notes) {}

  /** 写操作幂等标识以及各操作对应参数。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Action(
      String requestKey,
      Long lineId,
      BigDecimal amount,
      String kind,
      String reference,
      String note,
      Boolean inspectionPassed,
      Instant endAt) {}

  /** 获取有权限的订单列表。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Booking> list() {
    access.require("booking.read");
    return db.all(Booking.class).stream().filter(b -> access.visible(b.departmentId)).toList();
  }

  /** 读取订单详情和不可变台账。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    access.require("booking.read");
    var b = db.get(Booking.class, id);
    access.department(b.departmentId);
    return view(b);
  }

  /** 草稿不占库存，确认时重新校验并锁定全部设备。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> draft(Long id, Draft v) {
    access.require("booking.write");
    validatePeriod(v.startAt, v.endAt);
    var c = db.get(Customer.class, v.customerId);
    access.department(c.departmentId);
    if (v.assetIds == null
        || v.assetIds.isEmpty()
        || v.assetIds.size() > 100
        || new HashSet<>(v.assetIds).size() != v.assetIds.size())
      throw new Problem(400, "INVALID_ASSET_LIST");
    var b = id == null ? new Booking() : db.lock(Booking.class, id);
    if (id != null) {
      access.department(b.departmentId);
      state(b, "DRAFT");
      if (!entries(b.id).isEmpty()) throw new Problem(409, "PAYMENT_EXISTS");
      for (var l : lines(b.id)) db.delete(l);
    } else {
      b.number = "R-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
      b.createdAt = clock.instant();
      b.createdBy = access.current().username;
      b.status = "DRAFT";
      b.currency = setting("currency");
      b.turnaroundHours = Integer.parseInt(setting("turnaroundHours"));
    }
    b.customerId = c.id;
    b.departmentId = c.departmentId;
    b.startAt = v.startAt;
    b.endAt = v.endAt;
    b.notes = MasterService.optional(v.notes, 1000);
    if (id == null) db.save(b);
    for (Long assetId : v.assetIds.stream().sorted().toList()) {
      var a = db.lock(Asset.class, assetId);
      access.department(a.departmentId);
      if (!a.departmentId.equals(b.departmentId)) throw new Problem(400, "MIXED_DEPARTMENTS");
      var l = new BookingLine();
      l.bookingId = b.id;
      l.assetId = a.id;
      l.assetCode = a.code;
      l.assetName = a.name;
      l.accessories = a.accessories;
      l.dailyRate = a.dailyRate;
      l.deposit = a.deposit;
      l.lateFee = BigDecimal.ZERO.setScale(2);
      l.damageFee = BigDecimal.ZERO.setScale(2);
      l.inspection = "";
      db.save(l);
    }
    access.audit("BOOKING_DRAFT", b.id, b.departmentId);
    return view(b);
  }

  /** 查询指定日期设备可用性；逾期未归还设备保持阻塞，周转缓冲参与排期。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> availability(Instant start, Instant end) {
    access.require("asset.read");
    validatePeriod(start, end);
    int buffer = Integer.parseInt(setting("turnaroundHours"));
    return db.all(Asset.class).stream()
        .filter(a -> access.visible(a.departmentId))
        .map(
            a -> {
              String reason = unavailable(a, start, end.plusSeconds(buffer * 3600L), null);
              return Map.<String, Object>of(
                  "asset", a, "available", reason.isEmpty(), "reason", reason);
            })
        .toList();
  }

  /** 执行业务动作：订单锁、幂等检查、合法状态与金额验证在同一事务完成。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> action(Long id, String action, Action v) {
    access.require(Set.of("payment", "close").contains(action) ? "finance" : "booking.write");
    var b = db.lock(Booking.class, id);
    access.department(b.departmentId);
    String key = AdminService.text(v.requestKey, 80);
    if (!key.matches("[a-zA-Z0-9_-]{8,80}")) throw new Problem(400, "INVALID_REQUEST_KEY");
    String fingerprint = fingerprint(v.toString());
    var prior =
        db.query(
            MutationKey.class, "from MutationKey where bookingId=?1 and requestKey=?2", id, key);
    if (!prior.isEmpty()) {
      var old = prior.getFirst();
      if (!old.action.equals(action) || !old.fingerprint.equals(fingerprint))
        throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return view(b);
    }
    switch (action) {
      case "confirm" -> {
        state(b, "DRAFT");
        for (var l : lines(id)) {
          var a = db.lock(Asset.class, l.assetId);
          String reason =
              unavailable(a, b.startAt, b.endAt.plusSeconds(b.turnaroundHours * 3600L), id);
          if (!reason.isEmpty()) throw new Problem(409, reason);
        }
        b.status = "CONFIRMED";
      }
      case "checkout" -> {
        state(b, "CONFIRMED");
        if (b.startAt.isAfter(clock.instant())) throw new Problem(409, "NOT_STARTED");
        if (!b.endAt.isAfter(clock.instant())) throw new Problem(409, "PERIOD_EXPIRED");
        var balance = totals(b);
        if (balance.get("rentBalance").signum() != 0
            || balance.get("depositHeld").compareTo(balance.get("depositRequired")) < 0)
          throw new Problem(409, "PAYMENT_REQUIRED");
        for (var l : lines(id)) {
          var a = db.lock(Asset.class, l.assetId);
          String reason =
              unavailable(a, b.startAt, b.endAt.plusSeconds(b.turnaroundHours * 3600L), id);
          if (!reason.isEmpty()) throw new Problem(409, reason);
        }
        b.status = "OUT";
      }
      case "return" -> {
        state(b, "OUT", "PARTIAL");
        var l = db.get(BookingLine.class, v.lineId);
        if (!l.bookingId.equals(id) || l.returnedAt != null)
          throw new Problem(409, "INVALID_RETURN");
        var a = db.lock(Asset.class, l.assetId);
        l.returnedAt = clock.instant();
        l.lateFee = RentalPolicy.late(l.dailyRate, b.endAt, l.returnedAt);
        l.damageFee = RentalPolicy.money(v.amount == null ? BigDecimal.ZERO : v.amount);
        l.inspection = AdminService.text(v.note, 1000);
        if (v.inspectionPassed == null) throw new Problem(400, "INSPECTION_REQUIRED");
        a.readyAt = l.returnedAt.plusSeconds(b.turnaroundHours * 3600L);
        if (!v.inspectionPassed) {
          a.maintenance = true;
          a.maintenanceNote = l.inspection;
        }
        b.status = lines(id).stream().allMatch(x -> x.returnedAt != null) ? "RETURNED" : "PARTIAL";
      }
      case "extend" -> {
        state(b, "CONFIRMED", "OUT", "PARTIAL");
        validatePeriod(b.startAt, v.endAt);
        if (!v.endAt.isAfter(b.endAt) || !v.endAt.isAfter(clock.instant()))
          throw new Problem(400, "INVALID_EXTENSION");
        if (lines(id).stream().anyMatch(l -> l.returnedAt != null))
          throw new Problem(409, "PARTIAL_EXTENSION_UNSUPPORTED");
        for (var l : lines(id)) {
          var a = db.lock(Asset.class, l.assetId);
          String reason =
              unavailable(a, b.startAt, v.endAt.plusSeconds(b.turnaroundHours * 3600L), id);
          if (!reason.isEmpty()) throw new Problem(409, reason);
        }
        b.endAt = v.endAt;
      }
      case "payment" -> payment(b, v);
      case "cancel" -> {
        state(b, "DRAFT", "CONFIRMED");
        var t = totals(b);
        if (t.get("rentPaid").signum() != 0 || t.get("depositHeld").signum() != 0)
          throw new Problem(409, "REFUND_REQUIRED");
        b.status = "CANCELLED";
      }
      case "close" -> {
        state(b, "RETURNED");
        var t = totals(b);
        if (t.get("rentBalance").signum() != 0 || t.get("depositHeld").signum() != 0)
          throw new Problem(409, "UNSETTLED_BALANCE");
        b.status = "CLOSED";
      }
      default -> throw new Problem(404, "UNKNOWN_ACTION");
    }
    var mutation = new MutationKey();
    mutation.bookingId = id;
    mutation.requestKey = key;
    mutation.action = action;
    mutation.fingerprint = fingerprint;
    db.save(mutation);
    access.audit("BOOKING_" + action.toUpperCase(), id, b.departmentId);
    return view(b);
  }

  /** 删除完全未操作的草稿；有历史记录的订单走取消流程。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id) {
    access.require("booking.write");
    var b = db.lock(Booking.class, id);
    access.department(b.departmentId);
    state(b, "DRAFT");
    if (!entries(id).isEmpty()
        || !db.query(MutationKey.class, "from MutationKey where bookingId=?1", id).isEmpty())
      throw new Problem(409, "HISTORY_EXISTS");
    for (var l : lines(id)) db.delete(l);
    db.delete(b);
    access.audit("BOOKING_DELETE", id, b.departmentId);
  }

  /** 单据余额：押金不当收入，扣抵才转为租金或赔偿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, BigDecimal> totals(Booking b) {
    BigDecimal rent = BigDecimal.ZERO,
        deposit = BigDecimal.ZERO,
        late = BigDecimal.ZERO,
        damage = BigDecimal.ZERO;
    for (var l : lines(b.id)) {
      rent = rent.add(RentalPolicy.rent(l.dailyRate, b.startAt, b.endAt));
      deposit = deposit.add(l.deposit);
      late = late.add(l.lateFee);
      damage = damage.add(l.damageFee);
    }
    BigDecimal paid = BigDecimal.ZERO, held = BigDecimal.ZERO, cash = BigDecimal.ZERO;
    for (var l : entries(b.id)) {
      switch (l.kind) {
        case "RENT_RECEIPT" -> {
          paid = paid.add(l.amount);
          cash = cash.add(l.amount);
        }
        case "DEPOSIT_RECEIPT" -> {
          held = held.add(l.amount);
          cash = cash.add(l.amount);
        }
        case "RENT_REFUND" -> {
          paid = paid.subtract(l.amount);
          cash = cash.subtract(l.amount);
        }
        case "DEPOSIT_REFUND" -> {
          held = held.subtract(l.amount);
          cash = cash.subtract(l.amount);
        }
        case "DEPOSIT_DEDUCTION" -> {
          held = held.subtract(l.amount);
          paid = paid.add(l.amount);
        }
        default -> throw new Problem(500, "INVALID_LEDGER");
      }
    }
    var due = rent.add(late).add(damage);
    return Map.of(
        "rent",
        rent,
        "lateFee",
        late,
        "damageFee",
        damage,
        "rentDue",
        due,
        "rentPaid",
        paid,
        "rentBalance",
        due.subtract(paid),
        "depositRequired",
        deposit,
        "depositHeld",
        held,
        "netCash",
        cash);
  }

  /** 读取订单明细和台账，不返回内部认证或幂等凭证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> view(Booking b) {
    return Map.of("booking", b, "lines", lines(b.id), "ledger", entries(b.id), "totals", totals(b));
  }

  private void payment(Booking b, Action v) {
    state(b, "CONFIRMED", "OUT", "PARTIAL", "RETURNED");
    var amount = RentalPolicy.money(v.amount);
    if (amount.signum() == 0) throw new Problem(400, "INVALID_AMOUNT");
    String kind = AdminService.text(v.kind, 30);
    var t = totals(b);
    switch (kind) {
      case "RENT_RECEIPT" -> {
        if (amount.compareTo(t.get("rentBalance")) > 0) throw new Problem(409, "EXCESS_RENT");
      }
      case "DEPOSIT_RECEIPT" -> {
        if (amount.compareTo(t.get("depositRequired").subtract(t.get("depositHeld"))) > 0)
          throw new Problem(409, "EXCESS_DEPOSIT");
      }
      case "RENT_REFUND" -> {
        state(b, "CONFIRMED");
        if (amount.compareTo(t.get("rentPaid")) > 0) throw new Problem(409, "EXCESS_REFUND");
      }
      case "DEPOSIT_REFUND" -> {
        state(b, "CONFIRMED", "RETURNED");
        if (amount.compareTo(t.get("depositHeld")) > 0) throw new Problem(409, "EXCESS_REFUND");
        if (b.status.equals("RETURNED") && t.get("rentBalance").signum() != 0)
          throw new Problem(409, "RENT_UNPAID");
      }
      case "DEPOSIT_DEDUCTION" -> {
        state(b, "RETURNED");
        if (amount.compareTo(t.get("depositHeld")) > 0
            || amount.compareTo(t.get("rentBalance")) > 0)
          throw new Problem(409, "EXCESS_DEDUCTION");
      }
      default -> throw new Problem(400, "INVALID_PAYMENT_KIND");
    }
    var entry = new Ledger();
    entry.bookingId = b.id;
    entry.kind = kind;
    entry.amount = amount;
    entry.reference = AdminService.text(v.reference, 120);
    entry.note = MasterService.optional(v.note, 500);
    entry.createdAt = clock.instant();
    entry.createdBy = access.current().username;
    db.save(entry);
  }

  private String unavailable(Asset a, Instant start, Instant end, Long exclude) {
    if (a.maintenance) return "MAINTENANCE_HOLD";
    if (a.readyAt != null && start.isBefore(a.readyAt)) return "TURNAROUND_PENDING";
    for (var l :
        db.query(
            BookingLine.class, "from BookingLine where assetId=?1 and returnedAt is null", a.id)) {
      var b = db.get(Booking.class, l.bookingId);
      if (b.id.equals(exclude) || !Set.of("CONFIRMED", "OUT", "PARTIAL").contains(b.status))
        continue;
      var until = b.endAt.plusSeconds(b.turnaroundHours * 3600L);
      if (!b.status.equals("CONFIRMED") && b.endAt.isBefore(clock.instant()))
        until = Instant.parse("9999-12-31T00:00:00Z");
      if (RentalPolicy.overlap(start, end, b.startAt, until)) return "RESERVATION_CONFLICT";
    }
    return "";
  }

  private List<BookingLine> lines(Long id) {
    return db.query(BookingLine.class, "from BookingLine where bookingId=?1 order by assetId", id);
  }

  private List<Ledger> entries(Long id) {
    return db.query(Ledger.class, "from Ledger where bookingId=?1 order by id", id);
  }

  private String setting(String key) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", key).getFirst().value;
  }

  private void validatePeriod(Instant start, Instant end) {
    long days = RentalPolicy.days(start, end);
    if (days > 366 || end.isAfter(clock.instant().plus(Duration.ofDays(730))))
      throw new Problem(400, "INVALID_PERIOD");
  }

  private void state(Booking b, String... allowed) {
    if (!Arrays.asList(allowed).contains(b.status)) throw new Problem(409, "INVALID_STATE");
  }

  private String fingerprint(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
