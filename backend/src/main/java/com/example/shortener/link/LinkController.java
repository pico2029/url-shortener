package com.example.shortener.link;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.shortener.shared.ApiErrorHandler.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {
    private final LinkService service;
    private final AliasValidator aliases;

    public LinkController(LinkService service, AliasValidator aliases) {
        this.service = service;
        this.aliases = aliases;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a short link", description = "Accepts HTTP/HTTPS URLs up to 8192 characters. Optional aliases require a Bearer JWT: 3–32 ASCII letters, numbers, hyphens or underscores, case-sensitive. Surrounding whitespace is stripped; omitted, null or blank aliases generate an eight-character Base62 code. Aliases and generated codes share global uniqueness. Each request creates a new link; redirects are public.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Link committed to PostgreSQL",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_URL, INVALID_ALIAS or INVALID_REQUEST (malformed JSON or non-string alias)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Custom alias requires login; invalid or expired Bearer tokens are also rejected"),
            @ApiResponse(responseCode = "409", description = "ALIAS_ALREADY_TAKEN: alias or generated code already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))})
    public CreateLinkResponse create(@RequestBody CreateLinkRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt jwt) {
        String alias = aliases.normalize(request.alias());
        if (alias != null && jwt == null) throw new AliasAuthenticationException();
        return service.create(request.originalUrl(), alias);
    }
}
