package com.majordomo.domain.model.envoy;

/**
 * "X of Y APPLY_NOW postings applied" rollup. {@code total} is the count of
 * recent score reports recommending APPLY_NOW for an organization;
 * {@code applied} is the subset whose source posting has been marked applied.
 *
 * @param total   number of APPLY_NOW reports counted
 * @param applied number of those whose source posting has appliedAt set
 */
public record ApplyNowConversionStat(long total, long applied) {

    /** Empty stat used when an org has no APPLY_NOW reports yet. */
    public static final ApplyNowConversionStat EMPTY = new ApplyNowConversionStat(0L, 0L);
}
