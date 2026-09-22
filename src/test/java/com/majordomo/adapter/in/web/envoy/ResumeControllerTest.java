package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.envoy.ResumeNotAvailableException;
import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.envoy.ResolveResumeUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Résumé upload and status (#349).
 */
@ExtendWith(MockitoExtension.class)
class ResumeControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ManageAttachmentUseCase attachments;

    @Mock
    private ResolveResumeUseCase resumes;

    private ResumeController controller;

    @BeforeEach
    void setUp() {
        controller = new ResumeController(attachments, resumes);
    }

    /**
     * The résumé must land on the USER record, not anywhere else — that is the
     * only place {@code ResumeSourceService} looks for it.
     */
    @Test
    void upload_attachesTheFileToTheUser() throws Exception {
        when(attachments.upload(anyString(), any(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new Attachment());

        controller.upload(orgContext(), file("cv.pdf", "application/pdf"));

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> owner = ArgumentCaptor.forClass(UUID.class);
        verify(attachments).upload(type.capture(), owner.capture(),
                anyString(), anyString(), anyLong(), any());
        assertThat(type.getValue()).isEqualTo(EntityType.USER.name());
        assertThat(owner.getValue()).isEqualTo(USER_ID);
    }

    @Test
    void status_reportsReadyWhenTheResumeHasText() {
        when(resumes.resolve(USER_ID)).thenReturn("Staff Engineer at Acme");

        ResponseEntity<ResumeStatus> response = controller.status(orgContext());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().ready()).isTrue();
    }

    /**
     * Not ready is a 200 describing a state, not an error. Asking "is my résumé
     * usable?" and being told no is a successful answer to the question.
     */
    @Test
    void status_reportsWhyItIsNotUsable() {
        when(resumes.resolve(USER_ID)).thenThrow(new ResumeNotAvailableException(
                ResumeNotAvailableException.Reason.NOT_YET_EXTRACTED));

        ResponseEntity<ResumeStatus> response = controller.status(orgContext());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().ready()).isFalse();
        assertThat(response.getBody().reason())
                .isEqualTo(ResumeNotAvailableException.Reason.NOT_YET_EXTRACTED.name());
        assertThat(response.getBody().detail()).contains("extraction runs every");
    }

    private static OrgContext orgContext() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("robsartin");
        return new OrgContext(user, UUID.randomUUID());
    }

    private static MockMultipartFile file(String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, "a resume".getBytes());
    }
}
