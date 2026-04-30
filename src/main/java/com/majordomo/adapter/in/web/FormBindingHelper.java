package com.majordomo.adapter.in.web;

import com.majordomo.domain.model.concierge.ContactRole;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Form-binding utilities shared by the page controllers.
 *
 * <p>Each helper here was previously duplicated across PropertyPageController,
 * ContactPageController, AuditPageController, and the link/unlink controllers.
 * Centralized per #246. Pure functions only — no Spring dependencies, no
 * mutable state.</p>
 *
 * <p>Note: the per-controller {@code populateFormState}/{@code echoFormState}
 * methods are intentionally NOT extracted; their field lists differ enough
 * across forms that a generic builder costs more abstraction than the few
 * extra lines of duplication.</p>
 */
public final class FormBindingHelper {

    private FormBindingHelper() { }

    /**
     * @param s any string
     * @return {@code null} when the input is null or blank, otherwise the input unchanged
     */
    public static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Splits a textarea-style multi-line value into trimmed, non-blank lines.
     *
     * @param raw newline-separated input (handles {@code \r}, {@code \n}, {@code \r\n})
     * @return ordered list of trimmed lines, possibly empty (never null)
     */
    public static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return raw.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Parses an optional UUID-string form parameter.
     *
     * @param s the form value (may be null/blank)
     * @return parsed UUID, or {@code null} when blank
     * @throws IllegalArgumentException if the input is non-blank but not a valid UUID
     */
    public static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return UUID.fromString(s.trim());
    }

    /**
     * Parses an optional decimal price-string form parameter.
     *
     * @param s the form value (may be null/blank)
     * @return parsed BigDecimal, or {@code null} when blank
     * @throws NumberFormatException if the input is non-blank but not a valid decimal
     */
    public static BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return new BigDecimal(s.trim());
    }

    /**
     * Parses a contact-role form value, falling back to {@link ContactRole#OTHER}
     * for blank or unrecognized input. Used by the property↔contact link forms
     * where a role dropdown is always offered but the underlying enum may evolve.
     *
     * @param role the form value (may be null/blank)
     * @return the matching {@link ContactRole}, or {@code OTHER} when unrecognized
     */
    public static ContactRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return ContactRole.OTHER;
        }
        try {
            return ContactRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ContactRole.OTHER;
        }
    }
}
