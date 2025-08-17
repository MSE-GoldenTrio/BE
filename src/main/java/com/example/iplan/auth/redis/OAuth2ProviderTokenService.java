package com.example.iplan.auth.redis;

import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuth2ProviderTokenService {

    private final StringRedisTemplate redis;

    private static final String ACCESS_FMT = "oauth:access:%s:%s";
    private static final String REFRESH_FMT = "oauth:refresh:%s:%s";

    public void saveAccessToken(String provider, String subject, String token, Duration ttl){
        String key = REFRESH_FMT.formatted(provider, subject);
        if(ttl != null){
            redis.opsForValue().set(key, token, ttl);
        }else{
            redis.opsForValue().set(key, token);
        }
    }

    public void saveRefreshToken(String provider, String subject, String token, Duration ttlOrNull) {
        String key = REFRESH_FMT.formatted(provider, subject);
        if (ttlOrNull != null) {
            redis.opsForValue().set(key, token, ttlOrNull);
        } else {
            redis.opsForValue().set(key, token);
        }
    }

    public Optional<String> getAccessToken(String provider, String subject){
        String key = ACCESS_FMT.formatted(provider, subject);
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    public Optional<String> getRefreshToken(String provider, String subject){
        String key = REFRESH_FMT.formatted(provider, subject);
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    public void deleteAll(String provider, String subject){
        redis.delete(ACCESS_FMT.formatted(provider, subject));
        redis.delete(REFRESH_FMT.formatted(provider, subject));
    }
}
