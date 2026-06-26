package com.travelmate.admin;

import com.travelmate.ticket.Ticket;
import com.travelmate.ticket.TicketRepository;
import com.travelmate.trip.Trip;
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
import java.util.Set;

/** The Tickets tab on a trip's detail page: searchable/sortable list + soft-delete. */
@Controller
@RequestMapping("/admin/trips/{tripRid}/tickets")
public class AdminTicketController {

    private static final Set<String> SORTS = Set.of("title", "ticketType", "provider");

    private final TicketRepository ticketRepository;
    private final TripRepository tripRepository;
    private final AdminService adminService;

    public AdminTicketController(TicketRepository ticketRepository, TripRepository tripRepository,
                                 AdminService adminService) {
        this.ticketRepository = ticketRepository;
        this.tripRepository = tripRepository;
        this.adminService = adminService;
    }

    /** A flattened row (hides internal ids). */
    public record TicketRow(String rid, String title, String ticketType, String provider, String seat,
                            String bookingCode, boolean linked) {
    }

    @GetMapping
    public String list(@PathVariable String tripRid, @RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                       @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "0") int page,
                       Principal principal, Model model) {
        Trip trip = tripRepository.findByRid(tripRid)
                .orElseThrow(() -> new AdminActionException("Trip not found."));
        int rows = DataTables.clampSize(size);
        Page<Ticket> tickets = ticketRepository.searchByTrip(trip.getId(), q,
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "title")));
        Page<TicketRow> data = tickets.map(t -> new TicketRow(t.getRid(), t.getTitle(),
                t.getTicketType().name(), t.getProvider() == null ? "—" : t.getProvider(),
                t.getSeat() == null ? "—" : t.getSeat(), t.getBookingCode() == null ? "—" : t.getBookingCode(),
                t.getItineraryKind() != null));
        TripTabs.common(model, principal, adminService, trip, "tickets");
        model.addAttribute("table", DataTables.view("/admin/trips/" + tripRid + "/tickets", q, sort, dir, rows, data));
        return "admin/trips/tickets";
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String tripRid, @PathVariable String rid, Principal principal,
                         RedirectAttributes ra) {
        Ticket t = ticketRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Ticket not found."));
        t.setDeleted(true);
        adminService.audit(adminId(principal), "TICKET_DELETE", "TICKET", rid, "title=" + t.getTitle());
        ra.addFlashAttribute("flash", "Ticket deleted.");
        ra.addFlashAttribute("flashType", "ok");
        return "redirect:/admin/trips/" + tripRid + "/tickets";
    }

    private Long adminId(Principal principal) {
        return adminService.findAdminByEmail(principal.getName()).map(com.travelmate.user.User::getId)
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }
}
