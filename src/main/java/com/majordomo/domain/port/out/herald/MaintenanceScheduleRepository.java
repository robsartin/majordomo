package com.majordomo.domain.port.out.herald;

import com.majordomo.domain.model.herald.MaintenanceSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceScheduleRepository {

    MaintenanceSchedule save(MaintenanceSchedule schedule);

    Optional<MaintenanceSchedule> findById(UUID id);

    List<MaintenanceSchedule> findByPropertyId(UUID propertyId);

    List<MaintenanceSchedule> findDueBefore(LocalDate date);
}
