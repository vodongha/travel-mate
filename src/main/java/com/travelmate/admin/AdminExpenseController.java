package com.travelmate.admin;

import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.trip.Trip;
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

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Admin view of expenses across all trips: searchable/sortable list + soft-delete. */
@Controller
@RequestMapping("/admin/expenses")
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

    /** A flattened row with the trip name resolved (hides internal ids). */
    public record ExpenseRow(String rid, String title, BigDecimal amount, String currency,
                             String category, String type, String trip, Instant spentAt) {
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir, @RequestParam(required = false) Integer size,
                       @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        int rows = DataTables.clampSize(size);
        Page<Expense> expenses = expenseRepository.search(q,
                DataTables.pageable(page, rows, DataTables.sort(sort, dir, SORTS, "spentAt")));
        Map<Long, String> tripNames = tripRepository
                .findAllById(expenses.getContent().stream().map(Expense::getTripId).toList())
                .stream().collect(Collectors.toMap(Trip::getId, Trip::getName));
        Page<ExpenseRow> data = expenses.map(e -> new ExpenseRow(e.getRid(), e.getTitle(),
                e.getAmount(), e.getCurrency(), e.getCategory().name(), e.getExpenseType().name(),
                tripNames.getOrDefault(e.getTripId(), "—"), e.getSpentAt()));
        model.addAttribute("active", "expenses");
        model.addAttribute("admin", adminName(principal));
        model.addAttribute("table", DataTables.view("/admin/expenses", q, sort, dir, rows, data));
        return "admin/expenses";
    }

    @PostMapping("/{rid}/delete")
    @Transactional
    public String delete(@PathVariable String rid, Principal principal, RedirectAttributes ra) {
        Expense e = expenseRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Expense not found."));
        e.setDeleted(true);
        adminService.audit(adminId(principal), "EXPENSE_DELETE", "EXPENSE", rid, "title=" + e.getTitle());
        ra.addFlashAttribute("flash", "Expense deleted.");
        ra.addFlashAttribute("flashType", "ok");
        return "redirect:/admin/expenses";
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
