package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.envoy.ResumeNotAvailableException;
import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.envoy.ResolveResumeUseCase;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

/**
 * Upload and status for the résumé that grounds generated materials (#349,
 * ADR-0028).
 *
 * <p>It is stored as an ordinary attachment on the {@code USER} record, so the
 * extraction sweep reads it like anything else. Nothing here parses a résumé.
 */
@RestController
@RequestMapping("/api/envoy/resume")
public class ResumeController {

    private final ManageAttachmentUseCase attachments;
    private final ResolveResumeUseCase resumes;

    /**
     * Constructs the controller.
     *
     * @param attachments attachment management
     * @param resumes     résumé resolution
     */
    public ResumeController(ManageAttachmentUseCase attachments, ResolveResumeUseCase resumes) {
        this.attachments = attachments;
        this.resumes = resumes;
    }

    /**
     * Uploads a résumé for the signed-in user, replacing nothing — the most
     * recent upload is the one used, so an older CV stays as history.
     *
     * @param orgContext the authenticated user
     * @param file       the résumé
     * @return {@code 201 Created} with the attachment metadata
     * @throws IOException if reading the upload fails
     */
    @PostMapping
    public ResponseEntity<Attachment> upload(
            OrgContext orgContext, @RequestParam("file") MultipartFile file)
            throws IOException {
        Attachment saved = attachments.upload(
                EntityType.USER.name(), orgContext.user().getId(),
                file.getOriginalFilename(), file.getContentType(),
                file.getSize(), file.getInputStream());
        return ResponseEntity
                .created(URI.create("/api/attachments/" + saved.getId()))
                .body(saved);
    }

    /**
     * Reports whether the résumé is usable yet.
     *
     * <p>Not being usable is a {@code 200} describing a state rather than an
     * error: asking whether the résumé is ready and being told "not yet, it is
     * still being read" is a successful answer to the question.
     *
     * @param orgContext the authenticated user
     * @return the status
     */
    @GetMapping
    public ResponseEntity<ResumeStatus> status(OrgContext orgContext) {
        try {
            resumes.resolve(orgContext.user().getId());
            return ResponseEntity.ok(new ResumeStatus(true, null, "Résumé read and ready to use."));
        } catch (ResumeNotAvailableException e) {
            return ResponseEntity.ok(
                    new ResumeStatus(false, e.reason().name(), e.getMessage()));
        }
    }
}
