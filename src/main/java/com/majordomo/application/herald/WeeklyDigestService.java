package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.CalendarEvent;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.NotificationPort;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled service that sends each user a single weekly digest email rolling
 * up everything due in the next {@value #DIGEST_WINDOW_DAYS} days across their
 * organizations: upcoming maintenance and property warranty expirations.
 *
 * <p>Event assembly and per-user notification-category preference filtering are
 * delegated to {@link HeraldCalendarService} (reused from the calendar feed), so
 * a user who has opted out of a category simply does not see those entries, and
 * a user who has opted out of everything receives no digest at all. An empty
 * digest is never sent, and the send path is the Resilience4j-protected
 * {@link NotificationPort}.
 */
@Service
public class WeeklyDigestService {

    /** How far ahead the digest looks, in days. */
    static final int DIGEST_WINDOW_DAYS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(WeeklyDigestService.class);

    private final HeraldCalendarService calendarService;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final PropertyRepository propertyRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final NotificationPort notificationPort;

    /**
     * Constructs the digest service.
     *
     * @param calendarService      assembles per-user upcoming events with preference filtering
     * @param scheduleRepository   repository for maintenance schedules (organization discovery)
     * @param propertyRepository   repository for properties (organization discovery)
     * @param membershipRepository repository for organization memberships
     * @param userRepository       repository for users (recipient resolution)
     * @param notificationPort     outbound port for sending notifications
     */
    public WeeklyDigestService(HeraldCalendarService calendarService,
                               MaintenanceScheduleRepository scheduleRepository,
                               PropertyRepository propertyRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               NotificationPort notificationPort) {
        this.calendarService = calendarService;
        this.scheduleRepository = scheduleRepository;
        this.propertyRepository = propertyRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Assembles and sends the weekly digests. Runs on a configurable weekly cron
     * (default Monday 08:00). Each admin/owner receives one email covering all of
     * their organizations; users with nothing upcoming receive nothing.
     */
    @Scheduled(cron = "${majordomo.notifications.digest-cron:0 0 8 * * MON}")
    public void sendWeeklyDigests() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(DIGEST_WINDOW_DAYS);

        Map<UUID, List<CalendarEvent>> eventsByUser = new LinkedHashMap<>();
        for (UUID organizationId : organizationsWithUpcomingEvents(horizon)) {
            for (Membership membership : membershipRepository.findByOrganizationId(organizationId)) {
                if (membership.getRole() == MemberRole.MEMBER) {
                    continue;
                }
                List<CalendarEvent> events = calendarService
                        .upcomingEvents(membership.getUserId(), organizationId, today).stream()
                        .filter(event -> !event.date().isAfter(horizon))
                        .toList();
                if (!events.isEmpty()) {
                    eventsByUser.computeIfAbsent(membership.getUserId(), key -> new ArrayList<>())
                            .addAll(events);
                }
            }
        }

        int sent = 0;
        for (Map.Entry<UUID, List<CalendarEvent>> entry : eventsByUser.entrySet()) {
            List<CalendarEvent> events = entry.getValue();
            if (events.isEmpty()) {
                continue;
            }
            var user = userRepository.findById(entry.getKey());
            if (user.isEmpty()) {
                continue;
            }
            notificationPort.send(user.get().getEmail(), subject(events), body(events));
            sent++;
        }
        LOG.info("Weekly maintenance digest complete: {} digest(s) sent", sent);
    }

    private Set<UUID> organizationsWithUpcomingEvents(LocalDate horizon) {
        Set<UUID> organizationIds = new LinkedHashSet<>();
        LocalDate exclusiveUpper = horizon.plusDays(1);
        for (var schedule : scheduleRepository.findDueBefore(exclusiveUpper)) {
            propertyRepository.findById(schedule.getPropertyId())
                    .ifPresent(property -> organizationIds.add(property.getOrganizationId()));
        }
        for (var property : propertyRepository.findWithWarrantyExpiringBefore(exclusiveUpper)) {
            organizationIds.add(property.getOrganizationId());
        }
        return organizationIds;
    }

    private String subject(List<CalendarEvent> events) {
        return "Your weekly maintenance digest: " + events.size() + " item(s) coming up";
    }

    private String body(List<CalendarEvent> events) {
        var lines = new StringBuilder();
        lines.append("Here's what's coming up in the next ")
                .append(DIGEST_WINDOW_DAYS)
                .append(" days:\n\n");
        events.stream()
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .forEach(event -> lines.append(event.date())
                        .append("  ")
                        .append(event.summary())
                        .append(" (")
                        .append(event.description())
                        .append(")\n"));
        return lines.toString();
    }
}
