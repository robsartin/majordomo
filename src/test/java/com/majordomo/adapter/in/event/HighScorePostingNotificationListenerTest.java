package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.herald.NotificationPort;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HighScorePostingNotificationListener}.
 */
@ExtendWith(MockitoExtension.class)
class HighScorePostingNotificationListenerTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @Mock
    private NotificationPort notificationPort;

    private HighScorePostingNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new HighScorePostingNotificationListener(
                jobPostingRepository, membershipRepository, userRepository,
                preferencesRepository, notificationPort);
    }

    @Test
    void applyNowDispatchesNotification() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(orgId);
        posting.setCompany("Acme Inc");
        posting.setTitle("Staff Engineer");

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        var user = new User(userId, "owner", "owner@example.com");

        when(jobPostingRepository.findById(postingId, orgId))
                .thenReturn(Optional.of(posting));
        when(membershipRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(membership));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 92, Recommendation.APPLY_NOW, Instant.now()));

        verify(notificationPort).send(
                eq("owner@example.com"),
                contains("Acme Inc"),
                contains("APPLY_NOW"));
    }

    @Test
    void applyNowBodyIncludesScoreCompanyTitleAndDeeplink() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(orgId);
        posting.setCompany("Acme Inc");
        posting.setTitle("Staff Engineer");

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        var user = new User(userId, "owner", "owner@example.com");

        when(jobPostingRepository.findById(postingId, orgId))
                .thenReturn(Optional.of(posting));
        when(membershipRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(membership));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 92, Recommendation.APPLY_NOW, Instant.now()));

        verify(notificationPort).send(
                eq("owner@example.com"),
                anyString(),
                contains("Staff Engineer"));
        verify(notificationPort).send(
                eq("owner@example.com"),
                anyString(),
                contains("92/100"));
        verify(notificationPort).send(
                eq("owner@example.com"),
                anyString(),
                contains("/envoy/reports/" + reportId));
    }

    @Test
    void applyDoesNotDispatchNotification() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 80, Recommendation.APPLY, Instant.now()));

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void considerDoesNotDispatchNotification() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 60, Recommendation.CONSIDER, Instant.now()));

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void skipDoesNotDispatchNotification() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 30, Recommendation.SKIP, Instant.now()));

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void memberWithHighScorePostingDisabledIsSkipped() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID enabledUserId = UUID.randomUUID();
        UUID disabledUserId = UUID.randomUUID();

        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(orgId);
        posting.setCompany("Acme Inc");
        posting.setTitle("Staff Engineer");

        var enabledMembership = new Membership(
                UUID.randomUUID(), enabledUserId, orgId, MemberRole.OWNER);
        var disabledMembership = new Membership(
                UUID.randomUUID(), disabledUserId, orgId, MemberRole.OWNER);

        var enabledUser = new User(enabledUserId, "enabled", "enabled@example.com");
        var disabledUser = new User(disabledUserId, "disabled", "disabled@example.com");

        var disabledPrefs = new UserPreferences();
        disabledPrefs.setNotificationsEnabled(true);
        disabledPrefs.setNotificationCategoriesDisabled(List.of("HIGH_SCORE_POSTING"));

        when(jobPostingRepository.findById(postingId, orgId))
                .thenReturn(Optional.of(posting));
        when(membershipRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(enabledMembership, disabledMembership));
        when(userRepository.findById(enabledUserId))
                .thenReturn(Optional.of(enabledUser));
        when(userRepository.findById(disabledUserId))
                .thenReturn(Optional.of(disabledUser));
        when(preferencesRepository.findByUserId(enabledUserId))
                .thenReturn(Optional.empty());
        when(preferencesRepository.findByUserId(disabledUserId))
                .thenReturn(Optional.of(disabledPrefs));

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 92, Recommendation.APPLY_NOW, Instant.now()));

        verify(notificationPort).send(
                eq("enabled@example.com"), anyString(), anyString());
        verify(notificationPort, never()).send(
                eq("disabled@example.com"), anyString(), anyString());
    }

    @Test
    void postingNotFoundDoesNotDispatchNotification() {
        UUID reportId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();

        when(jobPostingRepository.findById(postingId, orgId))
                .thenReturn(Optional.empty());

        listener.onJobPostingScored(new JobPostingScored(
                reportId, orgId, postingId, 92, Recommendation.APPLY_NOW, Instant.now()));

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }
}
