package com.majordomo.adapter.in.web.envoy;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REST controller for rubric authoring. */
@RestController
@RequestMapping("/api/envoy/rubrics")
@Tag(name = "Envoy", description = "Rubric management")
public class RubricController {

    private final ManageRubricUseCase rubrics;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the controller.
     *
     * @param rubrics                   inbound port for rubric authoring
     * @param organizationAccessService enforces per-org access control
     */
    public RubricController(ManageRubricUseCase rubrics,
                            OrganizationAccessService organizationAccessService) {
        this.rubrics = rubrics;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Appends a new org-specific version of the named rubric.
     *
     * @param name           rubric name (path)
     * @param organizationId required org scope
     * @param rubric         submitted rubric body (id/version/effectiveFrom assigned by service)
     * @return the persisted rubric with its new id, version, and effectiveFrom
     */
    @PutMapping("/{name}")
    public Rubric putRubric(@PathVariable String name,
                            @RequestParam UUID organizationId,
                            @RequestBody Rubric rubric) {
        organizationAccessService.verifyAccess(organizationId);
        return rubrics.saveNewVersion(name, rubric, organizationId);
    }
}
