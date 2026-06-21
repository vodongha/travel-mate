package com.travelmate.legal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * Public, no-auth privacy policy served as a bilingual HTML page (SPEC §7 Module 1 / store policy).
 *
 * <p>Google Play requires a publicly reachable privacy-policy URL (for the store listing and the
 * Data Safety form) that is also accessible from inside the app. This route is that single source
 * of truth, rendered bilingually (vi/en) via a {@code ?lang=} query parameter. It is the only layer
 * (no service/repository) — the content + inline CSS live here as per-language section bundles.
 *
 * <p>The page is deliberately <b>framable</b> (we do not set {@code X-Frame-Options}) so the Flutter
 * app's in-app WebView and a web {@code <iframe>} can load it. Update {@link #EFFECTIVE_DATE} when
 * the policy text changes.
 */
@RestController
@RequestMapping("/api/v1")
public class LegalController {

    /** Bump this whenever the policy wording below changes. */
    static final String EFFECTIVE_DATE = "2026-06-21";

    private static final String APP_NAME = "TravelMate";
    private static final String DEVELOPER = "Võ Đông Hà";
    private static final String CONTACT_EMAIL = "vodongha@hotmail.com";
    private static final String WEBSITE = "https://vodongha.id.vn";

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @ResponseBody
    public String privacy(@RequestParam(name = "lang", defaultValue = "vi") String lang) {
        // Default to Vietnamese; only an explicit "en" switches language.
        return render("en".equals(lang) ? "en" : "vi");
    }

    // ── content ──────────────────────────────────────────────────────────────────

    /** A localized bundle: which switch link to show plus the title/intro/sections. */
    private record Page(String langLabel, String langSwitch, String title, String updated,
                        String intro, List<Section> sections) {
    }

    private record Section(String heading, String body) {
    }

    private static final Map<String, Page> CONTENT = Map.of(
            "vi", new Page(
                    "English", "?lang=en",
                    "Chính sách quyền riêng tư",
                    "Cập nhật lần cuối: " + EFFECTIVE_DATE,
                    "Chính sách này mô tả cách ứng dụng <strong>" + APP_NAME + "</strong> "
                            + "(do " + DEVELOPER + " phát triển) thu thập, sử dụng và bảo vệ thông tin "
                            + "của bạn. Bằng việc sử dụng ứng dụng, bạn đồng ý với chính sách này.",
                    List.of(
                            new Section("1. Thông tin chúng tôi thu thập",
                                    "<ul>"
                                            + "<li><strong>Thông tin tài khoản:</strong> email, tên hiển thị và "
                                            + "số điện thoại (tùy chọn) bạn cung cấp khi đăng ký hoặc cập nhật hồ sơ.</li>"
                                            + "<li><strong>Thông tin đăng nhập:</strong> mật khẩu được lưu dưới dạng "
                                            + "băm (hash), không bao giờ ở dạng văn bản thuần. Nếu đăng nhập bằng "
                                            + "Google, chúng tôi liên kết tài khoản theo email.</li>"
                                            + "<li><strong>Dữ liệu bạn nhập:</strong> chuyến đi, lịch trình, ngân sách, "
                                            + "chi tiêu, quỹ chung và ghi chú do bạn tạo trong ứng dụng.</li>"
                                            + "<li><strong>Thông tin nhóm:</strong> khi bạn tham gia một chuyến đi, các "
                                            + "thành viên trong chuyến đi thấy dữ liệu dùng chung của chuyến đi đó.</li>"
                                            + "</ul>"
                                            + "<p>Chúng tôi <strong>không</strong> thu thập vị trí, danh bạ, hay dữ liệu "
                                            + "quảng cáo, và không sử dụng trình theo dõi của bên thứ ba.</p>"),
                            new Section("2. Cách chúng tôi sử dụng thông tin",
                                    "<ul>"
                                            + "<li>Cung cấp chức năng cốt lõi: lập kế hoạch chuyến đi, ghi nhận chi tiêu, "
                                            + "quản lý quỹ chung và chia tiền trong nhóm.</li>"
                                            + "<li>Xác thực và bảo mật tài khoản của bạn.</li>"
                                            + "<li>Gửi thông báo nhắc lịch trình và công nợ (nếu bạn cho phép).</li>"
                                            + "<li>Vận hành, duy trì và cải thiện ứng dụng.</li>"
                                            + "</ul>"
                                            + "<p>Chúng tôi <strong>không bán</strong> dữ liệu cá nhân của bạn và không "
                                            + "dùng nó cho quảng cáo.</p>"),
                            new Section("3. Chia sẻ dữ liệu",
                                    "<p>Dữ liệu của bạn chỉ được chia sẻ trong các trường hợp sau:</p>"
                                            + "<ul>"
                                            + "<li><strong>Trong chuyến đi của bạn:</strong> dữ liệu dùng chung (lịch "
                                            + "trình, chi tiêu, quỹ, công nợ) hiển thị cho các thành viên cùng chuyến đi.</li>"
                                            + "<li><strong>Nhà cung cấp hạ tầng:</strong> dữ liệu được lưu trên dịch vụ "
                                            + "cơ sở dữ liệu đám mây (Oracle Cloud) và gửi thông báo qua Firebase Cloud "
                                            + "Messaging để vận hành ứng dụng.</li>"
                                            + "<li><strong>Yêu cầu pháp lý:</strong> khi pháp luật bắt buộc.</li>"
                                            + "</ul>"),
                            new Section("4. Lưu trữ và xóa dữ liệu",
                                    "<p>Bạn có thể <strong>xóa tài khoản ngay trong ứng dụng</strong> "
                                            + "(Hồ sơ → Xóa tài khoản). Khi xóa, tài khoản bị vô hiệu hóa ngay lập tức "
                                            + "(không thể đăng nhập) và các phiên đăng nhập bị thu hồi.</p>"
                                            + "<p>Bạn cũng có thể yêu cầu xóa dữ liệu qua email "
                                            + "<a href=\"mailto:" + CONTACT_EMAIL + "\">" + CONTACT_EMAIL + "</a>.</p>"),
                            new Section("5. Bảo mật",
                                    "<p>Kết nối tới máy chủ được mã hóa; mật khẩu được băm bằng bcrypt; "
                                            + "truy cập dữ liệu luôn giới hạn theo phạm vi chuyến đi của bạn. Tuy nhiên, "
                                            + "không có hệ thống nào an toàn tuyệt đối — hãy giữ kín mật khẩu của bạn.</p>"),
                            new Section("6. Trẻ em",
                                    "<p>Ứng dụng không hướng tới trẻ em dưới 13 tuổi và chúng tôi không cố ý "
                                            + "thu thập dữ liệu của trẻ em.</p>"),
                            new Section("7. Quyền của bạn",
                                    "<p>Bạn có quyền truy cập, chỉnh sửa và xóa dữ liệu của mình trực tiếp "
                                            + "trong ứng dụng, hoặc liên hệ với chúng tôi để được hỗ trợ.</p>"),
                            new Section("8. Thay đổi chính sách",
                                    "<p>Chúng tôi có thể cập nhật chính sách này; thay đổi sẽ được đăng tại "
                                            + "trang này kèm ngày cập nhật mới.</p>"),
                            new Section("9. Liên hệ",
                                    "<p>Mọi câu hỏi xin gửi tới <a href=\"mailto:" + CONTACT_EMAIL + "\">"
                                            + CONTACT_EMAIL + "</a> &middot; <a href=\"" + WEBSITE + "\">"
                                            + WEBSITE + "</a>.</p>"))),
            "en", new Page(
                    "Tiếng Việt", "?lang=vi",
                    "Privacy Policy",
                    "Last updated: " + EFFECTIVE_DATE,
                    "This policy explains how the <strong>" + APP_NAME + "</strong> app "
                            + "(developed by " + DEVELOPER + ") collects, uses, and protects your "
                            + "information. By using the app, you agree to this policy.",
                    List.of(
                            new Section("1. Information we collect",
                                    "<ul>"
                                            + "<li><strong>Account information:</strong> your email, display name, and "
                                            + "optional phone number, provided when you register or edit your profile.</li>"
                                            + "<li><strong>Sign-in information:</strong> your password is stored hashed, "
                                            + "never in plain text. If you sign in with Google, we link your account by "
                                            + "email.</li>"
                                            + "<li><strong>Data you enter:</strong> trips, itineraries, budgets, expenses, "
                                            + "shared funds, and notes you create in the app.</li>"
                                            + "<li><strong>Group information:</strong> when you join a trip, its members "
                                            + "can see that trip's shared data.</li>"
                                            + "</ul>"
                                            + "<p>We do <strong>not</strong> collect location, contacts, or advertising "
                                            + "data, and we use no third-party trackers.</p>"),
                            new Section("2. How we use information",
                                    "<ul>"
                                            + "<li>To provide core features: trip planning, expense tracking, shared-fund "
                                            + "management, and group settlement.</li>"
                                            + "<li>To authenticate and secure your account.</li>"
                                            + "<li>To send itinerary and debt reminders (if you opt in).</li>"
                                            + "<li>To operate, maintain, and improve the app.</li>"
                                            + "</ul>"
                                            + "<p>We do <strong>not sell</strong> your personal data and do not use it "
                                            + "for advertising.</p>"),
                            new Section("3. Data sharing",
                                    "<p>Your data is shared only in these cases:</p>"
                                            + "<ul>"
                                            + "<li><strong>Within your trip:</strong> shared data (itinerary, expenses, "
                                            + "fund, debts) is visible to members of the same trip.</li>"
                                            + "<li><strong>Infrastructure providers:</strong> data is stored on a cloud "
                                            + "database service (Oracle Cloud) and notifications are sent via Firebase "
                                            + "Cloud Messaging to run the app.</li>"
                                            + "<li><strong>Legal requirements:</strong> when required by law.</li>"
                                            + "</ul>"),
                            new Section("4. Data retention and deletion",
                                    "<p>You can <strong>delete your account from within the app</strong> "
                                            + "(Profile → Delete account). On deletion, the account is disabled immediately "
                                            + "(you can no longer sign in) and your sessions are revoked.</p>"
                                            + "<p>You may also request deletion by emailing "
                                            + "<a href=\"mailto:" + CONTACT_EMAIL + "\">" + CONTACT_EMAIL + "</a>.</p>"),
                            new Section("5. Security",
                                    "<p>Connections to the server are encrypted; passwords are hashed with "
                                            + "bcrypt; data access is always scoped to your trip. No system is perfectly "
                                            + "secure, however — please keep your password private.</p>"),
                            new Section("6. Children",
                                    "<p>The app is not directed to children under 13, and we do not knowingly "
                                            + "collect data from children.</p>"),
                            new Section("7. Your rights",
                                    "<p>You can access, edit, and delete your data directly in the app, or "
                                            + "contact us for help.</p>"),
                            new Section("8. Changes to this policy",
                                    "<p>We may update this policy; changes will be posted on this page with a "
                                            + "new last-updated date.</p>"),
                            new Section("9. Contact",
                                    "<p>Questions? Email <a href=\"mailto:" + CONTACT_EMAIL + "\">"
                                            + CONTACT_EMAIL + "</a> &middot; <a href=\"" + WEBSITE + "\">"
                                            + WEBSITE + "</a>.</p>"))));

    private static final String STYLE = """
            :root { color-scheme: light dark; }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
              line-height: 1.65;
              color: #1a1a2e;
              background: #f6f6fb;
            }
            @media (prefers-color-scheme: dark) {
              body { color: #e7e7ef; background: #14141b; }
              .card { background: #1e1e29 !important; box-shadow: none !important; }
              a { color: #9db4ff !important; }
              h2 { border-color: #2c2c3a !important; }
              .meta { color: #9a9aa8 !important; }
            }
            .wrap { max-width: 760px; margin: 0 auto; padding: 32px 20px 64px; }
            .card {
              background: #fff;
              border-radius: 18px;
              padding: 32px 28px;
              box-shadow: 0 2px 16px rgba(40, 40, 90, 0.08);
            }
            .top { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
            h1 { font-size: 1.7rem; margin: 0 0 4px; }
            h2 { font-size: 1.15rem; margin: 28px 0 8px; padding-bottom: 6px;
                 border-bottom: 1px solid #ececf3; }
            .meta { color: #6a6a78; font-size: 0.9rem; margin: 0 0 18px; }
            a { color: #4b56d2; text-decoration: none; }
            a:hover { text-decoration: underline; }
            ul { padding-left: 1.25rem; }
            li { margin: 4px 0; }
            .lang {
              font-size: 0.85rem; white-space: nowrap;
              border: 1px solid currentColor; border-radius: 999px;
              padding: 5px 12px; opacity: 0.8;
            }
            """;

    private static String render(String lang) {
        Page page = CONTENT.get(lang);
        StringBuilder sections = new StringBuilder();
        for (Section section : page.sections()) {
            sections.append("<h2>").append(section.heading()).append("</h2>").append(section.body());
        }
        return "<!DOCTYPE html>\n"
                + "<html lang=\"" + lang + "\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<meta name=\"robots\" content=\"index, follow\">\n"
                + "<title>" + page.title() + " — " + APP_NAME + "</title>\n"
                + "<style>" + STYLE + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"wrap\">\n"
                + "  <div class=\"card\">\n"
                + "    <div class=\"top\">\n"
                + "      <h1>" + page.title() + "</h1>\n"
                + "      <a class=\"lang\" href=\"" + page.langSwitch() + "\">" + page.langLabel() + "</a>\n"
                + "    </div>\n"
                + "    <p class=\"meta\">" + APP_NAME + " &middot; " + page.updated() + "</p>\n"
                + "    <p>" + page.intro() + "</p>\n"
                + "    " + sections + "\n"
                + "  </div>\n"
                + "</div>\n"
                + "</body>\n"
                + "</html>";
    }
}
