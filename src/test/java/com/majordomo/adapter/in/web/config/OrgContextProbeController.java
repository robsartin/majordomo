package com.majordomo.adapter.in.web.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Test-only controller that echoes the resolved {@link OrgContext}. Used
 * exclusively by {@link OrgContextArgumentResolverTest} to verify the
 * argument-resolver wiring without dragging in any production controller.
 */
@Controller
public class OrgContextProbeController {

    /**
     * Returns a plaintext rendering of the resolved {@link OrgContext}.
     *
     * @param orgContext resolved by {@link OrgContextArgumentResolver}
     * @return {@code "orgId=...,user=..."}
     */
    @GetMapping(value = "/__test__/org-probe", produces = "text/plain")
    @ResponseBody
    public String probe(OrgContext orgContext) {
        return "orgId=" + orgContext.organizationId()
                + ",user=" + orgContext.user().getUsername();
    }
}
