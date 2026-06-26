package com.travelmate.admin;

import com.travelmate.trip.Trip;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.trip.TripRepository;
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
import java.util.Set;

/**
 * The Members tab on a trip's detail page. Ghost members (no account yet) can be removed; a member
 * with a real account is left alone here — leaving/removing a real member is a trip-owner action in
 * the app, not an admin one, since it can shift money references.
 */
@Controller
@RequestMapping("/admin/trips/{tripRid}/members")
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

    /** A flattened row (hides internal ids). */
    public record MemberRow(String rid, String displayName, String email, String role, boolean ghost,
                            Instant joinedAt) {
    }

    @GetMapping
    public String list(@PathVariable String tripRid, @RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                       @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "0") int page,
                       Principal principal, Model model) {
        Trip trip = tripRepository.findByRid(tripRid)
                .orElseThrow(() -> new AdminActionException("Trip not found."));
        int rows = DataTables.clampSize(size);
        Page<TripMember> members = tripMemberRepository.searchByTrip(trip.getId(), q,
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "joinedAt")));
        Page<MemberRow> data = members.map(m -> new MemberRow(m.getRid(), m.getDisplayName(),
                m.getEmail() == null ? "—" : m.getEmail(), m.getRole().name(), m.isGhost(), m.getJoinedAt()));
        TripTabs.common(model, principal, adminService, trip, "members");
        model.addAttribute("table", DataTables.view("/admin/trips/" + tripRid + "/members", q, sort, dir, rows, data));
        return "admin/trips/members";
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String tripRid, @PathVariable String rid, Principal principal,
                         RedirectAttributes ra) {
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
        return "redirect:/admin/trips/" + tripRid + "/members";
    }

    private Long adminId(Principal principal) {
        return adminService.findAdminByEmail(principal.getName()).map(com.travelmate.user.User::getId)
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }
}
