package com.majordomo.application.steward;

import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.NotificationCategory;
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
 * Scheduled service that checks for properties with warranties expiring within
 * 30 days and sends email notifications to organization admins and owners.
 */
@Service
public class WarrantyAlertService {

    private static final Logger LOG = LoggerFactory.getLogger(WarrantyAlertService.class);

    private final PropertyRepository propertyRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final NotificationPort notificationPort;

    /**
     * Constructs the warranty alert service with required dependencies.
     *
     * @param propertyRepository    repository for properties
     * @param membershipRepository  repository for organization memberships
     * @param userRepository        repository for users
     * @param preferencesRepository repository for user notification preferences
     * @param notificationPort      outbound port for sending notifications
     */
    public WarrantyAlertService(PropertyRepository propertyRepository,
                                MembershipRepository membershipRepository,
                                UserRepository userRepository,
                                UserPreferencesRepository preferencesRepository,
                                NotificationPort notificationPort) {
        this.propertyRepository = propertyRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Runs on a configurable schedule to check for warranties expiring within 30 days.
     * Sends email notifications to organization admins and owners, then marks
     * each property so it is not notified again.
     */
    @Scheduled(cron = "${majordomo.notifications.warranty-cron:0 0 9 * * *}")
    public void checkAndNotify() {
        var expiringProperties = propertyRepository.findWithWarrantyExpiringBefore(
                LocalDate.now().plusDays(30));
        for (var property : expiringProperties) {
            var memberships = membershipRepository.findByOrganizationId(
                    property.getOrganizationId());
            for (var membership : memberships) {
                if (membership.getRole() == MemberRole.MEMBER) {
                    continue;
                }
                var user = userRepository.findById(membership.getUserId());
                user.ifPresent(u -> {
                    var prefs = preferencesRepository.findByUserId(u.getId());
                    if (prefs.isPresent()
                            && !prefs.get().isCategoryEnabled(NotificationCategory.WARRANTY_EXPIRING)) {
                        return;
                    }
                    notificationPort.send(
                            u.getEmail(),
                            "Warranty expiring soon: " + property.getName(),
                            "The warranty for " + property.getName()
                                    + " expires on " + property.getWarrantyExpiresOn()
                                    + ". Please take action if renewal or replacement is needed.");
                });
            }

            property.setWarrantyNotificationSentAt(Instant.now());
            propertyRepository.save(property);
        }
        LOG.info("Warranty alert check complete");
    }
}
