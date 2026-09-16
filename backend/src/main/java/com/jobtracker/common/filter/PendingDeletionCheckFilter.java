package com.jobtracker.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Component
public class PendingDeletionCheckFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String errorBaseUrl;

    public PendingDeletionCheckFilter(UserRepository userRepository,
                                      ObjectMapper objectMapper,
                                      @Value("${app.error.base-url:https://api.jobtracker.app/errors}") String errorBaseUrl) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.errorBaseUrl = errorBaseUrl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allowed paths for PENDING_DELETION users (refresh and account related)
        if (path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/logout") && !path.equals("/api/v1/auth/refresh")) {
            // Unauthenticated routes generally bypass the filter since there's no JWT.
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String subject = jwtAuth.getToken().getSubject();
            if (subject != null) {
                try {
                    UUID userId = UUID.fromString(subject);
                    Optional<User> userOpt = userRepository.findById(userId);
                    
                    if (userOpt.isPresent() && "PENDING_DELETION".equals(userOpt.get().getStatus())) {
                        if (isBlockedPathForPendingDeletion(path)) {
                            sendErrorResponse(response);
                            return;
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        filterChain.doFilter(request, response);
    }
    
    private boolean isBlockedPathForPendingDeletion(String path) {
        // According to requirements: /applications -> 403, /account -> 200, /auth/refresh -> allowed.
        // It means anything except /account, /auth/refresh (and probably /auth/logout) is blocked.
        if (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout")) {
            return false;
        }
        if (path.startsWith("/api/v1/account")) {
            return false;
        }
        // For now, block everything else if authenticated and PENDING_DELETION
        return true;
    }

    private void sendErrorResponse(HttpServletResponse response) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Account is pending deletion");
        problemDetail.setType(URI.create(errorBaseUrl + "/account-pending-deletion"));
        problemDetail.setTitle("Account Pending Deletion");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
