package com.project8.jobvault.security;

import com.project8.jobvault.auth.AuthCookieProperties;
import com.project8.jobvault.auth.JwtProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ JwtProperties.class, AuthCookieProperties.class, CorsProperties.class })
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        JsonAuthenticationEntryPoint authenticationEntryPoint,
                        JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
                http.csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/auth/login",
                                                                "/api/auth/register",
                                                                "/api/auth/refresh")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/employer/**").hasRole("EMPLOYER")
                                                .requestMatchers("/api/seeker/**").hasRole("JOB_SEEKER")
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(authenticationEntryPoint)
                                                .accessDeniedHandler(accessDeniedHandler))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
                CorsConfiguration cors = new CorsConfiguration();
                cors.setAllowedOrigins(corsProperties.getAllowedOrigins());
                cors.setAllowedMethods(corsProperties.getAllowedMethods());
                cors.setAllowedHeaders(corsProperties.getAllowedHeaders());
                cors.setAllowCredentials(corsProperties.isAllowCredentials());
                cors.setMaxAge(corsProperties.getMaxAge());
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/api/**", cors);
                return source;
        }

        @Bean
        public Clock clock() {
                return Clock.systemUTC();
        }
}
