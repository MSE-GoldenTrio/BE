package com.example.iplan.auth.redis;

import org.springframework.data.repository.CrudRepository;

public interface BlacklistRepository extends CrudRepository<Blacklist, String> {
    boolean existsById(String id); // id = nickname
}
