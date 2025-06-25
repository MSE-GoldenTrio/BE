package com.example.iplan.auth.redis;

import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@RedisHash(value = "refreshToken", timeToLive = 600)//단위는 초임
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {
    @Id
    private String id;

    private String refreshToken;

    @TimeToLive(unit = TimeUnit.MINUTES)
    private Long expiration;

    public RefreshToken(CustomOAuth2UserDetails userDetails, String refreshToken, Long expiration){
        this.id = userDetails.getUsername();    // 사용자 닉네임이 식별자
        this.refreshToken = refreshToken;
        this.expiration = expiration;
    }
}
