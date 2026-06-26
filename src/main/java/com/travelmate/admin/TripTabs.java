package com.travelmate.admin;

import com.travelmate.trip.Trip;
import com.travelmate.user.User;
import org.springframework.ui.Model;

import java.security.Principal;

/**
 * Shared setup for the trip detail page's tab strip (Overview/Members/Expenses/Tickets…): every tab
 * controller resolves the same {@code trip} + {@code activeTab} + signed-in admin name into the model
 * so the {@code admin/trips/tabs} fragment renders identically everywhere.
 */
final class TripTabs {

    private TripTabs() {
    }

    static void common(Model model, Principal principal, AdminService adminService, Trip trip, String activeTab) {
        model.addAttribute("active", "trips");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("t", trip);
        model.addAttribute("admin", principal == null ? "" :
                adminService.findAdminByEmail(principal.getName()).map(User::getName).orElse(principal.getName()));
    }
}
