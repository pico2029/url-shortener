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

    public LinkController(LinkService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an anonymous short link", description = "Accepts HTTP/HTTPS URLs up to 8192 characters, including local hosts. Each request creates a new link. The returned short URL opens the destination through a public 302 redirect.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Link committed to PostgreSQL",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL or malformed JSON",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))})
    public CreateLinkResponse create(@RequestBody CreateLinkRequest request) {
        return service.create(request.originalUrl());
    }
}
