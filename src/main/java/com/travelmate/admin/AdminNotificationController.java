package com.travelmate.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmate.notification.ScheduledNotification;
import com.travelmate.user.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

/** Admin "Notifications": list the queue, compose + push one immediately, and cancel a pending one. */
@Controller
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private static final Set<String> SORTS = Set.of("scheduledAt", "status", "type", "sentAt");

    private final AdminNotificationService service;
    private final AdminService adminService;
    private final ObjectMapper objectMapper;

    public AdminNotificationController(AdminNotificationService service, AdminService adminService,
                                      ObjectMapper objectMapper) {
        this.service = service;
        this.adminService = adminService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                       @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        int rows = DataTables.clampSize(size);
        Page<NotifRow> data = service
                .list(q, DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "scheduledAt")))
                .map(this::toRow);
        common(principal, model);
        model.addAttribute("table", DataTables.view("/admin/notifications", q, sort, dir, rows, data));
        return "admin/notifications/list";
    }

    @GetMapping("/new")
    public String compose(Principal principal, Model model) {
        common(principal, model);
        return "admin/notifications/new";
    }

    @PostMapping("/send")
    public String send(@RequestParam String target, @RequestParam String identifier,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) String deeplink,
                       Principal principal, RedirectAttributes ra) {
        try {
            int devices = service.sendNow(currentAdminId(principal), target, identifier, title, body, deeplink);
            ra.addFlashAttribute("flash", "Pushed to " + devices + " device(s).");
            ra.addFlashAttribute("flashType", "ok");
        } catch (AdminActionException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            ra.addFlashAttribute("flashType", "error");
            return "redirect:/admin/notifications/new";
        }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/{rid}/cancel")
    public String cancel(@PathVariable String rid, Principal principal, RedirectAttributes ra) {
        try {
            service.cancel(currentAdminId(principal), rid);
            ra.addFlashAttribute("flash", "Notification cancelled.");
            ra.addFlashAttribute("flashType", "ok");
        } catch (AdminActionException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            ra.addFlashAttribute("flashType", "error");
        }
        return "redirect:/admin/notifications";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A flattened row for the table (parses the payload title; hides internal ids). */
    public record NotifRow(String rid, String type, String status, Instant scheduledAt,
                           Instant sentAt, String title, String target, boolean pending) {
    }

    private NotifRow toRow(ScheduledNotification n) {
        String title = "";
        try {
            title = objectMapper.readTree(n.getPayload() == null ? "{}" : n.getPayload())
                    .path("title").asText("");
        } catch (Exception ignored) {
            // leave blank
        }
        String target = n.getUserId() != null ? "User" : (n.getTripId() != null ? "Trip" : "—");
        return new NotifRow(n.getRid(), n.getType().name(), n.getStatus().name(),
                n.getScheduledAt(), n.getSentAt(), title, target,
                "PENDING".equals(n.getStatus().name()));
    }

    private void common(Principal principal, Model model) {
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("active", "notifications");
    }

    private Long currentAdminId(Principal principal) {
        return adminService.findAdminByEmail(principal.getName())
                .map(User::getId)
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }

    private String adminName(Principal principal) {
        if (principal == null) {
            return "";
        }
        return adminService.findAdminByEmail(principal.getName()).map(User::getName)
                .orElse(principal.getName());
    }
}
