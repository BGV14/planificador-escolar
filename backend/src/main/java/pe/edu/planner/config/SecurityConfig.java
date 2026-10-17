package pe.edu.planner.config;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import pe.edu.planner.auth.PlannerPrincipal;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService users(JdbcTemplate db) {
    return username ->
        db
            .query(
                "select * from app_users where username=?",
                (rs, n) ->
                    new PlannerPrincipal(
                        rs.getString("id"),
                        rs.getString("school_id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")),
                username)
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    // Session-backed synchronizer token remains enabled, including login and logout.
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/csrf", "/api/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/", "/index.html", "/assets/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .formLogin(
            login ->
                login
                    .loginProcessingUrl("/api/login")
                    .successHandler((request, response, authentication) -> response.setStatus(200))
                    .failureHandler((request, response, error) -> response.sendError(401)))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)))
        .requestCache(cache -> cache.disable())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, error) -> response.sendError(401)))
        .build();
  }
}
