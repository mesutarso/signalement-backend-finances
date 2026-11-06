package cd.gouv.finances.signalements.access.controller;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.access.dto.AccountResponse;
import cd.gouv.finances.signalements.access.dto.LoginRequest;
import cd.gouv.finances.signalements.access.mapper.AccountMapper;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/api/auth/csrf")
    @SecurityRequirements
    @Operation(summary = "Issues the token that protects writes")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth")
    @SecurityRequirements
    @Operation(summary = "Authenticates an account")
    public AccountResponse authenticate(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            return AccountMapper.toResponse((AccountUserDetails) authentication.getPrincipal());
        } catch (AuthenticationException exception) {
            throw new AccessRejected("rejected-credentials", 401);
        }
    }

    @GetMapping("/api/auth")
    @SecurityRequirement(name = "auth")
    @Operation(summary = "Returns the authenticated account")
    public AccountResponse current(@AuthenticationPrincipal AccountUserDetails account) {
        return AccountMapper.toResponse(account);
    }

    @DeleteMapping("/api/auth")
    @SecurityRequirement(name = "auth")
    @Operation(summary = "Ends the current authentication")
    public ResponseEntity<Void> close(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
