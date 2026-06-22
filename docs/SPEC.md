# TravelMate — Đặc tả kỹ thuật đầy đủ (Spec cho Claude Code)

> Tài liệu này là nguồn chân lý (source of truth) để build app quản lý chuyến đi nhóm: lập kế hoạch, timeline, ngân sách, chi tiêu, quỹ chung và chia tiền.
> Quy tắc chung khi implement: **luôn tuân thủ các convention ở Mục 2–5**, không tự ý đổi kiểu dữ liệu tiền tệ, không expose `ID` nội bộ ra API.

---

## 0. Mục tiêu & phạm vi

- App cho **nhóm đi chung** (gia đình, bạn bè, công tác), chạy **đa nền tảng: Android + Web** (Flutter, một codebase, responsive) — KHÔNG chỉ Android như family-budget.
- **UI phải luôn hiện đại** (Material 3, responsive, polished) trên mọi nền tảng.
- Lập kế hoạch chuyến đi (timeline, di chuyển, lưu trú, checklist).
- Quản lý **ngân sách dự kiến** vs **chi tiêu thực tế**, **quỹ chung**, và **chia tiền/công nợ**.
- Hỗ trợ **đa tiền tệ** với tỷ giá snapshot.
- Phase 1 tập trung core. Các tính năng AI/OCR/Offline để Phase 2 (Mục 12).

---

## 1. Tech stack

| Layer | Công nghệ |
|---|---|
| Client | Flutter (stable mới nhất) — **Android + Web** (responsive, Material 3, UI hiện đại), `flutter_map` (OpenStreetMap), `firebase_messaging`, render QR client-side từ chuỗi (vd `qr_flutter`) |
| Backend | Spring Boot 3.x, Java 21, Spring Web, Spring Security, Spring Data JPA, Spring Validation |
| Scheduler | Spring `@Scheduled` (hoặc Quartz nếu cần cluster) |
| Database | Oracle Autonomous Database Free **hoặc** PostgreSQL (xem Mục 13 về đánh đổi) |
| Migration | **Flyway** (bắt buộc — versioned migration, không dùng `ddl-auto=update` ở prod) |
| Auth | Google Sign-In + Email/Password (JWT access + refresh token) |
| Push | Firebase Cloud Messaging |
| Hosting | Oracle Cloud Always Free VM (ưu tiên Ampere A1 ARM) |

**Lưu ý hạ tầng:** JVM cần RAM — ưu tiên instance Ampere A1 (4 OCPU/24GB). VM micro x86 1GB sẽ rất chật cho Spring Boot.

---

## 2. Quy ước kiến trúc (conventions) — BẮT BUỘC

### 2.1. Base entity
Mọi bảng kế thừa các cột sau (dùng `@MappedSuperclass` `BaseEntity`):

```text
ID            NUMBER(19)     PK, sinh bởi sequence/identity
RID           VARCHAR2(36)   UUID v7 (time-ordered — index locality tốt hơn v4), NOT NULL, UNIQUE — dùng để expose ra API
CREATED_AT    TIMESTAMP      (UTC)
CREATED_BY    NUMBER(19)     -> USERS.ID (nullable cho hệ thống)
UPDATED_AT    TIMESTAMP      (UTC)
UPDATED_BY    NUMBER(19)
VERSION       NUMBER(10)     @Version — optimistic locking
IS_DELETED    NUMBER(1)      DEFAULT 0 — soft delete
```

- `CREATED_AT/UPDATED_AT/BY` set tự động bằng JPA Auditing (`@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`).
- Mọi truy vấn mặc định lọc `IS_DELETED = 0` bằng Hibernate **`@SQLRestriction("IS_DELETED = 0")`** (KHÔNG dùng `@Where` — đã deprecated từ Hibernate 6.3+).
- ⚠️ Lưu ý: filter này áp dụng **global**, sẽ ẩn cả dòng đã soft-delete khi JOIN/FETCH qua FK — phải test kỹ các báo cáo lịch sử / settlement có tham chiếu tới bản ghi đã xóa.

### 2.2. ID vs RID
- **ID (NUMBER 19):** chỉ dùng nội bộ — khóa chính, khóa ngoại, join. **Không bao giờ trả ra ngoài.**
- **RID (UUID):** định danh public, dùng trong URL và response. Khi nhận `{rid}` từ client → resolve sang ID nội bộ trước khi xử lý.
- Index: `UK_<TABLE>_RID` cho mọi bảng.

### 2.3. Soft delete + unique
- Vì có `IS_DELETED`, các unique nghiệp vụ (vd `USERS.EMAIL`) phải là **partial unique** chỉ áp dụng khi chưa xóa.
  - Oracle: `CREATE UNIQUE INDEX UK_USERS_EMAIL ON USERS (CASE WHEN IS_DELETED = 0 THEN EMAIL END);`
  - Postgres: `CREATE UNIQUE INDEX UK_USERS_EMAIL ON USERS (EMAIL) WHERE IS_DELETED = FALSE;`

### 2.4. Tiền tệ (BẮT BUỘC — không dùng float/double)
- Mọi số tiền: `NUMBER(19,4)` ↔ Java `BigDecimal`.
- Tỷ giá: `NUMBER(19,8)`.
- Quy tắc đa tiền tệ: mỗi giao dịch tiền (expense, fund) lưu:
  - `CURRENCY` (ISO 4217, vd `VND`, `JPY`)
  - `AMOUNT` (nguyên tệ)
  - `EXCHANGE_RATE` (tỷ giá quy về `TRIP.BASE_CURRENCY` tại thời điểm chi — **snapshot, không tính lại sau**)
  - `AMOUNT_BASE` = `AMOUNT * EXCHANGE_RATE` (lưu sẵn để báo cáo & settlement nhanh)
- **Mọi tính toán tổng hợp, ngân sách, settlement đều chạy trên `AMOUNT_BASE`** (cùng `BASE_CURRENCY` của trip).
- **Nguồn tỷ giá (`ExchangeRateProvider`):** dùng API free không cần key — [frankfurter.app](https://frankfurter.app) hoặc exchangerate.host. Cache rate theo ngày. Luôn cho phép user **override tỷ giá thủ công** (tỷ giá đổi tiền thực tế thường lệch tỷ giá thị trường) — `EXCHANGE_RATE` nhập tay vẫn là snapshot.

### 2.5. Thời gian & múi giờ
- Lưu **tất cả mốc thời gian ở UTC** (`TIMESTAMP`).
- Hiển thị: convert theo `TRIP.TIMEZONE` (hoặc timezone của địa điểm với chuyến bay xuyên múi giờ).
- Event di chuyển (flight) lưu kèm timezone gốc nếu cần hiển thị "giờ địa phương đi/đến".

### 2.6. Audit & versioning
- Optimistic lock qua `@Version` trên `VERSION`.
- Khi update bị `OptimisticLockException` → trả `409 Conflict`.

### 2.7. Mã QR — lưu CHUỖI, không lưu ảnh (BẮT BUỘC)
- Vé/booking có mã QR: client **scan QR → lấy chuỗi đã giải mã → gửi chuỗi** lên server; server **chỉ lưu chuỗi** (cột `QR_DATA`).
- Khi user xem: client **tự sinh lại QR từ chuỗi** (vd `qr_flutter`). **Không bao giờ lưu ảnh QR** (tốn dung lượng, khó tái dùng).
- Áp dụng cho vé (Transport/Accommodation — Module 6/7) và **link mời nhóm** (Module 3: server trả token/URL dạng chuỗi, client render QR).

---

## 3. Cấu trúc package backend

```
com.travelmate
├── common
│   ├── entity        (BaseEntity, enums)
│   ├── exception     (ApiException, GlobalExceptionHandler)
│   ├── security      (JWT, SecurityConfig, CurrentUser)
│   ├── audit         (JpaAuditingConfig)
│   └── money         (MoneyService, ExchangeRateProvider)
├── user
├── trip
├── member
├── place
├── timeline         (events)
├── transport
├── accommodation
├── budget
├── expense
├── fund
├── settlement       (Settlement Engine)
├── checklist
├── dashboard
└── notification     (FCM + scheduler)
```

Mỗi module theo lát cắt: `*Controller` → `*Service` → `*Repository` → `*Entity` + `dto/` (Request/Response) + `mapper/`.

---

## 4. Quy ước API

- Base path: `/api/v1`.
- Định danh resource qua **RID**, không bao giờ qua ID:
  - `GET /api/v1/trips/{rid}`
  - `GET /api/v1/expenses/{rid}`
  - `GET /api/v1/events/{rid}`
- Resource lồng nhau theo trip:
  - `GET    /api/v1/trips/{tripRid}/expenses`
  - `POST   /api/v1/trips/{tripRid}/expenses`
  - `PATCH  /api/v1/expenses/{rid}`
  - `DELETE /api/v1/expenses/{rid}` (soft delete)
- Response chuẩn: bọc trong envelope `{ data, error, meta }`. Phần lỗi theo chuẩn **RFC 7807 Problem Details** (Spring Boot 3 hỗ trợ sẵn `ProblemDetail`): `{ type, title, status, detail, fieldErrors[] }`.
- Phân trang: `?page=&size=&sort=`.
- Validation bằng Bean Validation (`@Valid`).
- **Idempotency:** các POST tạo tiền (expense, fund contribution) nhận header `Idempotency-Key` (UUID do client sinh). Server lưu key + kết quả; request trùng key → trả lại kết quả cũ. Tránh tạo trùng do mobile mạng yếu / double-submit.
- HTTP codes: 200/201/204 thành công, 400 validation, 401 chưa auth, 403 không đủ quyền, 404 không thấy/không thuộc trip, 409 optimistic lock.

---

## 5. Bảo mật & phân quyền

### 5.1. Authentication
- Email/Password: mật khẩu hash **BCrypt**. Có luồng **xác thực email** + **reset password** qua token (bảng `AUTH_TOKENS`).
- Google Sign-In: verify ID token từ Google, tạo/link user theo email.
- Cấp **JWT access token (ngắn hạn)** + **refresh token (dài hạn, lưu DB để revoke)**.
- **Refresh token rotation:** mỗi lần `/auth/refresh` cấp refresh token mới và vô hiệu token cũ. Nếu một token đã dùng bị dùng lại (reuse) → coi là bị lộ, revoke toàn bộ phiên của user đó.

### 5.2. Authorization (rất quan trọng)
- Mọi endpoint trip-scoped phải kiểm tra **user hiện tại có là thành viên của trip** (qua `TRIP_MEMBERS`), kể cả khi truy cập bằng `{rid}` của resource con.
- Vai trò trong trip: `OWNER`, `EDITOR`, `VIEWER`.
  - `VIEWER`: chỉ đọc.
  - `EDITOR`: thêm/sửa event, expense, fund, checklist...
  - `OWNER`: toàn quyền + quản lý thành viên + xóa trip.
- Tạo annotation/aspect `@RequireTripRole(MIN_ROLE)` hoặc check trong service. **Không tin client.**
- **Tập trung hoá kiểm tra truy cập:** làm một `TripAccessGuard` dùng chung theo pattern `resource rid → load → suy ra tripId → check membership + role`. KHÔNG để mỗi service tự viết lại logic này (đây là chỗ dễ sót quyền nhất, đặc biệt với resource con truy cập trực tiếp bằng `{rid}`).

### 5.3. Hardening API (BẮT BUỘC cho API mobile công khai)
- **Rate limiting** theo IP + theo user (đặc biệt các endpoint auth: login / refresh / forgot-password).
- **CORS** whitelist origin rõ ràng (không `*` khi đã có credential).
- **Giới hạn body size** request (chống payload lớn).

---

## 6. Danh sách bảng & DDL (tổng quan)

> Tất cả bảng có base columns ở Mục 2.1. Dưới đây chỉ liệt kê cột nghiệp vụ + FK + index. FK luôn trỏ tới **ID nội bộ**.
> Quy tắc index: tạo index cho **mọi FK** và các cột lọc/sort thường dùng.

### Enums dùng chung

```text
TripType        : FAMILY, FRIENDS, BUSINESS, BACKPACKING, OTHER
TripStatus      : PLANNING, ONGOING, COMPLETED, CANCELLED
MemberRole      : OWNER, EDITOR, VIEWER
PlaceType       : HOTEL, RESTAURANT, ATTRACTION, AIRPORT, STATION, SHOPPING, OTHER
EventType       : TRANSPORT, HOTEL, FOOD, ACTIVITY, SIGHTSEEING, SHOPPING, OTHER
TransportType   : FLIGHT, TRAIN, BUS, FERRY, TAXI, RENTAL_VEHICLE
ExpenseType     : PLANNED, UNEXPECTED
-- DANH MỤC HỢP NHẤT cho cả BUDGET và EXPENSE (để báo cáo so sánh được):
Category        : TRANSPORT, ACCOMMODATION, FOOD, SHOPPING, ACTIVITY, SIGHTSEEING, MEDICAL, PARKING, OTHER
SplitType       : EQUAL, EXACT, PERCENT, SHARES
```

---

## 7. Đặc tả từng module

### MODULE 1 — USER

**Chức năng:** đăng ký (Google / Email), hồ sơ (tên, avatar, email, múi giờ, tiền tệ mặc định).

```text
USERS
  EMAIL              VARCHAR2(255)  partial-unique (Mục 2.3)
  PASSWORD_HASH      VARCHAR2(255)  nullable (null nếu chỉ login Google)
  NAME               VARCHAR2(150)
  AVATAR             VARCHAR2(500)
  TIMEZONE           VARCHAR2(64)   default 'Asia/Ho_Chi_Minh'
  DEFAULT_CURRENCY   VARCHAR2(3)    default 'VND'
  EMAIL_VERIFIED     NUMBER(1)      default 0
  PROVIDER           VARCHAR2(20)   LOCAL | GOOGLE
```


```text
USER_DEVICES                         -- để gửi FCM
  USER_ID            NUMBER(19) FK
  FCM_TOKEN          VARCHAR2(500)
  PLATFORM           VARCHAR2(20)    ANDROID | IOS
  LAST_SEEN_AT       TIMESTAMP
  idx: (USER_ID), unique(FCM_TOKEN)
```


```text
AUTH_TOKENS                          -- verify email & reset password & refresh
  USER_ID            NUMBER(19) FK
  TYPE               VARCHAR2(20)    EMAIL_VERIFY | PASSWORD_RESET | REFRESH
  TOKEN              VARCHAR2(255)   (hash của token)
  EXPIRES_AT         TIMESTAMP
  USED_AT            TIMESTAMP       nullable
  idx: (USER_ID, TYPE), (TOKEN)
```

**API:** `POST /auth/register`, `/auth/login`, `/auth/google`, `/auth/refresh`, `/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/change-password`; `GET/PATCH/DELETE /users/me`; `POST /users/me/devices` (đăng ký FCM token).

**Settings support (slice cho Flutter app):**
- `POST /auth/change-password {currentPassword?, newPassword}` → 204. Tài khoản chỉ-Google (chưa có mật khẩu) được đặt mật khẩu đầu tiên (bỏ `currentPassword`); ngược lại bắt buộc và verify `currentPassword` (400 nếu sai). Đổi mật khẩu thu hồi mọi refresh token.
- `GET /users/me` trả `name, email, phone, defaultCurrency, provider, hasPassword, emailVerified`. `PATCH /users/me` nhận `name, phone (E.164-ish, nullable), defaultCurrency` (validate theo tập tiền tệ hỗ trợ). Cột mới `USERS.PHONE VARCHAR2(32)` (migration V8).
- `DELETE /users/me` → 204: soft-delete user (`IS_DELETED`) + thu hồi refresh token. `JwtAuthenticationFilter` kiểm tra user còn tồn tại (qua `@SQLRestriction`) nên access token còn hạn của tài khoản đã xóa bị từ chối (401).
- `GET /privacy?lang=vi|en`: trang HTML chính sách quyền riêng tư **public, không auth**, song ngữ (mặc định `vi`), **framable** (không set `X-Frame-Options`) cho WebView trong app + store listing. Router-only (không DB), `EFFECTIVE_DATE` là hằng số.
- `GET /rates` → `{baseCurrency, updatedAt, rates:[{currency, rateToBase}]}` cho tập tiền tệ app hỗ trợ (base VND: VND, USD, EUR, JPY, KRW, THB, SGD, CNY, AUD, GBP). `POST /rates/refresh` ép fetch lại. Cache trong bộ nhớ, refresh job `@Scheduled` mỗi 12h theo giờ đồng hồ (cron, không `fixedRate`); reuse `FrankfurterExchangeRateProvider`; `503 EXCHANGE_RATE_UNAVAILABLE` nếu nguồn không truy cập được. Yêu cầu auth.

---

### MODULE 2 — TRIP

```text
TRIPS
  NAME            VARCHAR2(200)
  DESCRIPTION     VARCHAR2(2000)
  DESTINATION     VARCHAR2(300)
  START_DATE      DATE
  END_DATE        DATE
  TIMEZONE        VARCHAR2(64)
  BASE_CURRENCY   VARCHAR2(3)
  STATUS          VARCHAR2(20)   TripStatus, default PLANNING
  OWNER_ID        NUMBER(19) FK -> USERS.ID
  idx: (OWNER_ID), (STATUS)
```

**Business rules:** tạo trip → tự tạo `TRIP_MEMBERS` cho owner với role `OWNER`. `END_DATE >= START_DATE`. Đổi `BASE_CURRENCY` sau khi đã có expense → cảnh báo (vì amount_base đã snapshot).

**API:** CRUD `/trips`, `GET /trips/{rid}`, `GET /trips` (của user hiện tại).

---

### MODULE 3 — TRIP MEMBERS (+ ghost member + invitation)

> Thiết kế member-centric: **mọi giao dịch tiền (payer, contribution, share) tham chiếu `TRIP_MEMBERS.ID`, không tham chiếu `USERS.ID`**. Nhờ vậy có thể chia tiền với người **không cài app** (ghost member).

```text
TRIP_MEMBERS
  TRIP_ID        NUMBER(19) FK
  USER_ID        NUMBER(19) FK nullable   -- null = ghost (không có tài khoản)
  DISPLAY_NAME   VARCHAR2(150)            -- tên hiển thị (bắt buộc cho ghost)
  ROLE           VARCHAR2(20)             MemberRole
  JOINED_AT      TIMESTAMP
  idx: (TRIP_ID), (USER_ID), unique(TRIP_ID, USER_ID) where USER_ID not null
```


```text
TRIP_INVITATIONS
  TRIP_ID        NUMBER(19) FK
  TOKEN          VARCHAR2(64) unique      -- nhúng vào link / QR
  ROLE           VARCHAR2(20)             role sẽ cấp khi accept
  EXPIRES_AT     TIMESTAMP
  MAX_USES       NUMBER(5)    default 1
  USED_COUNT     NUMBER(5)    default 0
  idx: (TRIP_ID), unique(TOKEN)
```

**Business rules:** accept invitation phải **atomic** để tránh race condition vượt `MAX_USES` — dùng `UPDATE TRIP_INVITATIONS SET USED_COUNT = USED_COUNT + 1 WHERE TOKEN = ? AND USED_COUNT < MAX_USES AND EXPIRES_AT > now` rồi kiểm tra số dòng affected (KHÔNG read-check-write).

**API:** `GET/POST/DELETE /trips/{tripRid}/members`, `PATCH` role; `POST /trips/{tripRid}/invitations` (sinh link+QR), `POST /invitations/{token}/accept`.

---

### MODULE 4 — PLACES

```text
PLACES
  TRIP_ID        NUMBER(19) FK nullable   -- null = place dùng chung; có giá trị = riêng trip
  NAME           VARCHAR2(200)
  ADDRESS        VARCHAR2(500)
  LATITUDE       NUMBER(10,7)
  LONGITUDE      NUMBER(10,7)
  TYPE           VARCHAR2(20)  PlaceType
  idx: (TRIP_ID), (TYPE)
```

> Quyết định: dùng place **theo trip** (TRIP_ID not null) cho đơn giản, tránh chuyện sửa/xóa place dùng chung ảnh hưởng trip khác. Để nullable nếu sau này muốn catalog chung.

---

### MODULE 5 — TIMELINE / EVENTS

```text
EVENTS
  TRIP_ID     NUMBER(19) FK
  TITLE       VARCHAR2(200)
  TYPE        VARCHAR2(20)  EventType
  START_TIME  TIMESTAMP     (UTC)
  END_TIME    TIMESTAMP     (UTC) nullable
  PLACE_ID    NUMBER(19) FK nullable
  NOTE        VARCHAR2(2000)
  idx: (TRIP_ID, START_TIME), (PLACE_ID)
```

**API:** CRUD theo trip; `GET /trips/{tripRid}/events?from=&to=` sắp theo `START_TIME`.

---

### MODULE 6 — TRANSPORT

```text
TRANSPORTS
  TRIP_ID          NUMBER(19) FK
  TYPE             VARCHAR2(20)  TransportType
  PROVIDER         VARCHAR2(150)
  BOOKING_CODE     VARCHAR2(100)
  DEPARTURE_PLACE  VARCHAR2(300)
  ARRIVAL_PLACE    VARCHAR2(300)
  DEPARTURE_TIME   TIMESTAMP (UTC)
  ARRIVAL_TIME     TIMESTAMP (UTC)
  NOTE             VARCHAR2(2000)
  -- Ghế & QR vé từng người KHÔNG ở đây — chúng nằm trên TICKETS gắn vào chặng này (V17).
  idx: (TRIP_ID, DEPARTURE_TIME)
```

---

### MODULE 7 — ACCOMMODATION

```text
ACCOMMODATIONS
  TRIP_ID        NUMBER(19) FK
  NAME           VARCHAR2(200)
  BOOKING_CODE   VARCHAR2(100)
  ADDRESS        VARCHAR2(500)
  CHECKIN_TIME   TIMESTAMP (UTC)
  CHECKOUT_TIME  TIMESTAMP (UTC)
  NOTE           VARCHAR2(2000)
  -- Voucher/QR booking nằm trên TICKETS (vé nhóm) gắn vào lưu trú này, không ở đây (V17).
  idx: (TRIP_ID, CHECKIN_TIME)
```

---

### MODULE 8 — BUDGET (ngân sách dự kiến)

```text
BUDGETS
  TRIP_ID         NUMBER(19) FK
  CATEGORY        VARCHAR2(20)   Category (enum hợp nhất)
  PLANNED_AMOUNT  NUMBER(19,4)   -- theo BASE_CURRENCY của trip
  idx: (TRIP_ID), unique(TRIP_ID, CATEGORY) where IS_DELETED=0
```

> Dùng chung enum `Category` với EXPENSE để Module 13/báo cáo so sánh **Budget vs Actual theo từng danh mục** không bị lệch.

---

### MODULE 9 — EXPENSE (chi tiêu thực tế)

```text
EXPENSES
  TRIP_ID        NUMBER(19) FK
  TITLE          VARCHAR2(200)
  CATEGORY       VARCHAR2(20)   Category
  EXPENSE_TYPE   VARCHAR2(20)   ExpenseType (PLANNED | UNEXPECTED)
  CURRENCY       VARCHAR2(3)
  AMOUNT         NUMBER(19,4)   -- nguyên tệ
  EXCHANGE_RATE  NUMBER(19,8)   -- snapshot quy về BASE_CURRENCY
  AMOUNT_BASE    NUMBER(19,4)   -- = AMOUNT * EXCHANGE_RATE
  PAYER_ID       NUMBER(19) FK -> TRIP_MEMBERS.ID   -- ai trả
  PLACE_ID       NUMBER(19) FK nullable
  PAID_FROM_FUND NUMBER(1) default 0   -- 1 nếu chi từ quỹ chung (xem Module 10)
  NOTE           VARCHAR2(2000)
  SPENT_AT       TIMESTAMP (UTC)
  idx: (TRIP_ID, CATEGORY), (PAYER_ID), (TRIP_ID, SPENT_AT)
```

**Business rules:** nếu `CURRENCY == BASE_CURRENCY` thì `EXCHANGE_RATE = 1`. Service phải tự tính `AMOUNT_BASE`. Khi `PAID_FROM_FUND = 1`, khoản này được coi là chi từ quỹ (không tạo công nợ cá nhân, xem Module 11).

---

### MODULE 10 — QUỸ CHUNG

```text
FUND_CONTRIBUTIONS                 -- ai góp bao nhiêu vào quỹ
  TRIP_ID        NUMBER(19) FK
  MEMBER_ID      NUMBER(19) FK -> TRIP_MEMBERS.ID
  CURRENCY       VARCHAR2(3)
  AMOUNT         NUMBER(19,4)
  EXCHANGE_RATE  NUMBER(19,8)
  AMOUNT_BASE    NUMBER(19,4)
  NOTE           VARCHAR2(500)
  idx: (TRIP_ID), (MEMBER_ID)
```


```text
FUND_EXPENSES                      -- chi ra từ quỹ (nếu muốn tách khỏi EXPENSES)
  TRIP_ID        NUMBER(19) FK
  TITLE          VARCHAR2(200)
  CATEGORY       VARCHAR2(20)  Category
  CURRENCY       VARCHAR2(3)
  AMOUNT         NUMBER(19,4)
  EXCHANGE_RATE  NUMBER(19,8)
  AMOUNT_BASE    NUMBER(19,4)
  NOTE           VARCHAR2(2000)
  idx: (TRIP_ID)
```

**Quy ước chống đếm trùng (quan trọng):**
- **Số dư quỹ** = `SUM(FUND_CONTRIBUTIONS.AMOUNT_BASE) − SUM(FUND_EXPENSES.AMOUNT_BASE) − SUM(EXPENSES.AMOUNT_BASE WHERE PAID_FROM_FUND=1)`.
- Luôn **tính số dư bằng aggregation**, không lưu biến balance (tránh race condition).
- Một khoản chi **hoặc** là chi quỹ (`FUND_EXPENSES` / `EXPENSES.PAID_FROM_FUND=1`) **hoặc** là chi cá nhân tạo công nợ — không bao giờ cả hai.
- "Tổng đã chi" trong báo cáo = chi cá nhân + chi từ quỹ, nhưng mỗi khoản chỉ đếm một lần.

---

### MODULE 11 — CHIA TIỀN & SETTLEMENT

```text
EXPENSE_SHARES                     -- một expense cá nhân được chia cho ai, bao nhiêu
  EXPENSE_ID     NUMBER(19) FK
  MEMBER_ID      NUMBER(19) FK -> TRIP_MEMBERS.ID
  SHARE_BASE     NUMBER(19,4)   -- phần phải chịu (theo BASE_CURRENCY)
  idx: (EXPENSE_ID), (MEMBER_ID)
```

**Cách tạo share theo `SplitType`:**
- `EQUAL`: chia đều `AMOUNT_BASE` cho N member (chia phần dư cho member đầu để tổng khớp tuyệt đối).
- `EXACT`: nhập từng số tiền (tổng phải = `AMOUNT_BASE`).
- `PERCENT`: nhập %, tổng = 100%.
- `SHARES`: nhập số phần (vd 1:2:1).
- Validate: `SUM(SHARE_BASE) == EXPENSE.AMOUNT_BASE` (sai số ≤ 1 đơn vị nhỏ nhất).

**Settlement Engine (debt simplification — chạy khi mở màn tổng kết):**

1. Với mỗi member, tính **net balance** (theo BASE_CURRENCY):
   - `paid` = `SUM(EXPENSES.AMOUNT_BASE WHERE PAYER=member AND PAID_FROM_FUND=0)`
   - `owed` = `SUM(EXPENSE_SHARES.SHARE_BASE WHERE member=member)` *(chỉ tính share của expense không chi từ quỹ)*
   - `net = paid − owed`
   - *(Quỹ chung được tách riêng: góp dư/thiếu quỹ là một bài toán đối soát độc lập, không trộn vào settlement cá nhân.)*
2. Phân nhóm: **creditors** (`net > 0`), **debtors** (`net < 0`).
3. **Greedy min cash-flow:** lặp — ghép debtor nợ nhiều nhất với creditor cần thu nhiều nhất, chuyển `min(|debtor|, creditor)`; cập nhật, loại bỏ bên về 0; lặp đến hết. Cho ra danh sách tối thiểu các giao dịch.

Kết quả ví dụ:

```
Lan  → Nhân : 350.000
Minh → Lan  : 120.000
```

**API:** `GET /trips/{tripRid}/settlement` → trả balances + danh sách giao dịch tối thiểu. (Stateless: tính on-the-fly, có thể cache theo version dữ liệu.)

---

### MODULE 12 — CHECKLIST

```text
CHECKLIST_ITEMS
  TRIP_ID     NUMBER(19) FK
  TITLE       VARCHAR2(300)
  COMPLETED   NUMBER(1) default 0
  ASSIGNEE_ID NUMBER(19) FK -> TRIP_MEMBERS.ID nullable
  SORT_ORDER  NUMBER(10)
  idx: (TRIP_ID)
```

---

### MODULE 13 — DASHBOARD

Endpoint tổng hợp 1 lần: `GET /trips/{tripRid}/dashboard` trả:
- **Countdown:** số ngày tới `START_DATE` (theo timezone trip).
- **Tổng ngân sách:** `SUM(BUDGETS.PLANNED_AMOUNT)`.
- **Đã chi:** tổng chi (cá nhân + quỹ, không trùng) theo Module 10.
- **Quỹ còn lại:** công thức số dư quỹ ở Module 10.
- **Event tiếp theo:** event gần nhất có `START_TIME >= now`.

---

### MODULE 14 — NOTIFICATION (FCM + Scheduler)

```text
SCHEDULED_NOTIFICATIONS
  TRIP_ID        NUMBER(19) FK nullable
  USER_ID        NUMBER(19) FK nullable
  TYPE           VARCHAR2(40)   PRE_TRIP_30D | PRE_TRIP_7D | PRE_TRIP_1D
                                | EVENT_REMINDER | HOTEL_CHECKIN | DEBT_REMINDER
  PAYLOAD        VARCHAR2(1000) (json: title, body, deeplink)
  SCHEDULED_AT   TIMESTAMP (UTC)
  SENT_AT        TIMESTAMP nullable
  STATUS         VARCHAR2(20)   PENDING | SENT | FAILED | CANCELLED
  idx: (STATUS, SCHEDULED_AT)
```

**Cơ chế:**
- Khi tạo/sửa trip & event → sinh các bản ghi `SCHEDULED_NOTIFICATIONS` tương ứng.
- Job `@Scheduled(fixedDelay)` mỗi phút quét `STATUS=PENDING AND SCHEDULED_AT <= now`, gửi FCM tới `USER_DEVICES`, set `SENT_AT/STATUS`. **Idempotent** để restart không gửi trùng.
- **Khi chạy >1 instance:** thêm **ShedLock** (nhẹ hơn Quartz cluster) để chỉ một instance chạy job mỗi chu kỳ, tránh gửi FCM trùng. Single instance thì chưa cần.

**Loại thông báo:**
- Trước chuyến: 30 / 7 / 1 ngày.
- Trong chuyến: event sau 30 phút, nhắc check-in khách sạn.
- Sau chuyến: nhắc công nợ (từ Settlement).

---

## 8. BÁO CÁO CUỐI CHUYẾN

`GET /trips/{tripRid}/report`:
- **Tổng quan:** Budget, Actual, Over/Under (= Actual − Budget).
- **Theo danh mục:** Budget vs Actual cho từng `Category` (so sánh được nhờ enum hợp nhất).
- **Chi phí phát sinh:** lọc `EXPENSE_TYPE = UNEXPECTED` (vd Medical/Parking/Repair).
- **Công nợ:** danh sách giao dịch tối thiểu từ Settlement Engine.

---

## 9. Lộ trình build (đề xuất milestones)

1. **M1 — Nền tảng:** project skeleton, BaseEntity, JPA auditing, soft delete, Flyway, exception handler, envelope response, **Testcontainers (Postgres) cho integration test** ngay từ đầu.
2. **M2 — Auth:** Email/Password + Google + JWT/refresh + verify/reset; `users/me`; FCM device registration.
3. **M3 — Trip & Members:** CRUD trip, members, ghost member, invitation link/QR, `@RequireTripRole`.
4. **M4 — Planning:** Places, Events (timeline), Transport, Accommodation, Checklist.
5. **M5 — Money:** MoneyService + ExchangeRate snapshot, Budget, Expense + Shares.
6. **M6 — Fund & Settlement:** Fund contributions/expenses, số dư, Settlement Engine.
7. **M7 — Dashboard & Report:** endpoint tổng hợp.
8. **M8 — Notification:** scheduled notifications + FCM sender job.
9. **M9 — Flutter app:** màn hình theo module, `flutter_map`, push.

---

## 10. Phase 2 (sau khi core ổn định)

AI Trip Planner · AI Budget Estimation · Google Calendar Sync · Export PDF · Offline Sync · Ảnh hóa đơn · OCR vé máy bay.

---

## 11. Chi phí vận hành

| Thành phần | Chi phí |
|---|---|
| Oracle VM (Always Free, ưu tiên Ampere A1) | 0 |
| Oracle Autonomous DB Free **hoặc** PostgreSQL | 0 |
| Firebase FCM | 0 |
| OpenStreetMap | 0 |
| SSL (Let's Encrypt) | 0 |
| Domain | ~10–15 USD/năm |

**Rủi ro cần biết:** Oracle Always Free VM có thể bị reclaim khi idle, và capacity Ampere A1 đôi khi khó request. Autonomous DB Free tự stop sau ~7 ngày không hoạt động (giới hạn 20GB) — cần job giữ kết nối hoặc cân nhắc PostgreSQL nếu ưu tiên đơn giản/ổn định.

---

## 12. Checklist convention khi code (nhắc Claude Code)

- [ ] Không expose `ID`; mọi API dùng `RID`.
- [ ] Tiền = `BigDecimal` / `NUMBER(19,4)`, không float.
- [ ] Mọi tính toán tiền chạy trên `AMOUNT_BASE` (BASE_CURRENCY).
- [ ] Thời gian lưu UTC, convert khi hiển thị.
- [ ] Mọi endpoint trip-scoped kiểm tra membership + role.
- [ ] Soft delete + partial unique cho cột nghiệp vụ.
- [ ] Index cho mọi FK.
- [ ] Số dư quỹ tính bằng aggregation, không lưu balance.
- [ ] Một khoản chi: hoặc quỹ, hoặc cá nhân — không cả hai.
- [ ] Dùng Flyway, không `ddl-auto=update` ở prod.
- [ ] `RID` dùng UUID v7; soft delete filter bằng `@SQLRestriction` (không `@Where`).
- [ ] Tỷ giá lấy từ provider free + cho phép override thủ công; luôn snapshot.
- [ ] Lỗi API theo RFC 7807; POST tạo tiền nhận `Idempotency-Key`.
- [ ] Auth: refresh token rotation + reuse detection; có rate limit / CORS / giới hạn body size.
- [ ] Truy cập trip-scoped qua `TripAccessGuard` tập trung, không rải logic trong từng service.
- [ ] Accept invitation bằng UPDATE atomic (không read-check-write).
- [ ] Scheduler dùng ShedLock nếu chạy đa instance.
- [ ] Có Testcontainers + test cho Settlement Engine (đặc biệt phần chia dư của `EQUAL`).

---

## Phụ lục A — Ghi chú review spec (quyết định cần chốt)

> Tổng hợp từ buổi review spec. Khi đã chốt, cập nhật vào thân spec và xóa mục tương ứng ở đây. Chi tiết ở `CLAUDE.md` mục "Open decisions".

1. **Soft-delete filter:** cân nhắc `@FilterDef`/`@Filter` (bật/tắt được) thay vì `@SQLRestriction` (global, không tắt được per-query) cho nhóm entity settlement/report — vì cần đọc cả bản ghi đã soft-delete (member rời nhóm vẫn còn công nợ). Chốt trước M5.
2. **Thiếu bảng `IDEMPOTENCY_KEYS`** trong Mục 6 — cần định nghĩa + quy tắc same-key/different-payload → 422.
3. **Luồng merge ghost → real user** chưa đặc tả (Module 3) — khi accept invitation trùng email phải re-point `PAYER_ID`/`EXPENSE_SHARES`/`FUND_CONTRIBUTIONS` rồi soft-delete ghost.
4. **Thiếu email provider** cho verify/reset (Mục 1, 11) — chọn relay free, chặn M2.
5. **Greedy settlement KHÔNG tối thiểu tuyệt đối** (min-transactions là NP-hard) — đổi cách diễn đạt thành "minimised"; chạy trên integer minor unit để tránh lệch làm tròn.
6. **Tách quỹ vs settlement cá nhân** có thể gây UX khó hiểu — cân nhắc thêm view "gộp" một con số cuối cùng.
7. **Reuse detection** với `AUTH_TOKENS` phẳng chỉ revoke được toàn bộ phiên — thêm `FAMILY_ID`/session chain nếu muốn revoke đúng chuỗi bị lộ.
8. **Làm tròn `AMOUNT_BASE`** chưa định nghĩa — chốt `HALF_UP`, scale 4, tập trung trong `MoneyService` + test.
9. **Oracle `TIMESTAMP` không mang tz** — set `hibernate.jdbc.time_zone=UTC`.
10. **UUID v7 sinh ở Java** (Oracle `SYS_GUID()` không phải v7; Java 21 chưa có v7 built-in).
