package com.majordomo.adapter.in.web.envoy;

import org.springframework.util.AutoPopulatingList;

import java.util.List;

/**
 * Mutable form-binding DTO for the rubric author UI.
 *
 * <p>Records make poor form-backing objects for Spring's
 * {@code WebDataBinder} because they have no public no-arg constructor and no
 * setters — and the form needs indexed binding like
 * {@code categories[0].tiers[1].points} which requires
 * {@code List#get(int)} returning a mutable instance to populate. So the
 * author flow uses these plain classes for binding and converts to the
 * immutable domain {@link com.majordomo.domain.model.envoy.Rubric} only on a
 * successful submit.</p>
 *
 * <p>The list fields use Spring's {@link AutoPopulatingList} so that
 * indexed binding paths like {@code categories[3].key} grow the list on
 * demand rather than throwing {@code IndexOutOfBoundsException}.</p>
 */
public class RubricFormDto {

    private List<CategoryForm> categories = new AutoPopulatingList<>(CategoryForm.class);
    private ThresholdsForm thresholds = new ThresholdsForm();

    /** @return the editable category list */
    public List<CategoryForm> getCategories() {
        return categories;
    }

    /** @param categories the bound category list */
    public void setCategories(List<CategoryForm> categories) {
        this.categories = categories;
    }

    /** @return the editable thresholds */
    public ThresholdsForm getThresholds() {
        return thresholds;
    }

    /** @param thresholds the bound thresholds */
    public void setThresholds(ThresholdsForm thresholds) {
        this.thresholds = thresholds;
    }

    /** Form-binding row for a single scoring category. */
    public static class CategoryForm {
        private String key;
        private String description;
        private Integer maxPoints;
        private List<TierForm> tiers = new AutoPopulatingList<>(TierForm.class);

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getMaxPoints() {
            return maxPoints;
        }

        public void setMaxPoints(Integer maxPoints) {
            this.maxPoints = maxPoints;
        }

        public List<TierForm> getTiers() {
            return tiers;
        }

        public void setTiers(List<TierForm> tiers) {
            this.tiers = tiers;
        }
    }

    /** Form-binding row for a single tier within a category. */
    public static class TierForm {
        private String label;
        private Integer points;
        private String criteria;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Integer getPoints() {
            return points;
        }

        public void setPoints(Integer points) {
            this.points = points;
        }

        public String getCriteria() {
            return criteria;
        }

        public void setCriteria(String criteria) {
            this.criteria = criteria;
        }
    }

    /** Form-binding object for the thresholds triple. */
    public static class ThresholdsForm {
        private Integer applyImmediately;
        private Integer apply;
        private Integer considerOnly;

        public Integer getApplyImmediately() {
            return applyImmediately;
        }

        public void setApplyImmediately(Integer applyImmediately) {
            this.applyImmediately = applyImmediately;
        }

        public Integer getApply() {
            return apply;
        }

        public void setApply(Integer apply) {
            this.apply = apply;
        }

        public Integer getConsiderOnly() {
            return considerOnly;
        }

        public void setConsiderOnly(Integer considerOnly) {
            this.considerOnly = considerOnly;
        }
    }
}
