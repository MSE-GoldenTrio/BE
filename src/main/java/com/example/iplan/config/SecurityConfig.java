package com.example.iplan.config;

import com.example.iplan.auth.CustomLogoutHandler;
import com.example.iplan.auth.ExceptionHandler.CustomAccessDeniedHandler;
import com.example.iplan.auth.ExceptionHandler.CustomAuthenticationEntryPoint;
import com.example.iplan.auth.oauth2.CustomOAuth2UserService;
import com.example.iplan.auth.oauth2.OAuth2FailureHandler;
import com.example.iplan.auth.oauth2.OAuth2SuccessHandler;
import com.example.iplan.auth.jwt.JwtAuthenticationFilter;
import com.example.iplan.auth.jwt.JwtTokenProvider;
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
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final CustomLogoutHandler customLogoutHandler;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

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
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, customAuthenticationEntryPoint), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                // 로그인, 회원가입, 소셜 로그인은 인증 없이 허용
                                .requestMatchers(new AntPathRequestMatcher("/api/auth/**")).permitAll()
                                .requestMatchers(new AntPathRequestMatcher("/oauth2/**")).permitAll()

                                // Swagger 및 API 문서 접근 허용
                                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()

                                // 'unknown' 유저만 접근 가능
                                .requestMatchers(new AntPathRequestMatcher("/api/unknown/**")).hasRole("UNKNOWN")

                                // 'child' 권한이 있어야 접근 가능
                                .requestMatchers(new AntPathRequestMatcher("/child/**")).hasRole("CHILD")

                                // 'parent' 권한이 있어야 접근 가능
                                .requestMatchers(new AntPathRequestMatcher("/parent/**")).hasRole("PARENT")

                                // 나머지 요청은 인증 필요
                                .anyRequest().authenticated()
                )
                // OAuth2 로그인 후 JWT 발급 및 리다이렉트 처리
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .baseUri("/oauth2/authorization") // 프론트에서 OAuth2 인증 요청 URL 설정
                        )
                        .redirectionEndpoint(redir -> redir
                                .baseUri("/login/oauth2/code/*") // 카카오, 구글, 네이버에서 로그인 성공 후 백엔드로 인가 코드를 포함하여 보낼 리디렉션 URL 설정
                        )
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler) // 로그인 성공 핸들러
                        .failureHandler(oAuth2FailureHandler) // 실패 핸들러
                )
                // 로그아웃 설정
                .logout(logout -> logout
                        .logoutUrl("/api/logout") // 클라이언트에서 호출할 로그아웃 엔드포인트
                        .addLogoutHandler(customLogoutHandler)
                        .logoutSuccessHandler(customLogoutHandler)
                )
                // 예외 핸들러
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(customAuthenticationEntryPoint) // 인증 실패 처리
                        .accessDeniedHandler(customAccessDeniedHandler)           // 인가 실패 처리
                );

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
        configuration.addAllowedOriginPattern("https://iplanner.site"); // 모든 도메인 허용 (보안 고려 필요)
        configuration.addAllowedMethod("*"); // 모든 HTTP 메서드 허용
        configuration.addAllowedHeader("*"); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 자격 증명 허용 (쿠키, Authorization 헤더 등)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}


