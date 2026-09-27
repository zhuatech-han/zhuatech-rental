<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch, nextTick } from "vue";
import {
  LayoutDashboard,
  CalendarDays,
  Package,
  Users,
  ChartNoAxesCombined,
  Settings,
  LogOut,
  Search,
  Plus,
  ChevronRight,
  Check,
  X,
  Menu,
  ArrowDownToLine,
  FileText,
  ShieldCheck,
  Wrench,
  Globe,
  Printer,
} from "@lucide/vue";
import { api, resetCsrf, downloadReport } from "./api.js";
import { translate } from "./i18n.js";
import { inputDate, instant, dateTime } from "./format.js";
const lang = ref(localStorage.getItem("rental-language") || "zh");
const t = (k) => translate(k, lang.value);
const me = ref(null),
  page = ref("dashboard"),
  rows = ref([]),
  total = ref(0),
  pageNo = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("id"),
  descending = ref(true),
  loading = ref(false),
  busy = ref(false),
  message = ref(""),
  error = ref(""),
  drawer = ref(null),
  modal = ref(null),
  form = ref({}),
  catalog = ref({
    categories: [],
    departments: [],
    settings: [],
    dictionaries: [],
  }),
  overview = ref(null),
  available = ref([]),
  assetOptions = ref([]),
  customerOptions = ref([]),
  roleOptions = ref([]),
  permissionOptions = ref([]),
  mobileNav = ref(false),
  loginForm = ref({ username: "", password: "" });
const setting = (key) =>
  catalog.value.settings.find((s) => s.code === key)?.value;
const zone = computed(() => setting("timezone") || "Asia/Shanghai"),
  currency = computed(
    () => drawer.value?.booking.currency || setting("currency") || "CNY",
  );
const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
const fmtDate = (v) => dateTime(v, lang.value, zone.value);
const money = (v, c = currency.value) =>
  new Intl.NumberFormat(lang.value === "en" ? "en-GB" : "zh-CN", {
    style: "currency",
    currency: c,
  }).format(v || 0);
const can = (p) => me.value?.permissions.includes(p);
const masterPages = ["assets", "customers", "categories"],
  adminPages = [
    "users",
    "roles",
    "departments",
    "menus",
    "permissions",
    "dictionaries",
    "settings",
  ];
const icons = {
  dashboard: LayoutDashboard,
  bookings: CalendarDays,
  availability: CalendarDays,
  assets: Package,
  customers: Users,
  reports: ChartNoAxesCombined,
  audit: FileText,
};
const title = computed(() =>
  page.value === "about"
    ? t("about")
    : me.value?.menus.find((m) => m.code === page.value)?.[
        lang.value === "en" ? "nameEn" : "name"
      ] || t(page.value),
);
const fields = {
  customers: ["name", "contact", "notes", "departmentId"],
  categories: ["name", "nameEn"],
  assets: [
    "code",
    "name",
    "categoryId",
    "departmentId",
    "dailyRate",
    "deposit",
    "accessories",
  ],
  users: [
    "username",
    "displayName",
    "password",
    "roleId",
    "departmentId",
    "enabled",
  ],
  roles: ["name", "scope", "permissions"],
  departments: ["name"],
  menus: ["name", "nameEn", "permissionCode", "position", "enabled"],
  permissions: ["name"],
  dictionaries: ["type", "code", "name", "nameEn"],
  settings: ["value"],
};
const columns = {
  bookings: ["number", "customerId", "startAt", "endAt", "status"],
  assets: ["code", "name", "categoryId", "dailyRate", "deposit", "maintenance"],
  customers: ["name", "contact", "departmentId"],
  categories: ["name", "nameEn"],
  users: ["username", "displayName", "roleId", "departmentId", "enabled"],
  roles: ["name", "scope", "permissions"],
  departments: ["name"],
  menus: ["code", "name", "nameEn", "permissionCode", "position", "enabled"],
  permissions: ["code", "name"],
  dictionaries: ["type", "code", "name", "nameEn"],
  settings: ["code", "value"],
  audit: ["createdAt", "actor", "action", "objectId"],
};
const hasWrite = computed(() =>
  page.value === "customers"
    ? can("customer.write")
    : masterPages.includes(page.value)
      ? can("asset.write")
      : adminPages.includes(page.value)
        ? can("admin")
        : can("booking.write"),
);
const cell = (row, key) => {
  const v = row[key];
  if (key === "customerId")
    return customerOptions.value.find((x) => x.id === v)?.name || v;
  if (key === "categoryId")
    return (
      catalog.value.categories.find((x) => x.id === v)?.[
        lang.value === "en" ? "nameEn" : "name"
      ] || v
    );
  if (key === "departmentId")
    return catalog.value.departments.find((x) => x.id === v)?.name || v;
  if (key === "roleId")
    return roleOptions.value.find((x) => x.id === v)?.name || v;
  if (["startAt", "endAt", "createdAt", "readyAt"].includes(key))
    return fmtDate(v);
  if (["dailyRate", "deposit"].includes(key)) return money(v);
  if (typeof v === "boolean") return t(v ? "yes" : "no");
  if (key === "status" || key === "scope") return t(v);
  if (key === "permissions" && Array.isArray(v))
    return v
      .map((code) => {
        const name =
          permissionOptions.value.find((p) => p.code === code)?.name || code;
        return name.split(" / ")[lang.value === "en" ? 1 : 0] || name;
      })
      .join("、");
  if (Array.isArray(v)) return v.join(", ");
  if (key === "code" && page.value === "settings") return t(v);
  return v ?? "—";
};
function notice(e) {
  error.value = t(e.message);
  message.value = "";
  if (e.message === "UNAUTHENTICATED") {
    me.value = null;
    drawer.value = null;
    modal.value = null;
  }
}
async function run(work) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  message.value = "";
  try {
    await work();
  } catch (e) {
    notice(e);
  } finally {
    busy.value = false;
  }
}
async function options(resource, sort = "id") {
  const rows = [];
  for (let page = 0; page < 100; page++) {
    const result = await api(
      `/lists/${resource}?size=100&page=${page}&sort=${sort}`,
    );
    rows.push(...result.items);
    if (rows.length >= result.total) break;
  }
  return rows;
}
async function referenceData() {
  catalog.value = await api("/catalog");
  if (can("customer.read"))
    customerOptions.value = await options("customers", "name");
  if (can("asset.read")) assetOptions.value = await options("assets", "code");
  if (can("admin")) {
    roleOptions.value = await options("roles");
    permissionOptions.value = await options("permissions");
  }
}
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    if (["dashboard", "reports"].includes(page.value))
      overview.value = await api("/dashboard");
    else if (page.value === "availability") {
      if (!form.value.startAt)
        form.value = {
          startAt: inputDate(new Date()),
          endAt: inputDate(Date.now() + 86400000),
        };
      await checkAvailability();
    } else if (page.value !== "about") {
      const q = new URLSearchParams({
        page: pageNo.value,
        size: 20,
        search: search.value,
        status: status.value,
        sort: sort.value,
        desc: descending.value,
      });
      const v = await api("/lists/" + page.value + "?" + q);
      rows.value = v.items;
      total.value = v.total;
    }
  } catch (e) {
    notice(e);
  } finally {
    loading.value = false;
  }
}
async function signIn() {
  await run(async () => {
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    page.value =
      me.value.menus.find((m) => m.code === "dashboard")?.code ||
      me.value.menus[0]?.code ||
      "about";
    await referenceData();
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST");
    resetCsrf();
    me.value = null;
    drawer.value = null;
    modal.value = null;
    rows.value = [];
  });
}
async function navigate(p) {
  page.value = p;
  drawer.value = null;
  modal.value = null;
  search.value = "";
  status.value = "";
  pageNo.value = 0;
  sort.value = "id";
  mobileNav.value = false;
  form.value = {};
  await load();
  await nextTick();
  window.scrollTo({ top: 0 });
}
function changePage(delta) {
  pageNo.value += delta;
  load();
}
function openEdit(row) {
  form.value = row
    ? {
        ...row,
        permissions: row.permissions ? [...row.permissions] : [],
        password: "",
      }
    : {
        enabled: true,
        scope: "DEPARTMENT",
        permissions: [],
        departmentId: me.value.departmentId,
        dailyRate: 0,
        deposit: 0,
      };
  modal.value = { type: "edit", id: row?.id };
  error.value = "";
}
async function saveMaster() {
  await run(async () => {
    const path =
      (masterPages.includes(page.value) ? "/master/" : "/admin/") +
      page.value +
      (modal.value.id ? "/" + modal.value.id : "");
    await api(path, modal.value.id ? "PUT" : "POST", form.value);
    modal.value = null;
    me.value = await api("/auth/me");
    await referenceData();
    await load();
    message.value = t("success");
  });
}
async function remove(row) {
  modal.value = { type: "delete", row };
  error.value = "";
}
async function confirmDelete() {
  await run(async () => {
    const deletePath =
      page.value === "bookings"
        ? "/bookings/"
        : (masterPages.includes(page.value) ? "/master/" : "/admin/") +
          page.value +
          "/";
    await api(deletePath + modal.value.row.id, "DELETE");
    if (page.value === "bookings") drawer.value = null;
    modal.value = null;
    await referenceData();
    await load();
    message.value = t("success");
  });
}
async function openRental(id) {
  await run(async () => {
    drawer.value = await api("/bookings/" + id);
  });
}
async function newRental(edit = false) {
  form.value = edit
    ? {
        customerId: drawer.value.booking.customerId,
        startAt: inputDate(drawer.value.booking.startAt),
        endAt: inputDate(drawer.value.booking.endAt),
        notes: drawer.value.booking.notes,
        assetIds: drawer.value.lines.map((l) => l.assetId),
      }
    : {
        customerId: customerOptions.value[0]?.id,
        startAt: inputDate(Date.now() - 60000),
        endAt: inputDate(Date.now() - 60000 + 86400000),
        assetIds: [],
        notes: "",
      };
  modal.value = { type: "draft", id: edit ? drawer.value.booking.id : null };
  available.value = [];
  await run(() => checkAvailability());
}
async function checkAvailability() {
  available.value = await api(
    "/availability?" +
      new URLSearchParams({
        start: instant(form.value.startAt),
        end: instant(form.value.endAt),
      }),
  );
}
async function saveRental() {
  await run(async () => {
    const payload = {
      ...form.value,
      startAt: instant(form.value.startAt),
      endAt: instant(form.value.endAt),
    };
    drawer.value = await api(
      "/bookings" + (modal.value.id ? "/" + modal.value.id : ""),
      modal.value.id ? "PUT" : "POST",
      payload,
    );
    modal.value = null;
    await load();
    message.value = t("success");
  });
}
function actionDialog(action, line) {
  const b = drawer.value.booking;
  form.value = {
    requestKey: crypto.randomUUID(),
    lineId: line?.id,
    amount: action === "return" ? 0 : drawer.value.totals.rentBalance,
    kind: "RENT_RECEIPT",
    reference: "",
    note: "",
    inspectionPassed: true,
    endAt: inputDate(new Date(b.endAt).getTime() + 86400000),
  };
  modal.value = { type: "action", action };
  error.value = "";
}
async function submitAction() {
  await run(async () => {
    const payload = { ...form.value };
    if (modal.value.action === "extend") payload.endAt = instant(payload.endAt);
    else delete payload.endAt;
    drawer.value = await api(
      "/bookings/" + drawer.value.booking.id + "/" + modal.value.action,
      "POST",
      payload,
    );
    modal.value = null;
    await load();
    await referenceData();
    message.value = t("success");
  });
}
function maintenanceDialog(row) {
  form.value = { blocked: String(!row.maintenance), note: "" };
  modal.value = { type: "maintenance", row };
  error.value = "";
}
async function saveMaintenance() {
  await run(async () => {
    await api(
      "/assets/" + modal.value.row.id + "/maintenance",
      "POST",
      form.value,
    );
    modal.value = null;
    await load();
    message.value = t("success");
  });
}
async function exportCsv() {
  await run(downloadReport);
}
function closeModal() {
  if (!busy.value) modal.value = null;
}
function print() {
  window.print();
}
async function changePassword() {
  await run(async () => {
    await api("/auth/password", "POST", form.value);
    modal.value = null;
    me.value = null;
    resetCsrf();
    message.value = t("success");
  });
}
watch(lang, () => {
  localStorage.setItem("rental-language", lang.value);
  document.documentElement.lang = lang.value === "en" ? "en" : "zh-CN";
});
watch(
  () => [form.value.startAt, form.value.endAt],
  () => {
    available.value = [];
  },
);
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    page.value = me.value.menus[0]?.code || "about";
    await referenceData();
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") notice(e);
  }
});
</script>

<template>
  <div v-if="!me" class="login-screen">
    <div class="login-box">
      <div class="login-brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><span>{{ t("brand") }}</span
        ><button class="language" @click="lang = lang === 'en' ? 'zh' : 'en'">
          <Globe :size="16" />{{ lang === "en" ? "中文" : "English" }}
        </button>
      </div>
      <h1>{{ t("login") }}</h1>
      <form @submit.prevent="signIn">
        <label
          >{{ t("username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <div v-if="error" class="error" role="alert">{{ error }}</div>
        <button class="primary login-submit" :disabled="busy">
          {{ busy ? t("loading") : t("login") }}<ChevronRight :size="16" />
        </button>
      </form>
      <footer>
        {{ t("version") }}<br /><a
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noreferrer"
          >知华科技</a
        >
        · 微信 zhuatech / zhuatech2
      </footer>
    </div>
  </div>
  <div v-else class="app-shell">
    <aside :class="{ shown: mobileNav }">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><span>{{
          t("brand")
        }}</span>
      </div>
      <nav>
        <button
          v-for="m in me.menus"
          :key="m.id"
          :class="{ active: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || Settings" :size="18" /><span>{{
            lang === "en" ? m.nameEn : m.name
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <button @click="navigate('about')">
          <ShieldCheck :size="16" />{{ t("about") }}</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
          >www.zhuatech.cn ↗</a
        >
      </div>
    </aside>
    <section class="workspace">
      <header>
        <button
          class="icon mobile-toggle"
          aria-label="Navigation"
          @click="mobileNav = !mobileNav"
        >
          <Menu :size="20" /></button
        ><span class="breadcrumb"
          >{{ setting("companyName") || t("brand")
          }}<ChevronRight :size="14" /><strong>{{ title }}</strong></span
        >
        <div class="header-actions">
          <button class="language" @click="lang = lang === 'en' ? 'zh' : 'en'">
            <Globe :size="16" />{{ lang === "en" ? "中文" : "English" }}</button
          ><button
            class="account"
            @click="
              form = { oldPassword: '', newPassword: '' };
              modal = { type: 'password' };
            "
          >
            {{ me.displayName }}
            <span>{{
              me.role.split(" / ")[lang === "en" ? 1 : 0] || me.role
            }}</span></button
          ><button class="icon" :title="t('logout')" @click="signOut">
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <h1>{{ title }}</h1>
            <p v-if="page === 'bookings'">{{ t("draftHint") }}</p>
            <p v-if="page === 'reports'">{{ t("reportHint") }}</p>
          </div>
          <button
            v-if="page === 'bookings' && can('booking.write')"
            class="primary"
            @click="newRental()"
          >
            <Plus :size="16" />{{ t("newRental") }}</button
          ><button
            v-else-if="
              (masterPages.includes(page) || adminPages.includes(page)) &&
              hasWrite &&
              !['menus', 'permissions', 'settings'].includes(page)
            "
            class="primary"
            @click="openEdit()"
          >
            <Plus :size="16" />{{ t("new") }}</button
          ><button
            v-if="page === 'reports' && can('report')"
            class="primary"
            @click="exportCsv"
          >
            <ArrowDownToLine :size="16" />{{ t("download") }}
          </button>
        </div>
        <div v-if="error && !modal" class="error" role="alert">{{ error }}</div>
        <div v-if="message" class="success" role="status">
          <Check :size="16" />{{ message }}
        </div>
        <template v-if="['dashboard', 'reports'].includes(page) && overview"
          ><div class="metrics">
            <div>
              <span>{{ t("currentAssets") }}</span
              ><strong>{{ overview.assetCount }}</strong
              ><Package :size="22" />
            </div>
            <div>
              <span>{{ t("openRentals") }}</span
              ><strong>{{ overview.openRentals }}</strong
              ><CalendarDays :size="22" />
            </div>
            <div>
              <span>{{ t("maintenanceCount") }}</span
              ><strong>{{ overview.maintenanceCount }}</strong
              ><Wrench :size="22" />
            </div>
            <div>
              <span>{{ t("overdue") }}</span
              ><strong :class="{ danger: overview.overdue.length }">{{
                overview.overdue.length
              }}</strong
              ><FileText :size="22" />
            </div>
          </div>
          <section class="panel">
            <div class="panel-title">
              <h2>{{ t("finance") }}</h2>
              <span>{{ t("reportHint") }}</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("currency") }}</th>
                    <th>{{ t("rentPaid") }}</th>
                    <th>{{ t("rentBalance") }}</th>
                    <th>{{ t("depositHeld") }}</th>
                    <th>{{ t("netCash") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(v, c) in overview.money" :key="c">
                    <td>
                      <strong>{{ c }}</strong>
                    </td>
                    <td>{{ money(v.rentPaid, c) }}</td>
                    <td>{{ money(v.rentBalance, c) }}</td>
                    <td>{{ money(v.depositHeld, c) }}</td>
                    <td>{{ money(v.netCash, c) }}</td>
                  </tr>
                  <tr v-if="!Object.keys(overview.money).length">
                    <td colspan="5" class="empty">{{ t("noMoney") }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <div class="panel-title">
              <h2>{{ t("recent") }}</h2>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("number") }}</th>
                    <th>{{ t("customerId") }}</th>
                    <th>{{ t("endAt") }} · {{ zone }}</th>
                    <th>{{ t("status") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="b in overview.recent" :key="b.id">
                    <td class="mono">{{ b.number }}</td>
                    <td>{{ cell(b, "customerId") }}</td>
                    <td>{{ fmtDate(b.endAt) }}</td>
                    <td>
                      <span :class="['status', b.status]">{{
                        t(b.status)
                      }}</span>
                    </td>
                    <td>
                      <button
                        v-if="can('booking.read')"
                        class="link"
                        @click="openRental(b.id)"
                      >
                        {{ t("view") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!overview.recent.length">
                    <td colspan="5" class="empty">{{ t("empty") }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
        <section v-else-if="page === 'availability'" class="panel">
          <form
            class="toolbar availability-form"
            @submit.prevent="run(checkAvailability)"
          >
            <label
              >{{ t("startAt")
              }}<input
                v-model="form.startAt"
                type="datetime-local"
                required /></label
            ><label
              >{{ t("endAt")
              }}<input
                v-model="form.endAt"
                type="datetime-local"
                required /></label
            ><button class="primary" :disabled="busy">
              {{ t("checkAvailability") }}</button
            ><small>{{ t("inputTimezone") }}: {{ browserZone }}</small>
          </form>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("code") }}</th>
                  <th>{{ t("name") }}</th>
                  <th>{{ t("dailyRate") }}</th>
                  <th>{{ t("status") }}</th>
                  <th>{{ t("notes") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in available" :key="r.asset.id">
                  <td class="mono">{{ r.asset.code }}</td>
                  <td>{{ r.asset.name }}</td>
                  <td>{{ money(r.asset.dailyRate) }}</td>
                  <td>
                    <span
                      :class="['status', r.available ? 'CLOSED' : 'PARTIAL']"
                      >{{ t(r.available ? "available" : "unavailable") }}</span
                    >
                  </td>
                  <td>{{ r.reason ? t(r.reason) : "—" }}</td>
                </tr>
                <tr v-if="!available.length">
                  <td colspan="5" class="empty">
                    {{ t("checkAvailability") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section v-else-if="page === 'about'" class="panel about">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <h2>{{ t("brand") }}</h2>
          <p>{{ t("version") }}</p>
          <p>上海如静知华信息科技有限公司</p>
          <p>
            {{
              lang === "en"
                ? "Personal learning, technical research and non-commercial exchange only. Written authorization is required for commercial use, business deployment and resale."
                : "仅限个人学习、技术研究与非商业交流。商用、企业部署及二次销售须取得书面授权。"
            }}
          </p>
          <p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
              >https://www.zhuatech.cn/</a
            >
          </p>
          <p>
            {{
              lang === "en"
                ? "Commercial licensing, customization and deployment:"
                : "商业授权、定制与部署咨询："
            }}微信 zhuatech / zhuatech2
          </p>
        </section>
        <section v-else class="panel">
          <form
            class="toolbar"
            @submit.prevent="
              pageNo = 0;
              load();
            "
          >
            <div class="search">
              <Search :size="17" /><input
                v-model="search"
                :placeholder="t('search')"
                :aria-label="t('search')"
                maxlength="200"
              />
            </div>
            <select
              v-if="page === 'bookings'"
              v-model="status"
              :aria-label="t('status')"
            >
              <option value="">{{ t("all") }}</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'CONFIRMED',
                  'OUT',
                  'PARTIAL',
                  'RETURNED',
                  'CLOSED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
              >
                {{ t(s) }}
              </option></select
            ><select
              v-if="page === 'assets'"
              v-model="status"
              :aria-label="t('status')"
            >
              <option value="">{{ t("all") }}</option>
              <option value="READY">{{ t("READY") }}</option>
              <option value="MAINTENANCE">
                {{ t("MAINTENANCE") }}
              </option></select
            ><button>{{ t("filter") }}</button
            ><span class="table-count">{{ total }} {{ t("total") }}</span>
          </form>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th
                    v-for="c in columns[page]"
                    :key="c"
                    @click="
                      ['name', 'code', 'number', 'createdAt'].includes(c) &&
                      ((sort = c), (descending = !descending), load())
                    "
                  >
                    {{ t(c)
                    }}<span v-if="sort === c">{{
                      descending ? " ↓" : " ↑"
                    }}</span>
                  </th>
                  <th v-if="page !== 'audit'">{{ t("actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td :colspan="(columns[page]?.length || 1) + 1" class="empty">
                    {{ t("loading") }}
                  </td>
                </tr>
                <template v-else
                  ><tr v-for="row in rows" :key="row.id">
                    <td v-for="c in columns[page]" :key="c">
                      <span
                        v-if="c === 'status'"
                        :class="['status', row.status]"
                        >{{ cell(row, c) }}</span
                      ><span
                        v-else-if="c === 'maintenance'"
                        :class="[
                          'status',
                          row.maintenance ? 'PARTIAL' : 'CLOSED',
                        ]"
                        >{{
                          t(row.maintenance ? "MAINTENANCE" : "READY")
                        }}</span
                      ><span
                        v-else
                        :class="{ mono: ['number', 'code'].includes(c) }"
                        >{{ cell(row, c) }}</span
                      >
                    </td>
                    <td v-if="page !== 'audit'" class="row-actions">
                      <button
                        v-if="page === 'bookings'"
                        class="link"
                        @click="openRental(row.id)"
                      >
                        {{ t("view") }}</button
                      ><template v-else-if="hasWrite"
                        ><button class="link" @click="openEdit(row)">
                          {{ t("edit") }}</button
                        ><button
                          v-if="page === 'assets'"
                          class="link"
                          @click="maintenanceDialog(row)"
                        >
                          {{ t(row.maintenance ? "release" : "hold") }}</button
                        ><button
                          v-if="
                            !['permissions', 'menus', 'settings'].includes(page)
                          "
                          class="link muted"
                          @click="remove(row)"
                        >
                          {{ t("delete") }}
                        </button></template
                      >
                    </td>
                  </tr>
                  <tr v-if="!rows.length">
                    <td
                      :colspan="(columns[page]?.length || 1) + 1"
                      class="empty"
                    >
                      {{ t("empty") }}
                    </td>
                  </tr></template
                >
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span
              >{{ total ? pageNo * 20 + 1 : 0 }}–{{
                Math.min((pageNo + 1) * 20, total)
              }}
              / {{ total }}</span
            ><button
              :disabled="pageNo === 0 || loading"
              @click="changePage(-1)"
            >
              {{ t("previous") }}</button
            ><button
              :disabled="(pageNo + 1) * 20 >= total || loading"
              @click="changePage(1)"
            >
              {{ t("next") }}
            </button>
          </div>
        </section>
      </main>
    </section>
    <div v-if="drawer" class="drawer-overlay" @click.self="drawer = null">
      <section class="drawer">
        <div class="drawer-head">
          <div>
            <small>{{ t("details") }}</small>
            <h2 class="mono">{{ drawer.booking.number }}</h2>
          </div>
          <button class="icon" :aria-label="t('close')" @click="drawer = null">
            <X :size="22" />
          </button>
        </div>
        <div class="drawer-content">
          <div class="rental-summary">
            <span :class="['status', drawer.booking.status]">{{
              t(drawer.booking.status)
            }}</span
            ><strong>{{ cell(drawer.booking, "customerId") }}</strong
            ><span
              >{{ fmtDate(drawer.booking.startAt) }} →
              {{ fmtDate(drawer.booking.endAt) }} · {{ zone }}</span
            >
            <p v-if="drawer.booking.notes">{{ drawer.booking.notes }}</p>
          </div>
          <div class="detail-actions">
            <template v-if="can('booking.write')"
              ><button
                v-if="drawer.booking.status === 'DRAFT'"
                @click="remove(drawer.booking)"
              >
                {{ t("delete") }}</button
              ><button
                v-if="drawer.booking.status === 'DRAFT'"
                @click="newRental(true)"
              >
                {{ t("edit") }}</button
              ><button
                v-if="drawer.booking.status === 'DRAFT'"
                class="primary"
                @click="actionDialog('confirm')"
              >
                {{ t("confirm") }}</button
              ><button
                v-if="drawer.booking.status === 'CONFIRMED'"
                class="primary"
                @click="actionDialog('checkout')"
              >
                {{ t("checkout") }}</button
              ><button
                v-if="['CONFIRMED', 'OUT'].includes(drawer.booking.status)"
                @click="actionDialog('extend')"
              >
                {{ t("extend") }}</button
              ><button
                v-if="['DRAFT', 'CONFIRMED'].includes(drawer.booking.status)"
                @click="actionDialog('cancel')"
              >
                {{ t("cancel") }}
              </button></template
            ><button
              v-if="
                can('finance') &&
                ['CONFIRMED', 'OUT', 'PARTIAL', 'RETURNED'].includes(
                  drawer.booking.status,
                )
              "
              @click="actionDialog('payment')"
            >
              {{ t("payment") }}</button
            ><button
              v-if="can('finance') && drawer.booking.status === 'RETURNED'"
              class="primary"
              @click="actionDialog('close')"
            >
              {{ t("settle") }}</button
            ><button @click="print">
              <Printer :size="15" />{{ t("print") }}
            </button>
          </div>
          <h3>{{ t("rentalEquipment") }}</h3>
          <div v-for="l in drawer.lines" :key="l.id" class="rental-line">
            <div>
              <strong>{{ l.assetCode }} · {{ l.assetName }}</strong
              ><span>{{ l.accessories }}</span
              ><small
                >{{ t("dailyRate") }} {{ money(l.dailyRate) }} ·
                {{ t("deposit") }} {{ money(l.deposit) }}</small
              ><small v-if="l.returnedAt"
                >{{ t("returnedAt") }} {{ fmtDate(l.returnedAt) }} ·
                {{ l.inspection }}</small
              >
            </div>
            <button
              v-if="
                !l.returnedAt &&
                ['OUT', 'PARTIAL'].includes(drawer.booking.status) &&
                can('booking.write')
              "
              @click="actionDialog('return', l)"
            >
              {{ t("return") }}</button
            ><span v-if="l.returnedAt" class="status CLOSED"
              ><Check :size="14" />{{ t("checkedIn") }}</span
            >
          </div>
          <h3>{{ t("finance") }} · {{ currency }}</h3>
          <dl class="totals">
            <template
              v-for="k in [
                'rent',
                'lateFee',
                'damageFee',
                'rentDue',
                'rentPaid',
                'rentBalance',
                'depositRequired',
                'depositHeld',
                'netCash',
              ]"
              :key="k"
              ><dt>{{ t(k) }}</dt>
              <dd
                :class="{
                  highlight: ['rentBalance', 'depositHeld'].includes(k),
                }"
              >
                {{ money(drawer.totals[k]) }}
              </dd></template
            >
          </dl>
          <h3>{{ t("ledger") }}</h3>
          <div class="table-wrap">
            <table class="small-table">
              <thead>
                <tr>
                  <th>{{ t("createdAt") }}</th>
                  <th>{{ t("kind") }}</th>
                  <th>{{ t("amount") }}</th>
                  <th>{{ t("reference") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="l in drawer.ledger" :key="l.id">
                  <td>{{ fmtDate(l.createdAt) }}</td>
                  <td>{{ t(l.kind) }}</td>
                  <td>{{ money(l.amount) }}</td>
                  <td>{{ l.reference }}</td>
                </tr>
                <tr v-if="!drawer.ledger.length">
                  <td colspan="4" class="empty">{{ t("empty") }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
    <div v-if="modal" class="modal-overlay" @click.self="closeModal">
      <section
        class="modal"
        :class="{ wide: modal.type === 'draft' }"
        role="dialog"
        aria-modal="true"
      >
        <div class="modal-head">
          <h2>
            {{
              modal.type === "edit"
                ? modal.id
                  ? t("edit")
                  : t("new")
                : modal.type === "draft"
                  ? t("newRental")
                  : modal.type === "action"
                    ? t(modal.action === "close" ? "settle" : modal.action)
                    : modal.type === "password"
                      ? t("changePassword")
                      : modal.type === "delete"
                        ? t("delete")
                        : t(form.blocked === "true" ? "hold" : "release")
            }}
          </h2>
          <button class="icon" :aria-label="t('close')" @click="closeModal">
            <X :size="20" />
          </button>
        </div>
        <form
          @submit.prevent="
            modal.type === 'edit'
              ? saveMaster()
              : modal.type === 'draft'
                ? saveRental()
                : modal.type === 'action'
                  ? submitAction()
                  : modal.type === 'password'
                    ? changePassword()
                    : modal.type === 'delete'
                      ? confirmDelete()
                      : saveMaintenance()
          "
        >
          <div class="modal-content">
            <div v-if="error" class="error" role="alert">{{ error }}</div>
            <template v-if="modal.type === 'edit'"
              ><div class="form-grid">
                <component
                  :is="f === 'permissions' ? 'div' : 'label'"
                  class="form-field"
                  :role="f === 'permissions' ? 'group' : undefined"
                  :aria-label="
                    f === 'permissions' ? t('permissions') : undefined
                  "
                  v-for="f in fields[page]"
                  :key="f"
                  :class="{
                    full: ['notes', 'accessories', 'permissions'].includes(f),
                  }"
                  >{{ page === "settings" ? t(form.code) : t(f)
                  }}<select
                    v-if="
                      [
                        'departmentId',
                        'categoryId',
                        'roleId',
                        'scope',
                        'permissionCode',
                      ].includes(f)
                    "
                    v-model="form[f]"
                    required
                  >
                    <template v-if="f === 'departmentId'"
                      ><option
                        v-for="d in catalog.departments"
                        :key="d.id"
                        :value="d.id"
                      >
                        {{ d.name }}
                      </option></template
                    ><template v-if="f === 'categoryId'"
                      ><option
                        v-for="d in catalog.categories"
                        :key="d.id"
                        :value="d.id"
                      >
                        {{ lang === "en" ? d.nameEn : d.name }}
                      </option></template
                    ><template v-if="f === 'roleId'"
                      ><option
                        v-for="d in roleOptions"
                        :key="d.id"
                        :value="d.id"
                      >
                        {{ d.name }}
                      </option></template
                    ><template v-if="f === 'scope'"
                      ><option value="DEPARTMENT">{{ t("DEPARTMENT") }}</option>
                      <option value="ALL">{{ t("ALL") }}</option></template
                    ><template v-if="f === 'permissionCode'"
                      ><option
                        v-for="d in permissionOptions"
                        :key="d.id"
                        :value="d.code"
                      >
                        {{ d.name }}
                      </option></template
                    >
                  </select>
                  <div
                    v-else-if="f === 'permissions'"
                    class="permission-checks"
                  >
                    <label v-for="p in permissionOptions" :key="p.id"
                      ><input
                        v-model="form.permissions"
                        type="checkbox"
                        :value="p.code"
                      />{{
                        p.name.split(" / ")[lang === "en" ? 1 : 0] || p.name
                      }}</label
                    >
                  </div>
                  <select v-else-if="f === 'enabled'" v-model="form.enabled">
                    <option :value="true">{{ t("yes") }}</option>
                    <option :value="false">{{ t("no") }}</option></select
                  ><textarea
                    v-else-if="['notes', 'accessories'].includes(f)"
                    v-model="form[f]"
                    maxlength="1000"
                    rows="3"
                  ></textarea
                  ><input
                    v-else
                    v-model="form[f]"
                    :type="
                      f === 'password'
                        ? 'password'
                        : ['dailyRate', 'deposit', 'position'].includes(f)
                          ? 'number'
                          : 'text'
                    "
                    :min="0"
                    :step="f === 'position' ? 1 : 0.01"
                    :required="
                      !['contact', 'notes', 'accessories'].includes(f) &&
                      !(f === 'password' && modal.id)
                    "
                    :autocomplete="f === 'password' ? 'new-password' : 'off'"
                    :maxlength="f === 'password' ? 72 : 200"
                  /><small v-if="f === 'password'">{{
                    t("passwordHint")
                  }}</small></component
                >
              </div></template
            >
            <template v-else-if="modal.type === 'draft'"
              ><div class="form-grid">
                <label
                  >{{ t("customerId")
                  }}<select v-model="form.customerId" required>
                    <option
                      v-for="c in customerOptions"
                      :key="c.id"
                      :value="c.id"
                    >
                      {{ c.name }}
                    </option>
                  </select></label
                >
                <div></div>
                <label
                  >{{ t("startAt")
                  }}<input
                    v-model="form.startAt"
                    type="datetime-local"
                    required /></label
                ><label
                  >{{ t("endAt")
                  }}<input v-model="form.endAt" type="datetime-local" required
                /></label>
                <div class="full availability-controls">
                  <small>{{ t("inputTimezone") }}: {{ browserZone }}</small
                  ><button
                    type="button"
                    :disabled="busy"
                    @click="run(checkAvailability)"
                  >
                    {{ t("checkAvailability") }}
                  </button>
                </div>
                <fieldset class="full">
                  <legend>{{ t("pickAssets") }}</legend>
                  <label
                    v-for="r in available"
                    :key="r.asset.id"
                    class="asset-choice"
                    ><input
                      v-model="form.assetIds"
                      type="checkbox"
                      :value="r.asset.id"
                      :disabled="!r.available"
                    /><span
                      ><strong>{{ r.asset.code }} · {{ r.asset.name }}</strong
                      ><small
                        >{{ money(r.asset.dailyRate) }} /
                        {{ lang === "en" ? "day" : "天" }} ·
                        {{ r.reason ? t(r.reason) : t("available") }}</small
                      ></span
                    ></label
                  >
                  <p v-if="!available.length" class="muted">
                    {{ t("checkAvailability") }}
                  </p>
                </fieldset>
                <label class="full"
                  >{{ t("notes")
                  }}<textarea
                    v-model="form.notes"
                    maxlength="1000"
                    rows="2"
                  ></textarea>
                </label></div
            ></template>
            <template v-else-if="modal.type === 'action'"
              ><p class="operation-summary">
                <strong>{{ drawer.booking.number }}</strong> ·
                {{ cell(drawer.booking, "customerId") }}
              </p>
              <template v-if="modal.action === 'payment'"
                ><p class="hint">{{ t("paymentHint") }}</p>
                <label
                  >{{ t("kind")
                  }}<select v-model="form.kind">
                    <option
                      v-for="k in [
                        'RENT_RECEIPT',
                        'DEPOSIT_RECEIPT',
                        'RENT_REFUND',
                        'DEPOSIT_REFUND',
                        'DEPOSIT_DEDUCTION',
                      ]"
                      :key="k"
                      :value="k"
                    >
                      {{ t(k) }}
                    </option>
                  </select></label
                ><label
                  >{{ t("amount") }} · {{ currency
                  }}<input
                    v-model="form.amount"
                    type="number"
                    min="0.01"
                    step="0.01"
                    required /></label
                ><label
                  >{{ t("reference")
                  }}<input
                    v-model="form.reference"
                    maxlength="120"
                    required /></label
                ><label
                  >{{ t("note")
                  }}<textarea
                    v-model="form.note"
                    maxlength="500"
                    rows="2"
                  ></textarea></label></template
              ><template v-else-if="modal.action === 'return'"
                ><div class="inspection-list">
                  <strong>{{ t("returnChecks") }}</strong
                  ><span
                    v-for="d in catalog.dictionaries.filter(
                      (x) => x.type === 'inspection',
                    )"
                    :key="d.id"
                    >{{ lang === "en" ? d.nameEn : d.name }}</span
                  >
                </div>
                <label
                  >{{ t("inspectionPassed")
                  }}<select v-model="form.inspectionPassed">
                    <option :value="true">{{ t("yes") }}</option>
                    <option :value="false">
                      {{ t("no") }} · {{ t("MAINTENANCE") }}
                    </option>
                  </select></label
                ><label
                  >{{ t("damageFee") }} · {{ currency
                  }}<input
                    v-model="form.amount"
                    type="number"
                    min="0"
                    step="0.01"
                    required /></label
                ><label
                  >{{ t("note")
                  }}<textarea
                    v-model="form.note"
                    required
                    maxlength="1000"
                    rows="3"
                  ></textarea></label></template
              ><template v-else-if="modal.action === 'extend'"
                ><label
                  >{{ t("endAt")
                  }}<input
                    v-model="form.endAt"
                    type="datetime-local"
                    required /></label
                ><small
                  >{{ t("inputTimezone") }}: {{ browserZone }}</small
                ></template
              >
              <p v-else>{{ t("operationConfirm") }}</p></template
            >
            <template v-else-if="modal.type === 'maintenance'"
              ><strong>{{ modal.row.code }} · {{ modal.row.name }}</strong
              ><label
                >{{ t("maintenanceNote")
                }}<textarea
                  v-model="form.note"
                  required
                  maxlength="1000"
                  rows="3"
                ></textarea></label
            ></template>
            <template v-else-if="modal.type === 'password'"
              ><label
                >{{ t("oldPassword")
                }}<input
                  v-model="form.oldPassword"
                  type="password"
                  autocomplete="current-password"
                  required /></label
              ><label
                >{{ t("newPassword")
                }}<input
                  v-model="form.newPassword"
                  type="password"
                  autocomplete="new-password"
                  minlength="12"
                  maxlength="72"
                  required /></label
              ><small>{{ t("passwordHint") }}</small></template
            >
            <p v-else>{{ t("deleteQuestion") }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" :disabled="busy" @click="closeModal">
              {{ t("cancel") }}</button
            ><button
              class="primary"
              :disabled="
                busy || (modal.type === 'draft' && !form.assetIds.length)
              "
            >
              {{
                busy
                  ? t("loading")
                  : t(modal.type === "delete" ? "delete" : "save")
              }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
