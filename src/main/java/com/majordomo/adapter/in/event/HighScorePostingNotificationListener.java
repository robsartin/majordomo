package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.model.identity.NotificationCategory;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.herald.NotificationPort;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link JobPostingScored} events and dispatches a notification to
 * each member of the owning organization when the recommendation is
 * {@link Recommendation#APPLY_NOW}. Mirrors the maintenance/warranty
 * notification pattern: looks up the org's members, honors each member's
 * {@link NotificationCategory#HIGH_SCORE_POSTING} opt-out via
 * {@code UserPreferences}, and dispatches via the existing
 * {@link NotificationPort}.
 */
@Component
public class HighScorePostingNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(
            HighScorePostingNotificationListener.class);

    private final JobPostingRepository jobPostingRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final NotificationPort notificationPort;

    /**
     * Constructs the listener with required dependencies.
     *
     * @param jobPostingRepository  outbound port for job postings
     * @param membershipRepository  outbound port for organization memberships
     * @param userRepository        outbound port for users
     * @param preferencesRepository outbound port for user notification preferences
     * @param notificationPort      outbound port for sending notifications
     */
    public HighScorePostingNotificationListener(JobPostingRepository jobPostingRepository,
                                                MembershipRepository membershipRepository,
                                                UserRepository userRepository,
                                                UserPreferencesRepository preferencesRepository,
                                                NotificationPort notificationPort) {
        this.jobPostingRepository = jobPostingRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Dispatches a notification for {@code APPLY_NOW} score events to each
     * member of the org who has not disabled the {@code HIGH_SCORE_POSTING}
     * category. Other recommendations are ignored.
     *
     * @param event the score event
     */
    @EventListener
    public void onJobPostingScored(JobPostingScored event) {
        if (event.recommendation() != Recommendation.APPLY_NOW) {
            return;
        }

        var posting = jobPostingRepository.findById(event.postingId(), event.organizationId());
        if (posting.isEmpty()) {
            LOG.warn("APPLY_NOW event for unknown posting {} in org {}",
                    event.postingId(), event.organizationId());
            return;
        }

        String company = posting.get().getCompany();
        String title = posting.get().getTitle();
        String subject = "High-scoring posting: " + company + " - " + title;
        String body = company + " - " + title
                + " scored " + event.finalScore() + "/100 - APPLY_NOW - "
                + "/envoy/reports/" + event.reportId();

        var memberships = membershipRepository.findByOrganizationId(event.organizationId());
        for (var membership : memberships) {
            var user = userRepository.findById(membership.getUserId());
            user.ifPresent(u -> {
                var prefs = preferencesRepository.findByUserId(u.getId());
                if (prefs.isPresent()
                        && !prefs.get().isCategoryEnabled(NotificationCategory.HIGH_SCORE_POSTING)) {
                    return;
                }
                notificationPort.send(u.getEmail(), subject, body);
            });
        }
        LOG.info("APPLY_NOW notification dispatched for posting {} (report {})",
                event.postingId(), event.reportId());
    }
}
