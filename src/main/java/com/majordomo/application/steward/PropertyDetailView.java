package com.majordomo.application.steward;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;

import java.util.List;

/**
 * View-model for the property detail page (everything the page needs in one
 * value object, assembled by {@link PropertyDetailViewService}).
 *
 * @param property           the focal property
 * @param parent             the parent property, or {@code null} if top-level
 * @param children           direct children
 * @param propertyContacts   active PropertyContact link rows
 * @param linkedContacts     contacts hydrated from the active links (same order)
 * @param contactCandidates  contacts in the same org not already linked
 * @param scheduleRows       active schedules with computed days-until-due
 * @param recentRecords      most recent service records (capped)
 * @param attachments        property-scoped attachments
 */
public record PropertyDetailView(
        Property property,
        Property parent,
        List<Property> children,
        List<PropertyContact> propertyContacts,
        List<Contact> linkedContacts,
        List<Contact> contactCandidates,
        List<ScheduleRow> scheduleRows,
        List<ServiceRecord> recentRecords,
        List<Attachment> attachments
) {

    /**
     * Schedule with its days-until-due delta. Negative = overdue, 0 = today,
     * {@code null} when {@code nextDue} is unset.
     *
     * @param schedule     the maintenance schedule
     * @param daysUntilDue computed delta or {@code null}
     */
    public record ScheduleRow(MaintenanceSchedule schedule, Integer daysUntilDue) { }
}
