package ch.bzz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class HttpsRedirectFilter extends OncePerRequestFilter {

    private final int httpsPort;

    public HttpsRedirectFilter(@Value("${server.port:8443}") int httpsPort) {
        this.httpsPort = httpsPort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getLocalPort() == 8080 && !request.isSecure()) {
            String location = "https://localhost:" + httpsPort + request.getRequestURI();
            if (request.getQueryString() != null) {
                location += "?" + request.getQueryString();
            }
            response.setStatus(HttpStatus.PERMANENT_REDIRECT.value());
            response.setHeader("Location", location);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
