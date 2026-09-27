// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.rental;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 HTTP 认证、持久化、金额、权限与并发预约集成测试。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(RentalIntegrationTest.TestTimeConfig.class)
class RentalIntegrationTest {
  static final String password = "Learning" + UUID.randomUUID() + "A9";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:rental;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("rental.admin-password", () -> password);
    r.add("rental.seed-demo", () -> false);
  }

  /** 独立测试时间，不修改生产时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @org.springframework.boot.test.context.TestConfiguration
  static class TestTimeConfig {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  /** 可推进的测试时钟，验证实际逾期及周转状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class TestClock extends Clock {
    Instant now = Instant.now();

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  @Autowired TestClock testClock;
  @Autowired MockMvc mvc;
  @Autowired Store db;
  @Autowired RentalService rentals;
  @Autowired PlatformTransactionManager transactions;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession session;
  final List<Long> authenticationAccounts = new ArrayList<>();
  Long customerId, categoryId;
  List<Long> assetIds;

  @BeforeEach
  void setup() throws Exception {
    testClock.now = Instant.now();
    session = login("admin", password);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var c = new Customer();
              c.name = "Test customer " + UUID.randomUUID();
              c.contact = "test@example.invalid";
              c.notes = "";
              c.departmentId = 1L;
              db.save(c);
              customerId = c.id;
              var category = new Category();
              category.name = "Test " + UUID.randomUUID();
              category.nameEn = "Test";
              db.save(category);
              categoryId = category.id;
              assetIds = new ArrayList<>();
              for (int i = 0; i < 2; i++) {
                var a = new Asset();
                a.name = "Test camera";
                a.code = "TEST-" + UUID.randomUUID();
                a.categoryId = category.id;
                a.departmentId = 1L;
                a.dailyRate = new BigDecimal("20.00");
                a.deposit = new BigDecimal("200.00");
                a.accessories = "Case, charger";
                a.maintenanceNote = "";
                db.save(a);
                assetIds.add(a.id);
              }
            });
  }

  /** 清理本测试创建的认证账号，不影响默认管理员和其他测试。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @AfterEach
  void cleanAuthenticationAccounts() {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              for (Long id : authenticationAccounts) db.delete(db.get(Account.class, id));
            });
    authenticationAccounts.clear();
  }

  JsonNode authenticationAccount(boolean administrator) throws Exception {
    var user =
        send(
            "/api/admin/users",
            Map.of(
                "username",
                "auth" + UUID.randomUUID().toString().substring(0, 8),
                "displayName",
                "Authentication test",
                "password",
                password,
                "roleId",
                administrator ? 1 : 2,
                "departmentId",
                1,
                "enabled",
                true),
            200);
    authenticationAccounts.add(user.get("id").asLong());
    return user;
  }

  void updateAuthenticationAccount(JsonNode user, String newPassword, boolean enabled)
      throws Exception {
    var body =
        new LinkedHashMap<String, Object>(
            Map.of(
                "username",
                user.get("username").asText(),
                "displayName",
                "Authentication test",
                "roleId",
                user.get("roleId").asLong(),
                "departmentId",
                1,
                "enabled",
                enabled));
    if (newPassword != null) body.put("password", newPassword);
    mvc.perform(
            put("/api/admin/users/" + user.get("id").asLong())
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk());
  }

  @Test
  void ownPasswordChangeSucceedsAndInvalidatesTheSession() throws Exception {
    var user = authenticationAccount(false);
    String name = user.get("username").asText(), changed = "Changed" + UUID.randomUUID() + "A9";
    session = login(name, password);
    send("/api/auth/password", Map.of("oldPassword", password, "newPassword", changed), 200);
    assertTrue(session.isInvalid());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", name, "password", password))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").session(login(name, changed))).andExpect(status().isOk());
    assertTrue(
        Boolean.TRUE.equals(
            new TransactionTemplate(transactions)
                .execute(
                    tx ->
                        db.all(AuditEvent.class).stream()
                            .anyMatch(
                                a -> a.actor.equals(name) && a.action.equals("PASSWORD_CHANGE")))));
  }

  @Test
  void failedPasswordChangeKeepsTheOldCredentialsAndSession() throws Exception {
    var user = authenticationAccount(false);
    String name = user.get("username").asText();
    session = login(name, password);
    send("/api/auth/password", Map.of("oldPassword", "incorrect", "newPassword", password), 400);
    send("/api/auth/password", Map.of("oldPassword", password, "newPassword", "weak"), 400);
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(login(name, password))).andExpect(status().isOk());
    assertFalse(
        Boolean.TRUE.equals(
            new TransactionTemplate(transactions)
                .execute(
                    tx ->
                        db.all(AuditEvent.class).stream()
                            .anyMatch(
                                a -> a.actor.equals(name) && a.action.equals("PASSWORD_CHANGE")))));
  }

  @Test
  void administratorResetInvalidatesExistingBusinessSessions() throws Exception {
    var user = authenticationAccount(false);
    var oldSession = login(user.get("username").asText(), password);
    String changed = "Reset" + UUID.randomUUID() + "A9";
    updateAuthenticationAccount(user, changed, true);
    mvc.perform(get("/api/auth/me").session(oldSession)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").session(login(user.get("username").asText(), changed)))
        .andExpect(status().isOk());
  }

  @Test
  void administratorCanResetTheirOwnPasswordWithoutAuditRejection() throws Exception {
    var user = authenticationAccount(true);
    String changed = "Reset" + UUID.randomUUID() + "A9";
    session = login(user.get("username").asText(), password);
    updateAuthenticationAccount(user, changed, true);
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/lists/users").session(login(user.get("username").asText(), changed)))
        .andExpect(status().isOk());
  }

  @Test
  void administratorCanDisableTheirOwnAccountWhenAnotherAdministratorRemains() throws Exception {
    var user = authenticationAccount(true);
    session = login(user.get("username").asText(), password);
    updateAuthenticationAccount(user, null, false);
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/lists/users").session(login("admin", password)))
        .andExpect(status().isOk());
  }

  MockHttpSession login(String user, String pass) throws Exception {
    return (MockHttpSession)
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", user, "password", pass))))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession();
  }

  JsonNode send(String path, Object value, int expected) throws Exception {
    var response =
        mvc.perform(
                post(path)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(value)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse();
    return json.readTree(response.getContentAsString());
  }

  JsonNode draft(Instant start, Instant end, List<Long> assets) throws Exception {
    return send(
        "/api/bookings",
        Map.of(
            "customerId",
            customerId,
            "startAt",
            start.toString(),
            "endAt",
            end.toString(),
            "assetIds",
            assets,
            "notes",
            ""),
        200);
  }

  JsonNode draft() throws Exception {
    return draft(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(86400), assetIds);
  }

  Map<String, Object> actionBody() {
    return new HashMap<>(Map.of("requestKey", UUID.randomUUID().toString()));
  }

  JsonNode action(Long id, String action, int expected) throws Exception {
    return send("/api/bookings/" + id + "/" + action, actionBody(), expected);
  }

  JsonNode payment(Long id, String kind, String amount, int expected) throws Exception {
    var body = actionBody();
    body.put("kind", kind);
    body.put("amount", amount);
    body.put("reference", "Test receipt");
    return send("/api/bookings/" + id + "/payment", body, expected);
  }

  Long id(JsonNode v) {
    return v.get("booking").get("id").asLong();
  }

  @Test
  void anonymousBusinessDenied() throws Exception {
    mvc.perform(get("/api/lists/assets")).andExpect(status().isUnauthorized());
  }

  @Test
  void missingCsrfDenied() throws Exception {
    mvc.perform(
            post("/api/bookings").session(session).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidCredentialsDenied() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"invalid\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void draftDoesNotReserveButConfirmationDoes() throws Exception {
    var one = draft();
    var two = draft();
    action(id(one), "confirm", 200);
    action(id(two), "confirm", 409);
  }

  @Test
  void confirmedCancellationReleasesEquipment() throws Exception {
    var one = draft();
    action(id(one), "confirm", 200);
    action(id(one), "cancel", 200);
    action(id(draft()), "confirm", 200);
  }

  @Test
  void checkoutRequiresActualPaymentRecords() throws Exception {
    Long id = id(draft());
    action(id, "confirm", 200);
    action(id, "checkout", 409);
  }

  @Test
  void rejectMoneyBeyondBalance() throws Exception {
    Long id = id(draft());
    action(id, "confirm", 200);
    payment(id, "RENT_RECEIPT", "999.00", 409);
    payment(id, "DEPOSIT_RECEIPT", "401.00", 409);
  }

  @Test
  void idempotentReceiptAndChangedPayloadRejected() throws Exception {
    Long id = id(draft());
    action(id, "confirm", 200);
    var body = actionBody();
    body.putAll(Map.of("kind", "DEPOSIT_RECEIPT", "amount", "200.00", "reference", "Test receipt"));
    var first = send("/api/bookings/" + id + "/payment", body, 200);
    var retry = send("/api/bookings/" + id + "/payment", body, 200);
    assertEquals(1, retry.get("ledger").size());
    assertEquals(first.get("totals"), retry.get("totals"));
    body.put("amount", "201.00");
    send("/api/bookings/" + id + "/payment", body, 409);
  }

  @Test
  void fullPartialReturnDamageDepositSettlement() throws Exception {
    var v = draft();
    Long id = id(v);
    action(id, "confirm", 200);
    payment(id, "RENT_RECEIPT", v.get("totals").get("rentDue").asText(), 200);
    payment(id, "DEPOSIT_RECEIPT", "400.00", 200);
    action(id, "checkout", 200);
    var body = actionBody();
    body.putAll(
        Map.of(
            "lineId",
            v.get("lines").get(0).get("id").asLong(),
            "amount",
            "0.00",
            "inspectionPassed",
            true,
            "note",
            "Checked accessories"));
    var partial = send("/api/bookings/" + id + "/return", body, 200);
    assertEquals("PARTIAL", partial.get("booking").get("status").asText());
    action(id, "close", 409);
    var second = actionBody();
    second.putAll(
        Map.of(
            "lineId",
            v.get("lines").get(1).get("id").asLong(),
            "amount",
            "10.00",
            "inspectionPassed",
            false,
            "note",
            "Missing accessory"));
    var returned = send("/api/bookings/" + id + "/return", second, 200);
    assertEquals("RETURNED", returned.get("booking").get("status").asText());
    payment(id, "DEPOSIT_REFUND", "400.00", 409);
    payment(id, "DEPOSIT_DEDUCTION", "10.00", 200);
    payment(id, "DEPOSIT_REFUND", "390.00", 200);
    var closed = action(id, "close", 200);
    assertEquals("CLOSED", closed.get("booking").get("status").asText());
    assertEquals(0, closed.get("totals").get("depositHeld").decimalValue().signum());
    assertEquals(0, closed.get("totals").get("rentBalance").decimalValue().signum());
  }

  @Test
  void returnCannotTakeLineFromOtherRental() throws Exception {
    var one = draft();
    Long id = id(one);
    action(id, "confirm", 200);
    payment(id, "RENT_RECEIPT", one.get("totals").get("rentDue").asText(), 200);
    payment(id, "DEPOSIT_RECEIPT", "400", 200);
    action(id, "checkout", 200);
    var other = draft();
    var body = actionBody();
    body.putAll(
        Map.of(
            "lineId",
            other.get("lines").get(0).get("id").asLong(),
            "amount",
            "0",
            "inspectionPassed",
            true,
            "note",
            "Checked"));
    send("/api/bookings/" + id + "/return", body, 409);
  }

  @Test
  void paidConfirmedRentalNeedsRefundBeforeCancellation() throws Exception {
    Long id = id(draft());
    action(id, "confirm", 200);
    payment(id, "DEPOSIT_RECEIPT", "200", 200);
    action(id, "cancel", 409);
    payment(id, "DEPOSIT_REFUND", "200", 200);
    action(id, "cancel", 200);
  }

  @Test
  void maintenanceEquipmentCannotBeConfirmed() throws Exception {
    send(
        "/api/assets/" + assetIds.getFirst() + "/maintenance",
        Map.of("blocked", "true", "note", "Needs service"),
        200);
    Long id = id(draft());
    action(id, "confirm", 409);
    send(
        "/api/assets/" + assetIds.getFirst() + "/maintenance",
        Map.of("blocked", "false", "note", "Inspection passed"),
        200);
    action(id, "confirm", 200);
  }

  @Test
  void extensionMustRecheckNextReservation() throws Exception {
    Instant start = Instant.now().plusSeconds(86400), end = start.plusSeconds(86400);
    Long one = id(draft(start, end, assetIds));
    Long two = id(draft(end.plusSeconds(7200), end.plusSeconds(93600), assetIds));
    action(one, "confirm", 200);
    action(two, "confirm", 200);
    var body = actionBody();
    body.put("endAt", end.plusSeconds(10800).toString());
    send("/api/bookings/" + one + "/extend", body, 409);
  }

  @Test
  void changingAssetPriceDoesNotChangeOldQuote() throws Exception {
    var old = draft();
    mvc.perform(
            put("/api/master/assets/" + assetIds.getFirst())
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            "Updated camera",
                            "code",
                            "CHANGED-" + UUID.randomUUID(),
                            "categoryId",
                            categoryId,
                            "departmentId",
                            1,
                            "dailyRate",
                            "999",
                            "deposit",
                            "999"))))
        .andExpect(status().isOk());
    var detail =
        mvc.perform(get("/api/bookings/" + id(old)).session(session))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        old.get("totals").get("rent"),
        json.readTree(detail.getResponse().getContentAsString()).get("totals").get("rent"));
  }

  @Test
  void lastAdministratorCannotBeDisabled() throws Exception {
    mvc.perform(
            put("/api/admin/users/1")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "username",
                            "admin",
                            "displayName",
                            "Admin",
                            "roleId",
                            1,
                            "departmentId",
                            1,
                            "enabled",
                            false))))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
  }

  @Test
  void roleAndDepartmentScopeAreEnforced() throws Exception {
    var dep = send("/api/admin/departments", Map.of("name", "Other " + UUID.randomUUID()), 200);
    Long dept = dep.get("id").asLong();
    String username = "staff" + UUID.randomUUID().toString().substring(0, 8);
    send(
        "/api/admin/users",
        Map.of(
            "username",
            username,
            "displayName",
            "Test operator",
            "password",
            password,
            "roleId",
            2,
            "departmentId",
            dept,
            "enabled",
            true),
        200);
    Long b = id(draft());
    var staff = login(username, password);
    mvc.perform(get("/api/bookings/" + b).session(staff)).andExpect(status().isForbidden());
    mvc.perform(get("/api/lists/users").session(staff)).andExpect(status().isForbidden());
    var list =
        mvc.perform(get("/api/lists/bookings").session(staff))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(0, json.readTree(list.getResponse().getContentAsString()).get("total").asInt());
  }

  @Test
  void disabledAccountSessionBecomesInvalid() throws Exception {
    String username = "staff" + UUID.randomUUID().toString().substring(0, 8);
    var user =
        send(
            "/api/admin/users",
            Map.of(
                "username",
                username,
                "displayName",
                "Test operator",
                "password",
                password,
                "roleId",
                2,
                "departmentId",
                1,
                "enabled",
                true),
            200);
    var staff = login(username, password);
    mvc.perform(
            put("/api/admin/users/" + user.get("id").asLong())
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "username",
                            username,
                            "displayName",
                            "Test operator",
                            "roleId",
                            2,
                            "departmentId",
                            1,
                            "enabled",
                            false))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(staff)).andExpect(status().isUnauthorized());
  }

  @Test
  void passwordHashesNeverAppearInAccountResponses() throws Exception {
    var response =
        mvc.perform(get("/api/lists/users").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(response.contains("passwordHash"));
    assertFalse(response.contains("$2a$"));
  }

  @Test
  void searchFilterAndPaginationAreReal() throws Exception {
    var b = draft();
    String number = b.get("booking").get("number").asText();
    var r =
        mvc.perform(
                get("/api/lists/bookings")
                    .param("search", number)
                    .param("status", "DRAFT")
                    .param("page", "0")
                    .param("size", "1")
                    .session(session))
            .andExpect(status().isOk())
            .andReturn();
    var data = json.readTree(r.getResponse().getContentAsString());
    assertEquals(1, data.get("total").asInt());
    assertEquals(number, data.get("items").get(0).get("number").asText());
  }

  @Test
  void exportHasNoBrandOrCredentialsInsideBusinessData() throws Exception {
    draft();
    var r =
        mvc.perform(get("/api/reports.csv").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertTrue(r.getContentType().contains("text/csv"));
    assertFalse(r.getContentAsString().contains("zhuatech2"));
    assertFalse(r.getContentAsString().contains("password"));
  }

  @Test
  void concurrentConfirmationsAllowExactlyOneWinner() throws Exception {
    Long one = id(draft()), two = id(draft());
    var authentication =
        ((SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT")).getAuthentication();
    var executor = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var tasks = new ArrayList<Future<Integer>>();
      for (Long id : List.of(one, two))
        tasks.add(
            executor.submit(
                () -> {
                  gate.await();
                  var context = SecurityContextHolder.createEmptyContext();
                  context.setAuthentication(authentication);
                  SecurityContextHolder.setContext(context);
                  try {
                    rentals.action(
                        id,
                        "confirm",
                        new RentalService.Action(
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
                    return 200;
                  } catch (Problem e) {
                    return e.status;
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      gate.countDown();
      var codes =
          List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
      assertEquals(1, codes.stream().filter(c -> c == 200).count());
      assertEquals(1, codes.stream().filter(c -> c == 409).count());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void freshDatabaseMigrationAndAdminHealth() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/lists/permissions").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(11));
  }

  @Test
  void overdueItemsStayBlockedUntilReturnAndTurnaroundCompletes() throws Exception {
    Instant now = testClock.instant();
    var v = draft(now.minusSeconds(3600), now.plusSeconds(82800), assetIds);
    Long bid = id(v);
    action(bid, "confirm", 200);
    payment(bid, "RENT_RECEIPT", "40", 200);
    payment(bid, "DEPOSIT_RECEIPT", "400", 200);
    action(bid, "checkout", 200);
    testClock.now = now.plusSeconds(172800);
    Long next =
        id(
            draft(
                testClock.instant().plusSeconds(3600),
                testClock.instant().plusSeconds(90000),
                assetIds));
    action(next, "confirm", 409);
    for (var line : v.get("lines")) {
      var body = actionBody();
      body.putAll(
          Map.of(
              "lineId",
              line.get("id").asLong(),
              "amount",
              "0",
              "inspectionPassed",
              true,
              "note",
              "Returned and checked"));
      v = send("/api/bookings/" + bid + "/return", body, 200);
    }
    assertEquals(
        new BigDecimal("80.00"), v.get("totals").get("lateFee").decimalValue().setScale(2));
    action(next, "confirm", 409);
    testClock.now = testClock.now.plusSeconds(7201);
    var future =
        draft(testClock.instant().plusSeconds(1), testClock.instant().plusSeconds(86401), assetIds);
    action(id(future), "confirm", 200);
  }

  @Test
  void referencedEquipmentCannotBeDeleted() throws Exception {
    draft();
    mvc.perform(delete("/api/master/assets/" + assetIds.getFirst()).session(session).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void permissionRevocationAffectsExistingSession() throws Exception {
    var role =
        send(
            "/api/admin/roles",
            Map.of(
                "name",
                "Limited " + UUID.randomUUID(),
                "scope",
                "DEPARTMENT",
                "permissions",
                List.of("asset.read")),
            200);
    String username = "reader" + UUID.randomUUID().toString().substring(0, 8);
    send(
        "/api/admin/users",
        Map.of(
            "username",
            username,
            "displayName",
            "Reader",
            "password",
            password,
            "roleId",
            role.get("id").asLong(),
            "departmentId",
            1,
            "enabled",
            true),
        200);
    var reader = login(username, password);
    mvc.perform(get("/api/lists/assets").session(reader)).andExpect(status().isOk());
    mvc.perform(
            put("/api/admin/roles/" + role.get("id").asLong())
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            role.get("name").asText(),
                            "scope",
                            "DEPARTMENT",
                            "permissions",
                            List.of()))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/lists/assets").session(reader)).andExpect(status().isForbidden());
  }

  @Test
  void registeredMenuAndSettingsCanBeEdited() throws Exception {
    var menus =
        json.readTree(
                mvc.perform(get("/api/lists/menus").session(session))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("items");
    var menu = menus.get(0);
    mvc.perform(
            put("/api/admin/menus/" + menu.get("id").asLong())
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            "工作台",
                            "nameEn",
                            "Overview",
                            "permissionCode",
                            "dashboard",
                            "position",
                            0,
                            "enabled",
                            true))))
        .andExpect(status().isOk());
    var settings =
        json.readTree(
                mvc.perform(get("/api/lists/settings").session(session))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("items");
    for (var row : settings)
      if (row.get("code").asText().equals("turnaroundHours"))
        mvc.perform(
                put("/api/admin/settings/" + row.get("id").asLong())
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"value\":\"-1\"}"))
            .andExpect(status().isBadRequest());
  }
}
