package com.example.shortener.link;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.shortener.shared.ApiErrorHandler.ApiError;
import org.springframework.http.HttpStatus;
import com.example.shortener.security.CurrentUser;
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
    @Operation(summary = "List personal links", description = "Requires a Bearer JWT. Returns only the current user's links, newest first. page starts at 0; size defaults to 20 and must be 1–100. Status SAVED indicates a persisted link; public redirects are available.")
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a short link", description = "Accepts HTTP/HTTPS URLs up to 8192 characters. A valid Bearer JWT assigns ownership to the current user. Optional aliases require login and 3–32 ASCII letters, numbers, hyphens or underscores, case-sensitive. Surrounding whitespace is stripped; omitted, null or blank aliases generate an eight-character Base62 code. Aliases and generated codes share global uniqueness. Without a token and alias creation is anonymous. Invalid, expired or unknown-user tokens return 401 without insertion. Redirects are public.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Link committed to PostgreSQL",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_URL, INVALID_ALIAS or INVALID_REQUEST (malformed JSON or non-string alias)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Custom alias requires login; invalid, expired or unknown-user tokens are rejected"),
            @ApiResponse(responseCode = "409", description = "ALIAS_ALREADY_TAKEN: alias or generated code already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))})
    public CreateLinkResponse create(@RequestBody CreateLinkRequest request, @AuthenticationPrincipal Jwt jwt) {
        String alias = aliases.normalize(request.alias());
        if (alias != null && jwt == null) throw new AliasAuthenticationException();
        return service.create(request.originalUrl(), alias, currentUser.optionalId(jwt));
    }
}
