package com.majordomo.domain.port.out.herald;

import com.majordomo.domain.model.herald.ServiceRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRecordRepository {

    ServiceRecord save(ServiceRecord record);

    Optional<ServiceRecord> findById(UUID id);

    List<ServiceRecord> findByPropertyId(UUID propertyId);

    List<ServiceRecord> findByScheduleId(UUID scheduleId);
}
