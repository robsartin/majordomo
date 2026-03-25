package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ServiceRecordRepositoryAdapter implements ServiceRecordRepository {

    private final JpaServiceRecordRepository jpa;

    public ServiceRecordRepositoryAdapter(JpaServiceRecordRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ServiceRecord save(ServiceRecord record) {
        var entity = ServiceRecordMapper.toEntity(record);
        return ServiceRecordMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<ServiceRecord> findById(UUID id) {
        return jpa.findById(id).map(ServiceRecordMapper::toDomain);
    }

    @Override
    public List<ServiceRecord> findByPropertyId(UUID propertyId) {
        return jpa.findByPropertyId(propertyId).stream().map(ServiceRecordMapper::toDomain).toList();
    }

    @Override
    public List<ServiceRecord> findByScheduleId(UUID scheduleId) {
        return jpa.findByScheduleId(scheduleId).stream().map(ServiceRecordMapper::toDomain).toList();
    }
}
