package com.example.iplan.auth.redis;

import com.example.iplan.auth.redis.UserRefreshToken;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.repository.CrudRepository;


@RedisHash
public interface RefreshTokenRepository extends CrudRepository<UserRefreshToken, String> {

}
