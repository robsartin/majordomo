package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.User;

final class UserMapper {

    private UserMapper() {}

    static UserEntity toEntity(User user) {
        var entity = new UserEntity();
        entity.setId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getEmail());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        entity.setArchivedAt(user.getArchivedAt());
        return entity;
    }

    static User toDomain(UserEntity entity) {
        var user = new User(entity.getId(), entity.getUsername(), entity.getEmail());
        user.setCreatedAt(entity.getCreatedAt());
        user.setUpdatedAt(entity.getUpdatedAt());
        user.setArchivedAt(entity.getArchivedAt());
        return user;
    }
}
