package com.travelmate.admin;

import com.travelmate.user.User;
import com.travelmate.user.UserDevice;
import com.travelmate.user.UserDeviceRepository;
import com.travelmate.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Admin view of registered push devices (FCM tokens), with the ability to revoke one. */
@Controller
@RequestMapping("/admin/devices")
public class AdminDeviceController {

    private static final Set<String> SORTS = Set.of("platform", "lastSeenAt", "locale");

    private final UserDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;

    public AdminDeviceController(UserDeviceRepository deviceRepository, UserRepository userRepository,
                                AdminService adminService) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.adminService = adminService;
    }

    /** A device row with the owner's email resolved (hides internal ids). */
    public record DeviceRow(String rid, String user, String platform, String locale, Instant lastSeenAt) {
    }

    @GetMapping
    public String list(@RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                       @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "0") int page,
                       Principal principal, Model model) {
        int rows = DataTables.clampSize(size);
        Page<UserDevice> devices = deviceRepository.findAll(
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "lastSeenAt")));
        Map<Long, String> emails = userRepository
                .findAllById(devices.getContent().stream().map(UserDevice::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getEmail));
        Page<DeviceRow> data = devices.map(d -> new DeviceRow(d.getRid(),
                emails.getOrDefault(d.getUserId(), "—"), d.getPlatform().name(),
                d.getLocale() == null ? "—" : d.getLocale(), d.getLastSeenAt()));
        model.addAttribute("active", "devices");
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("table", DataTables.view("/admin/devices", "", sort, dir, rows, data));
        return "admin/devices";
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String rid, Principal principal, RedirectAttributes ra) {
        UserDevice device = deviceRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Device not found."));
        device.setDeleted(true);
        adminService.audit(adminId(principal), "DEVICE_DELETE", "DEVICE", rid, null);
        ra.addFlashAttribute("flash", "Device removed.");
        ra.addFlashAttribute("flashType", "ok");
        return "redirect:/admin/devices";
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
