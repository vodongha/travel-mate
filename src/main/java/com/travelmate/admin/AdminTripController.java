package com.travelmate.admin;

import com.travelmate.trip.Trip;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.trip.TripRepository;
import com.travelmate.trip.TripType;
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
import java.util.Set;

/** Admin management of trips: searchable/sortable list, detail, and soft-delete. */
@Controller
@RequestMapping("/admin/trips")
public class AdminTripController {

    private static final Set<String> SORTS = Set.of("name", "startDate", "createdAt");

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final AdminService adminService;

    public AdminTripController(TripRepository tripRepository, TripMemberRepository tripMemberRepository,
                              AdminService adminService) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.adminService = adminService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                       @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        int rows = DataTables.clampSize(size);
        Page<Trip> trips = tripRepository.search(q,
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "createdAt")));
        common(principal, model);
        model.addAttribute("table", DataTables.view("/admin/trips", q, sort, dir, rows, trips));
        return "admin/trips/list";
    }

    @GetMapping("/{rid}")
    public String detail(@PathVariable String rid, Principal principal, Model model) {
        Trip trip = tripRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Trip not found."));
        TripTabs.common(model, principal, adminService, trip, "overview");
        model.addAttribute("memberCount", tripMemberRepository.findByTripId(trip.getId()).size());
        return "admin/trips/detail";
    }

    @PostMapping("/{rid}/edit")
    @Transactional
    public String edit(@PathVariable String rid, @RequestParam String name,
                       @RequestParam(required = false) String destination,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(required = false) String tripType,
                       Principal principal, RedirectAttributes ra) {
        Trip trip = tripRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Trip not found."));
        try {
            if (name != null && !name.isBlank()) {
                trip.setName(name.trim());
            }
            trip.setDestination(blankToNull(destination));
            trip.setStartDate(parseDate(startDate));
            trip.setEndDate(parseDate(endDate));
            if (tripType != null && !tripType.isBlank()) {
                trip.setTripType(TripType.valueOf(tripType));
            }
            adminService.audit(adminId(principal), "TRIP_EDIT", "TRIP", rid, "name=" + trip.getName());
            ra.addFlashAttribute("flash", "Trip updated.");
            ra.addFlashAttribute("flashType", "ok");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("flash", "Invalid input: " + e.getMessage());
            ra.addFlashAttribute("flashType", "error");
        }
        return "redirect:/admin/trips/" + rid;
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String rid, Principal principal, RedirectAttributes ra) {
        Trip trip = tripRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Trip not found."));
        trip.setDeleted(true);
        adminService.audit(adminId(principal), "TRIP_DELETE", "TRIP", rid, "name=" + trip.getName());
        ra.addFlashAttribute("flash", "Trip deleted.");
        ra.addFlashAttribute("flashType", "ok");
        return "redirect:/admin/trips";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static java.time.LocalDate parseDate(String s) {
        return s == null || s.isBlank() ? null : java.time.LocalDate.parse(s.trim());
    }

    private void common(Principal principal, Model model) {
        model.addAttribute("active", "trips");
        model.addAttribute("admin", principal == null ? "" :
                adminService.findAdminByEmail(principal.getName()).map(User::getName).orElse(principal.getName()));
    }

    private Long adminId(Principal principal) {
        return adminService.findAdminByEmail(principal.getName()).map(User::getId)
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }
}
