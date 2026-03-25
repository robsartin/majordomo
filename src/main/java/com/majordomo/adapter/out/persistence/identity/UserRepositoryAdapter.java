package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpa;

    public UserRepositoryAdapter(JpaUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        var entity = UserMapper.toEntity(user);
        return UserMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(UserMapper::toDomain);
    }
}
