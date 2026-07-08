package com.majordomo.adapter.in.web;

import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Handles the dashboard's inline "mark serviced" action. Verifies the caller may
 * access the schedule's organization, then delegates to the Herald use case,
 * which records the service and reschedules the next due date — no domain logic
 * lives here.
 */
@Controller
public class DashboardMaintenanceController {

    private final ManageScheduleUseCase scheduleUseCase;
    private final ScheduleAccessGuard accessGuard;

    /**
     * Constructs the controller.
     *
     * @param scheduleUseCase Herald schedule use case
     * @param accessGuard     authorization guard for schedule access
     */
    public DashboardMaintenanceController(ManageScheduleUseCase scheduleUseCase,
                                          ScheduleAccessGuard accessGuard) {
        this.scheduleUseCase = scheduleUseCase;
        this.accessGuard = accessGuard;
    }

    /**
     * Marks a maintenance schedule serviced as of today, then returns to the dashboard.
     *
     * @param scheduleId the schedule to complete
     * @return redirect to the dashboard
     */
    @PostMapping("/dashboard/maintenance/{scheduleId}/complete")
    public String complete(@PathVariable UUID scheduleId) {
        accessGuard.verifyForSchedule(scheduleId);
        scheduleUseCase.completeService(scheduleId, LocalDate.now());
        return "redirect:/dashboard";
    }
}
