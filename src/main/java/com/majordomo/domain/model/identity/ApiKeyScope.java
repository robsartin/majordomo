package com.majordomo.domain.model.identity;

/**
 * Permission scope for an API key. Constrains what an authenticated key may do:
 * a {@link #READ_ONLY} key may perform safe (read) requests only, while a
 * {@link #READ_WRITE} key may also perform state-changing requests.
 */
public enum ApiKeyScope {

    /** May perform safe/read requests only; state-changing requests are rejected. */
    READ_ONLY,

    /** May perform both read and state-changing requests. */
    READ_WRITE;

    /**
     * Whether this scope permits state-changing (write) requests.
     *
     * @return {@code true} for {@link #READ_WRITE}, {@code false} for {@link #READ_ONLY}
     */
    public boolean permitsWrites() {
        return this == READ_WRITE;
    }
}
