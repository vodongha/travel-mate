package com.travelmate.admin;

import com.travelmate.trip.Trip;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.trip.TripRepository;
import com.travelmate.user.User;
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

/**
 * Admin view of trip members across all trips. Ghost members (no account yet) can be removed; a
 * member with a real account is left alone here — leaving/removing a real member is a trip-owner
 * action in the app, not an admin one, since it can shift money references.
 */
@Controller
@RequestMapping("/admin/members")
public class AdminMemberController {

    private static final Set<String> SORTS = Set.of("displayName", "role", "joinedAt");

    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;
    private final AdminService adminService;

    public AdminMemberController(TripMemberRepository tripMemberRepository, TripRepository tripRepository,
                                 AdminService adminService) {
        this.tripMemberRepository = tripMemberRepository;
        this.tripRepository = tripRepository;
        this.adminService = adminService;
    }

    /** A flattened row with the trip name resolved (hides internal ids). */
    public record MemberRow(String rid, String displayName, String email, String role, boolean ghost,
                            String trip, Instant joinedAt) {
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                       @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        int rows = DataTables.clampSize(size);
        Page<TripMember> members = tripMemberRepository.search(q,
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "joinedAt")));
        Map<Long, String> tripNames = tripRepository
                .findAllById(members.getContent().stream().map(TripMember::getTripId).toList())
                .stream().collect(Collectors.toMap(Trip::getId, Trip::getName));
        Page<MemberRow> data = members.map(m -> new MemberRow(m.getRid(), m.getDisplayName(),
                m.getEmail() == null ? "—" : m.getEmail(), m.getRole().name(), m.isGhost(),
                tripNames.getOrDefault(m.getTripId(), "—"), m.getJoinedAt()));
        model.addAttribute("active", "members");
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("table", DataTables.view("/admin/members", q, sort, dir, rows, data));
        return "admin/members";
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String rid, Principal principal, RedirectAttributes ra) {
        try {
            TripMember m = tripMemberRepository.findByRid(rid)
                    .orElseThrow(() -> new AdminActionException("Member not found."));
            if (!m.isGhost()) {
                throw new AdminActionException("Only a ghost member (no account) can be removed here.");
            }
            m.setDeleted(true);
            adminService.audit(adminId(principal), "MEMBER_DELETE", "TRIP_MEMBER", rid, "name=" + m.getDisplayName());
            ra.addFlashAttribute("flash", "Member removed.");
            ra.addFlashAttribute("flashType", "ok");
        } catch (AdminActionException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            ra.addFlashAttribute("flashType", "error");
        }
        return "redirect:/admin/members";
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
