package com.travelmate.admin;

import com.travelmate.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Server-rendered admin panel pages (Thymeleaf). The login POST/logout are handled by the
 * dedicated Spring Security chain in {@code SecurityConfig#adminFilterChain}. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int PAGE_SIZE = 20;

    private final AdminService adminService;
    private final AdminUserService adminUserService;

    public AdminController(AdminService adminService, AdminUserService adminUserService) {
        this.adminService = adminService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/login")
    public String login() {
        // If already authenticated, skip the form.
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/admin";
        }
        return "admin/login";
    }

    @GetMapping({"", "/"})
    public String dashboard(Principal principal, Model model) {
        model.addAttribute("admin", currentAdminName(principal));
        model.addAttribute("counts", adminService.dashboardCounts());
        model.addAttribute("active", "dashboard");
        return "admin/dashboard";
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public String users(@RequestParam(required = false, defaultValue = "") String q,
                        @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        Page<User> users = adminUserService.list(q,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("id").descending()));
        model.addAttribute("admin", currentAdminName(principal));
        model.addAttribute("active", "users");
        model.addAttribute("users", users);
        model.addAttribute("q", q);
        return "admin/users/list";
    }

    @GetMapping("/users/{rid}")
    public String userDetail(@PathVariable String rid, Principal principal, Model model) {
        model.addAttribute("admin", currentAdminName(principal));
        model.addAttribute("active", "users");
        model.addAttribute("u", adminUserService.get(rid));
        model.addAttribute("self", currentAdmin(principal).getId());
        return "admin/users/detail";
    }

    @PostMapping("/users/{rid}/edit")
    public String editUser(@PathVariable String rid, @RequestParam String name,
                           @RequestParam String email, Principal principal, RedirectAttributes ra) {
        return run(ra, "/admin/users/" + rid, "Profile updated.",
                () -> adminUserService.updateProfile(currentAdmin(principal), rid, name, email));
    }

    @PostMapping("/users/{rid}/reset-password")
    public String resetPassword(@PathVariable String rid, @RequestParam String password,
                                Principal principal, RedirectAttributes ra) {
        return run(ra, "/admin/users/" + rid, "Password reset; the user's sessions were revoked.",
                () -> adminUserService.resetPassword(currentAdmin(principal), rid, password));
    }

    @PostMapping("/users/{rid}/admin")
    public String setAdmin(@PathVariable String rid, @RequestParam boolean grant,
                           Principal principal, RedirectAttributes ra) {
        return run(ra, "/admin/users/" + rid, grant ? "Admin access granted." : "Admin access revoked.",
                () -> adminUserService.setAdmin(currentAdmin(principal), rid, grant));
    }

    @PostMapping("/users/{rid}/disabled")
    public String setDisabled(@PathVariable String rid, @RequestParam boolean disabled,
                              Principal principal, RedirectAttributes ra) {
        return run(ra, "/admin/users/" + rid, disabled ? "Account disabled." : "Account restored.",
                () -> adminUserService.setDisabled(currentAdmin(principal), rid, disabled));
    }

    // ── Audit log ──────────────────────────────────────────────────────────────

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        Page<AdminAuditLog> entries =
                adminService.auditLog(PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        List<Long> actorIds = entries.getContent().stream()
                .map(AdminAuditLog::getActorUserId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> actors = adminService.actorLabels(actorIds);
        model.addAttribute("admin", currentAdminName(principal));
        model.addAttribute("active", "audit");
        model.addAttribute("entries", entries);
        model.addAttribute("actors", actors);
        return "admin/audit";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Run an admin action, turning success/failure into a flash message + redirect. */
    private String run(RedirectAttributes ra, String redirect, String okMessage, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("flash", okMessage);
            ra.addFlashAttribute("flashType", "ok");
        } catch (AdminActionException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            ra.addFlashAttribute("flashType", "error");
        }
        return "redirect:" + redirect;
    }

    private User currentAdmin(Principal principal) {
        return adminService.findAdminByEmail(principal.getName())
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }

    private String currentAdminName(Principal principal) {
        if (principal == null) {
            return "";
        }
        return adminService.findAdminByEmail(principal.getName())
                .map(User::getName)
                .orElse(principal.getName());
    }
}
