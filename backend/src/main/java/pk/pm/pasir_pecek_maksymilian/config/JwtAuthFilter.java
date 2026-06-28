package pk.pm.pasir_pecek_maksymilian.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pk.pm.pasir_pecek_maksymilian.security.JwtUtil;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        // 1. Szukamy tokena w nagłówku (standardowe zapytania REST/GraphQL)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        // 2. NIEZAWODNE szukanie tokena w URL (dla WebSocketów)
        else if (request.getQueryString() != null && request.getQueryString().contains("token=")) {
            String queryString = request.getQueryString();
            int startIndex = queryString.indexOf("token=") + 6;
            int endIndex = queryString.indexOf("&", startIndex);
            token = (endIndex == -1) ? queryString.substring(startIndex) : queryString.substring(startIndex, endIndex);
        }

        if (token != null) {
            try {
                String email = jwtUtil.extractUsername(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    if (jwtUtil.validateToken(token)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        SecurityContextHolder.getContext().setAuthentication(authToken); // Ustawienie kontekstu logowania
                    }
                }
            } catch (Exception ex) {
                System.out.println("Błąd parsowania JWT: " + ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}