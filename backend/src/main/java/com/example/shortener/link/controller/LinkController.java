package com.example.shortener.link.controller;

import com.example.shortener.link.dto.CreateLinkRequest;
import com.example.shortener.link.dto.CreateLinkResponse;
import com.example.shortener.link.dto.LinkPageResponse;
import com.example.shortener.link.entity.Link;
import com.example.shortener.link.exception.AliasAuthenticationException;
import com.example.shortener.link.exception.ExpirationAuthenticationException;
import com.example.shortener.link.exception.InvalidPageException;
import com.example.shortener.link.service.AliasValidator;
import com.example.shortener.link.service.LinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.shortener.shared.exception.ApiErrorHandler.ApiError;
import org.springframework.http.HttpStatus;
import com.example.shortener.security.service.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {
    private final LinkService service;
    private final AliasValidator aliases;

    private final CurrentUser currentUser;

    public LinkController(LinkService service, CurrentUser currentUser, AliasValidator aliases) {
        this.service = service;
        this.currentUser = currentUser;
        this.aliases = aliases;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List personal links", description = "Requires a Bearer JWT. Returns only the current user's links, newest first. page starts at 0; size defaults to 20 and must be 1–100. Status SAVED indicates a non-expired link; EXPIRED indicates a link whose expiration has been reached. expiresAt is UTC or null.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Personal links, including an empty page",
                    content = @Content(schema = @Schema(implementation = LinkPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or unsupported query parameters"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired identity"),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable")})
    public LinkPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        var ownerId = currentUser.requiredId(jwt);
        if (request.getParameterMap().entrySet().stream().anyMatch(entry ->
                (!entry.getKey().equals("page") && !entry.getKey().equals("size"))
                || entry.getValue().length != 1 || entry.getValue()[0].isBlank())) {
            throw new InvalidPageException();
        }
        return service.list(ownerId, page, size);
    }

    @DeleteMapping("/{shortCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a personal link", description = "Permanently deletes a link owned by the current JWT subject. Missing, anonymous and other users' links all return 404. The public redirect then returns 404.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Link deleted", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired identity", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Link not available to this owner", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable", content = @Content(schema = @Schema(implementation = ApiError.class)))})
    public void delete(@PathVariable String shortCode, @AuthenticationPrincipal Jwt jwt) {
        service.delete(shortCode, currentUser.requiredId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a short link", description = "Accepts HTTP/HTTPS URLs up to 8192 characters. A valid Bearer JWT assigns ownership to the current user. Optional aliases require login and 3–32 ASCII letters, numbers, hyphens or underscores, case-sensitive. Surrounding whitespace is stripped; omitted, null or blank aliases generate an eight-character Base62 code. Aliases and generated codes share global uniqueness. Without a token and alias creation is anonymous. Invalid, expired or unknown-user tokens return 401 without insertion. Optional expiresAt requires login and an ISO-8601 timestamp with Z or offset, strictly in the future. Omitted or null means no expiration. Redirects are public and return 410 at expiration.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Link committed to PostgreSQL",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_URL, INVALID_ALIAS, INVALID_EXPIRATION or INVALID_REQUEST (malformed JSON or non-string alias)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Custom alias or expiration requires login; invalid, expired or unknown-user tokens are rejected"),
            @ApiResponse(responseCode = "409", description = "ALIAS_ALREADY_TAKEN: alias or generated code already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))})
    public CreateLinkResponse create(@RequestBody CreateLinkRequest request, @AuthenticationPrincipal Jwt jwt) {
        String alias = aliases.normalize(request.alias());
        if (alias != null && jwt == null) throw new AliasAuthenticationException();
        if (request.expiresAt() != null && jwt == null) throw new ExpirationAuthenticationException();
        return service.create(request.originalUrl(), alias, currentUser.optionalId(jwt), request.expiresAt());
    }
}
