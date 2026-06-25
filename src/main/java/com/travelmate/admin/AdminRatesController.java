package com.travelmate.admin;

import com.travelmate.common.money.ExchangeRateCache;
import com.travelmate.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Admin view of the in-memory exchange-rate table, with a manual refresh. */
@Controller
@RequestMapping("/admin/rates")
public class AdminRatesController {

    private final ExchangeRateCache rateCache;
    private final AdminService adminService;

    public AdminRatesController(ExchangeRateCache rateCache, AdminService adminService) {
        this.rateCache = rateCache;
        this.adminService = adminService;
    }

    @GetMapping
    public String view(Principal principal, Model model) {
        model.addAttribute("active", "rates");
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("snapshot", rateCache.current());
        return "admin/rates";
    }

    @PostMapping("/refresh")
    public String refresh(Principal principal, RedirectAttributes ra) {
        try {
            rateCache.refresh();
            adminService.audit(adminId(principal), "RATES_REFRESH", "RATES", null, null);
            ra.addFlashAttribute("flash", "Exchange rates refreshed.");
            ra.addFlashAttribute("flashType", "ok");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("flash", "Refresh failed: " + e.getMessage());
            ra.addFlashAttribute("flashType", "error");
        }
        return "redirect:/admin/rates";
    }

    private String adminName(Principal principal) {
        return principal == null ? "" :
                adminService.findAdminByEmail(principal.getName()).map(User::getName).orElse(principal.getName());
    }

    private Long adminId(Principal principal) {
        return adminService.findAdminByEmail(principal.getName()).map(User::getId)
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }
}
