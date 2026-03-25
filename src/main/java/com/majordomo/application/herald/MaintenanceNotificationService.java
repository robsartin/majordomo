package com.majordomo.application.herald;

import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.NotificationCategory;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.NotificationPort;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Scheduled service that checks for upcoming maintenance and sends
 * email notifications to organization admins.
 */
@Service
public class MaintenanceNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceNotificationService.class);

    private final MaintenanceScheduleRepository scheduleRepository;
    private final PropertyRepository propertyRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final NotificationPort notificationPort;

    /**
     * Constructs the notification service with required dependencies.
     *
     * @param scheduleRepository   repository for maintenance schedules
     * @param propertyRepository   repository for properties
     * @param membershipRepository repository for organization memberships
     * @param userRepository       repository for users
     * @param preferencesRepository repository for user notification preferences
     * @param notificationPort     outbound port for sending notifications
     */
    public MaintenanceNotificationService(MaintenanceScheduleRepository scheduleRepository,
                                          PropertyRepository propertyRepository,
                                          MembershipRepository membershipRepository,
                                          UserRepository userRepository,
                                          UserPreferencesRepository preferencesRepository,
                                          NotificationPort notificationPort) {
        this.scheduleRepository = scheduleRepository;
        this.propertyRepository = propertyRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Runs on a configurable schedule to check for maintenance due within 7 days.
     * Sends email notifications and marks schedules as notified.
     */
    @Scheduled(cron = "${majordomo.notifications.cron:0 0 8 * * *}")
    public void checkAndNotify() {
        var dueSchedules = scheduleRepository.findDueBefore(LocalDate.now().plusDays(7));
        for (var schedule : dueSchedules) {
            if (schedule.getNotificationSentAt() != null) {
                continue;
            }

            var property = propertyRepository.findById(schedule.getPropertyId());
            if (property.isEmpty()) {
                continue;
            }

            var memberships = membershipRepository.findByOrganizationId(
                    property.get().getOrganizationId());
            for (var membership : memberships) {
                if (membership.getRole() == MemberRole.MEMBER) {
                    continue;
                }
                var user = userRepository.findById(membership.getUserId());
                user.ifPresent(u -> {
                    var prefs = preferencesRepository.findByUserId(u.getId());
                    if (prefs.isPresent()
                            && !prefs.get().isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE)) {
                        return;
                    }
                    notificationPort.send(
                            u.getEmail(),
                            "Upcoming maintenance: " + schedule.getDescription(),
                            "Maintenance for " + property.get().getName()
                                    + " is due on " + schedule.getNextDue());
                });
            }

            schedule.setNotificationSentAt(Instant.now());
            scheduleRepository.save(schedule);
        }
        LOG.info("Maintenance notification check complete");
    }
}
