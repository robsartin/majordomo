package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.domain.model.steward.Property;

final class PropertyMapper {

    private PropertyMapper() {}

    static PropertyEntity toEntity(Property property) {
        var entity = new PropertyEntity();
        entity.setId(property.getId());
        entity.setOrganizationId(property.getOrganizationId());
        entity.setParentId(property.getParentId());
        entity.setName(property.getName());
        entity.setDescription(property.getDescription());
        entity.setSerialNumber(property.getSerialNumber());
        entity.setModelNumber(property.getModelNumber());
        entity.setManufacturer(property.getManufacturer());
        entity.setCategory(property.getCategory());
        entity.setLocation(property.getLocation());
        entity.setStatus(property.getStatus());
        entity.setAcquiredOn(property.getAcquiredOn());
        entity.setWarrantyExpiresOn(property.getWarrantyExpiresOn());
        entity.setCreatedAt(property.getCreatedAt());
        entity.setUpdatedAt(property.getUpdatedAt());
        entity.setArchivedAt(property.getArchivedAt());
        entity.setPurchasePrice(property.getPurchasePrice());
        return entity;
    }

    static Property toDomain(PropertyEntity entity) {
        var property = new Property();
        property.setId(entity.getId());
        property.setOrganizationId(entity.getOrganizationId());
        property.setParentId(entity.getParentId());
        property.setName(entity.getName());
        property.setDescription(entity.getDescription());
        property.setSerialNumber(entity.getSerialNumber());
        property.setModelNumber(entity.getModelNumber());
        property.setManufacturer(entity.getManufacturer());
        property.setCategory(entity.getCategory());
        property.setLocation(entity.getLocation());
        property.setStatus(entity.getStatus());
        property.setAcquiredOn(entity.getAcquiredOn());
        property.setWarrantyExpiresOn(entity.getWarrantyExpiresOn());
        property.setCreatedAt(entity.getCreatedAt());
        property.setUpdatedAt(entity.getUpdatedAt());
        property.setArchivedAt(entity.getArchivedAt());
        property.setPurchasePrice(entity.getPurchasePrice());
        return property;
    }
}
