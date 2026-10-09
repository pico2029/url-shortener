package com.example.shortener.link;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {
    private final LinkResolutionService service;

    public RedirectController(LinkResolutionService service) {
        this.service = service;
    }

    @GetMapping("/r/{shortCode}")
    @Operation(summary = "Open a short link", description = "Public redirect resolved from Redis, falling back to PostgreSQL when the cache misses or is unavailable. HEAD returns the same status and headers without a body. Destinations are not fetched. Query parameters on the short URL are not forwarded.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the saved destination", content = @Content,
                    headers = @Header(name = "Location", description = "Original URL encoded as an ASCII URI", schema = @Schema(type = "string", format = "uri"))),
            @ApiResponse(responseCode = "404", description = "Short code not found",
                    content = @Content(mediaType = "text/html", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "410", description = "Link expired",
                    content = @Content(mediaType = "text/html", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable",
                    content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    })
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        URI destination = URI.create(URI.create(service.resolve(shortCode)).toASCIIString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(destination)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
