package com.travelmate.admin;

import com.travelmate.expense.ExpenseRepository;
import com.travelmate.trip.TripRepository;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/** Backing logic for the {@code /admin} panel: dashboard counters, the current admin, and audit. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final ExpenseRepository expenseRepository;
    private final AdminAuditLogRepository auditRepository;

    public AdminService(UserRepository userRepository, TripRepository tripRepository,
                        ExpenseRepository expenseRepository,
                        AdminAuditLogRepository auditRepository) {
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
        this.auditRepository = auditRepository;
    }

    /** The signed-in admin by email (only active super-admins authenticate, so this is one of them). */
    @Transactional(readOnly = true)
    public Optional<User> findAdminByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase()).filter(User::isSuperadmin);
    }

    /** Top-line counters for the dashboard (soft-deleted rows are excluded by the entity filters). */
    @Transactional(readOnly = true)
    public Map<String, Long> dashboardCounts() {
        return Map.of(
                "users", userRepository.count(),
                "admins", userRepository.countBySuperadminTrue(),
                "trips", tripRepository.count(),
                "expenses", expenseRepository.count());
    }

    /** Record a privileged mutation. Call this from every admin write. */
    @Transactional
    public void audit(Long actorUserId, String action, String targetType, String targetRid,
                      String detail) {
        auditRepository.save(new AdminAuditLog(actorUserId, action, targetType, targetRid, detail));
    }
}
