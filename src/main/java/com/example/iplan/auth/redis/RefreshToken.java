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
    private String id;            // 복합키: nickname:fcmtoken

    private String nickname;      // 식별자(암호화된 닉네임) 별도 보관
    private String fcmToken;      // 실제 기기 토큰(혹은 placeholder)

    private String refreshToken;

    @TimeToLive(unit = TimeUnit.MINUTES)
    private Long expiration;      // 분 단위 TTL

    public static String compositeKey(String nickname, String fcmToken) {
        return nickname + ":" + (fcmToken == null ? "NULL" : fcmToken);
    }

    public RefreshToken(CustomOAuth2UserDetails userDetails,
                        String fcmToken,
                        String refreshToken,
                        Long expiration){
        this.nickname = userDetails.getUsername();     // 암호화된 닉네임
        this.fcmToken = fcmToken;                      // 실제 또는 placeholder
        this.refreshToken = refreshToken;
        this.expiration = expiration;
        this.id = compositeKey(this.nickname, this.fcmToken);
    }
}
