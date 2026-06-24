package com.travelmate.admin.ops;

import com.travelmate.admin.AdminService;
import com.travelmate.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** "Ops &amp; libs" admin page: outdated-dependency report + Dependabot alerts (see {@link OpsService}). */
@Controller
@RequestMapping("/admin/ops")
public class OpsController {

    private final OpsService opsService;
    private final AdminService adminService;

    public OpsController(OpsService opsService, AdminService adminService) {
        this.opsService = opsService;
        this.adminService = adminService;
    }

    @GetMapping
    public String ops(Principal principal, Model model) {
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("active", "ops");
        model.addAttribute("snapshot", opsService.current());
        return "admin/ops";
    }

    @PostMapping("/refresh")
    public String refresh(RedirectAttributes ra) {
        try {
            opsService.refresh();
            ra.addFlashAttribute("flash", "Dependency report refreshed.");
            ra.addFlashAttribute("flashType", "ok");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("flash", "Refresh failed: " + e.getMessage());
            ra.addFlashAttribute("flashType", "error");
        }
        return "redirect:/admin/ops";
    }

    private String adminName(Principal principal) {
        if (principal == null) {
            return "";
        }
        return adminService.findAdminByEmail(principal.getName())
                .map(User::getName)
                .orElse(principal.getName());
    }
}
