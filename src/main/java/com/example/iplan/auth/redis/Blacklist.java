package com.example.iplan.auth.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("blacklist")
public class Blacklist {

    @Id
    private String id; // 사용자 닉네임

    private String accessToken;

    private String reason; // 예: logout, revoked

    @TimeToLive(unit = TimeUnit.MINUTES)
    private Long expiration;
}
