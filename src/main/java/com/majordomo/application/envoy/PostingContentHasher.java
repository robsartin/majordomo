package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes a deterministic content fingerprint for a {@link JobPosting}. The hash
 * covers only the fields that influence scoring (company, title, location, raw text,
 * and extracted fields) — never identity or timestamp fields — so two postings with
 * identical content hash equal and any content change flips the hash.
 *
 * <p>Used by {@link JobScorerService} to detect that a posting is unchanged since a
 * prior score, allowing the previous {@code ScoreReport} to be reused instead of
 * re-invoking the LLM.</p>
 */
@Component
public class PostingContentHasher {

    /** Unit-separator control char, chosen so it cannot appear in field values. */
    private static final char FIELD_SEPARATOR = '\u001F';

    /**
     * Hashes a posting's scoring-relevant content.
     *
     * @param posting the posting to fingerprint
     * @return a lowercase hex SHA-256 digest of the posting's content
     */
    public String hash(JobPosting posting) {
        StringBuilder sb = new StringBuilder();
        append(sb, posting.getCompany());
        append(sb, posting.getTitle());
        append(sb, posting.getLocation());
        append(sb, posting.getRawText());
        Map<String, String> extracted = posting.getExtracted();
        if (extracted != null) {
            for (Map.Entry<String, String> e : new TreeMap<>(extracted).entrySet()) {
                append(sb, e.getKey());
                append(sb, e.getValue());
            }
        }
        return sha256Hex(sb.toString());
    }

    private static void append(StringBuilder sb, String value) {
        sb.append(value == null ? "" : value).append(FIELD_SEPARATOR);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
