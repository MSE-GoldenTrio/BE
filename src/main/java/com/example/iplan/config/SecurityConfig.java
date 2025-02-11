package com.example.iplan.config;

import com.example.iplan.auth.oauth2.CustomOAuth2UserService;
import com.example.iplan.config.jwt.JwtAuthenticationFilter;
import com.example.iplan.config.jwt.JwtTokenProvider;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig{
    private final JwtTokenProvider jwtTokenProvider;
    private final FirebaseAuth firebaseAuth;
    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{

        http
                // 기본 설정인 Session 방식을 사용하지 않고 JWT를 사용하기 위해 STATELESS로 처리
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // CSRF 보호 비활성화 (JWT 사용 시 필요 없음)

                // addFilterBefore({등록할 필터}, {특정 필터}) -> 특정 필터 앞에 등록할 필터를 추가
                // JWT 인증 필터 추가
//                .addFilterBefore(new JwtTokenFilter(firebaseAuth), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                // 로그인, 회원가입, 소셜 로그인은 인증 없이 허용
                                .requestMatchers(new AntPathRequestMatcher("/api/auth/login")).permitAll()
                                .requestMatchers(new AntPathRequestMatcher("/api/auth/register")).permitAll()
                                .requestMatchers(new AntPathRequestMatcher("/oauth2/**")).permitAll()

                                // Swagger 및 API 문서 접근 허용
                                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()

                                // 'child' 권한이 있어야 접근 가능
                                .requestMatchers(new AntPathRequestMatcher("/api/child/**")).hasRole("CHILD")

                                // 'parent' 권한이 있어야 접근 가능
                                .requestMatchers(new AntPathRequestMatcher("/api/parent/**")).hasRole("PARENT")

                                // 나머지 요청은 인증 필요
                                .anyRequest().authenticated()
                )
                // OAuth2 로그인 후 JWT 발급 및 리다이렉트 처리
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler((request, response, authentication) -> {
                            log.info("OAuth2 Login Successfully: " + authentication.getName());
                            response.sendRedirect("http://localhost:8080/register");
                        })
                )

                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt Encoder 사용
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*"); // 모든 도메인 허용 (보안 고려 필요)
        configuration.addAllowedMethod("*"); // 모든 HTTP 메서드 허용
        configuration.addAllowedHeader("*"); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 자격 증명 허용 (쿠키, Authorization 헤더 등)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}


