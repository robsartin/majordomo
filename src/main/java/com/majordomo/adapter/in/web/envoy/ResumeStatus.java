package com.majordomo.adapter.in.web.envoy;

/**
 * Whether the user's résumé can be used as grounding text, and if not, why
 * (#349).
 *
 * @param ready  true when there is readable résumé text
 * @param reason the {@code ResumeNotAvailableException.Reason} name, or null
 *               when ready
 * @param detail what to do about it, or a confirmation when ready
 */
public record ResumeStatus(boolean ready, String reason, String detail) { }
