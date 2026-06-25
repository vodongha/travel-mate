package com.travelmate.admin;

import com.travelmate.admin.ops.OpsController;
import com.travelmate.admin.ops.OpsService;
import com.travelmate.admin.ops.OpsService.OpsSnapshot;
import com.travelmate.common.money.ExchangeRateCache;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.trip.TripRepository;
import com.travelmate.user.UserDeviceRepository;
import com.travelmate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every admin page through the real Thymeleaf engine (standalone MockMvc, mocked services)
 * so a broken expression, fragment reference or {@code @{...}} link fails the build — the
 * integration tests boot the context but never render a view. No security/filters are involved.
 */
class AdminTemplatesRenderTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AdminService adminService = mock(AdminService.class);
        AdminUserService adminUserService = mock(AdminUserService.class);
        OpsService opsService = mock(OpsService.class);
        AdminNotificationService notificationService = mock(AdminNotificationService.class);

        when(adminService.dashboardCounts())
                .thenReturn(Map.of("users", 0L, "admins", 0L, "trips", 0L, "expenses", 0L));
        when(adminUserService.list(any(), any())).thenReturn(Page.empty());
        when(adminService.auditLog(any(), any())).thenReturn(Page.empty());
        when(adminService.actorLabels(any())).thenReturn(Map.of());
        when(opsService.current())
                .thenReturn(new OpsSnapshot(Instant.now(), List.of(), List.of(), List.of(), false, List.of()));
        when(notificationService.list(any(), any())).thenReturn(Page.empty());

        TripRepository tripRepository = mock(TripRepository.class);
        TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
        ExchangeRateCache rateCache = mock(ExchangeRateCache.class);
        UserDeviceRepository deviceRepository = mock(UserDeviceRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        when(tripRepository.search(any(), any())).thenReturn(Page.empty());
        Trip trip = new Trip();
        trip.setName("Kyoto");
        trip.setBaseCurrency("VND");
        when(tripRepository.findByRid(any())).thenReturn(java.util.Optional.of(trip));
        when(tripMemberRepository.findByTripId(any())).thenReturn(List.of());
        when(rateCache.current())
                .thenReturn(new ExchangeRateCache.Snapshot("VND", Instant.now(), List.of()));
        when(deviceRepository.search(any(), any())).thenReturn(Page.empty());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        mvc = MockMvcBuilders
                .standaloneSetup(new AdminController(adminService, adminUserService),
                        new OpsController(opsService, adminService),
                        new AdminNotificationController(notificationService, adminService,
                                new com.fasterxml.jackson.databind.ObjectMapper()),
                        new AdminTripController(tripRepository, tripMemberRepository, adminService),
                        new AdminRatesController(rateCache, adminService),
                        new AdminDeviceController(deviceRepository, userRepository, adminService))
                .setViewResolvers(thymeleafViewResolver())
                .build();
    }

    private void renders(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void allAdminPagesRender() throws Exception {
        renders("/admin");
        renders("/admin/login");
        renders("/admin/users");
        renders("/admin/users?q=foo&sort=email&dir=asc&size=50&page=0");
        renders("/admin/audit");
        renders("/admin/audit?sort=action&dir=desc&size=10");
        renders("/admin/ops");
        renders("/admin/ops/maven?sort=outdated&dir=desc");
        renders("/admin/ops/pub");
        renders("/admin/ops/alerts?q=cve");
        renders("/admin/notifications");
        renders("/admin/notifications?sort=status&dir=asc&size=10");
        renders("/admin/notifications/new");
        renders("/admin/trips");
        renders("/admin/trips?q=kyoto&sort=name&dir=asc");
        renders("/admin/trips/some-rid");
        renders("/admin/rates");
        renders("/admin/devices");
        renders("/admin/devices?sort=platform&dir=asc&size=50");
    }

    private static ThymeleafViewResolver thymeleafViewResolver() {
        GenericApplicationContext appContext = new GenericApplicationContext();
        appContext.refresh();

        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(appContext);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");
        return viewResolver;
    }
}
