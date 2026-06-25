package com.travelmate.admin.ops;

import com.travelmate.admin.AdminService;
import com.travelmate.admin.DataTables;
import com.travelmate.admin.ops.OpsService.DependabotAlert;
import com.travelmate.admin.ops.OpsService.LibStatus;
import com.travelmate.admin.ops.OpsService.OpsSnapshot;
import com.travelmate.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;

/**
 * "Ops &amp; libs" admin: an overview ({@code /admin/ops}) plus a server-side DataTable per list —
 * backend libs ({@code /maven}), app libs ({@code /pub}) and Dependabot alerts ({@code /alerts}).
 * Each table route filters/sorts/pages the in-memory snapshot via {@link DataTables}.
 */
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
        common(principal, model);
        model.addAttribute("snapshot", opsService.current());
        return "admin/ops";
    }

    @GetMapping("/maven")
    public String maven(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                        @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        common(principal, model);
        OpsSnapshot snap = opsService.current();
        libsModel(model, "/admin/ops/maven", "Backend dependencies (Maven Central)",
                snap == null ? List.of() : snap.maven(), q, sort, dir, size, page);
        return "admin/ops_libs";
    }

    @GetMapping("/pub")
    public String pub(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                      @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                      @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        common(principal, model);
        OpsSnapshot snap = opsService.current();
        libsModel(model, "/admin/ops/pub", "App dependencies (pub.dev)",
                snap == null ? List.of() : snap.pub(), q, sort, dir, size, page);
        return "admin/ops_libs";
    }

    @GetMapping("/alerts")
    public String alerts(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                         @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                         @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        common(principal, model);
        OpsSnapshot snap = opsService.current();
        List<DependabotAlert> all = snap == null ? List.of() : snap.alerts();
        String needle = q.trim().toLowerCase();
        List<DependabotAlert> filtered = all.stream()
                .filter(a -> needle.isEmpty()
                        || a.packageName().toLowerCase().contains(needle)
                        || a.summary().toLowerCase().contains(needle))
                .sorted(alertComparator(sort, dir))
                .toList();
        int rows = DataTables.clampSize(size);
        Page<DependabotAlert> pageObj =
                DataTables.slice(filtered, DataTables.pageable(page, rows, Sort.unsorted()));
        model.addAttribute("table", DataTables.view("/admin/ops/alerts", q, sort, dir, rows, pageObj));
        model.addAttribute("configured", snap != null && snap.dependabotConfigured());
        return "admin/ops_alerts";
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private void libsModel(Model model, String baseUrl, String title, List<LibStatus> all,
                           String q, String sort, String dir, Integer size, int page) {
        String needle = q.trim().toLowerCase();
        List<LibStatus> filtered = all.stream()
                .filter(l -> needle.isEmpty() || l.name().toLowerCase().contains(needle))
                .sorted(libComparator(sort, dir))
                .toList();
        int rows = DataTables.clampSize(size);
        Page<LibStatus> pageObj = DataTables.slice(filtered, DataTables.pageable(page, rows, Sort.unsorted()));
        model.addAttribute("table", DataTables.view(baseUrl, q, sort, dir, rows, pageObj));
        model.addAttribute("title", title);
    }

    private static Comparator<LibStatus> libComparator(String sort, String dir) {
        Comparator<LibStatus> base = switch (sort == null ? "" : sort) {
            case "current" -> Comparator.comparing(LibStatus::current, String.CASE_INSENSITIVE_ORDER);
            case "latest" -> Comparator.comparing(LibStatus::latest, String.CASE_INSENSITIVE_ORDER);
            case "outdated" -> Comparator.comparing(LibStatus::outdated);
            default -> Comparator.comparing(LibStatus::name, String.CASE_INSENSITIVE_ORDER);
        };
        return "desc".equalsIgnoreCase(dir) ? base.reversed() : base;
    }

    private static Comparator<DependabotAlert> alertComparator(String sort, String dir) {
        Comparator<DependabotAlert> base = switch (sort == null ? "" : sort) {
            case "repo" -> Comparator.comparing(DependabotAlert::repo, String.CASE_INSENSITIVE_ORDER);
            case "packageName" -> Comparator.comparing(DependabotAlert::packageName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(DependabotAlert::severity, String.CASE_INSENSITIVE_ORDER);
        };
        return "desc".equalsIgnoreCase(dir) ? base.reversed() : base;
    }

    private void common(Principal principal, Model model) {
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("active", "ops");
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
