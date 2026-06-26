package com.travelmate.admin;

import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseRepository;
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

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;

/** The Expenses tab on a trip's detail page: searchable/sortable list + soft-delete. */
@Controller
@RequestMapping("/admin/trips/{tripRid}/expenses")
public class AdminExpenseController {

    private static final Set<String> SORTS = Set.of("title", "amount", "spentAt", "category");

    private final ExpenseRepository expenseRepository;
    private final TripRepository tripRepository;
    private final AdminService adminService;

    public AdminExpenseController(ExpenseRepository expenseRepository, TripRepository tripRepository,
                                 AdminService adminService) {
        this.expenseRepository = expenseRepository;
        this.tripRepository = tripRepository;
        this.adminService = adminService;
    }

    /** A flattened row (hides internal ids). */
    public record ExpenseRow(String rid, String title, BigDecimal amount, String currency,
                             String category, String type, Instant spentAt) {
    }

    @GetMapping
    public String list(@PathVariable String tripRid, @RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                       @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "0") int page,
                       Principal principal, Model model) {
        Trip trip = tripRepository.findByRid(tripRid)
                .orElseThrow(() -> new AdminActionException("Trip not found."));
        int rows = DataTables.clampSize(size);
        Page<Expense> expenses = expenseRepository.searchByTrip(trip.getId(), q,
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "spentAt")));
        Page<ExpenseRow> data = expenses.map(e -> new ExpenseRow(e.getRid(), e.getTitle(),
                e.getAmount(), e.getCurrency(), e.getCategory().name(), e.getExpenseType().name(),
                e.getSpentAt()));
        TripTabs.common(model, principal, adminService, trip, "expenses");
        model.addAttribute("table", DataTables.view("/admin/trips/" + tripRid + "/expenses", q, sort, dir, rows, data));
        return "admin/trips/expenses";
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String tripRid, @PathVariable String rid, Principal principal,
                         RedirectAttributes ra) {
        Expense e = expenseRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Expense not found."));
        e.setDeleted(true);
        adminService.audit(adminId(principal), "EXPENSE_DELETE", "EXPENSE", rid, "title=" + e.getTitle());
        ra.addFlashAttribute("flash", "Expense deleted.");
        ra.addFlashAttribute("flashType", "ok");
        return "redirect:/admin/trips/" + tripRid + "/expenses";
    }

    private Long adminId(Principal principal) {
        return adminService.findAdminByEmail(principal.getName()).map(com.travelmate.user.User::getId)
                .orElseThrow(() -> new AdminActionException("Not signed in."));
    }
}
