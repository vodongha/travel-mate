package com.travelmate.admin;

import com.travelmate.user.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

/** Server-rendered admin panel pages (Thymeleaf). The login POST/logout are handled by the
 * dedicated Spring Security chain in {@code SecurityConfig#adminFilterChain}. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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

    private String currentAdminName(Principal principal) {
        if (principal == null) {
            return "";
        }
        return adminService.findAdminByEmail(principal.getName())
                .map(User::getName)
                .orElse(principal.getName());
    }
}
