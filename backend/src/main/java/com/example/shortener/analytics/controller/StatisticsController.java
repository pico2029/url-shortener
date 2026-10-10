package com.example.shortener.analytics.controller;

import com.example.shortener.analytics.dto.LinkStatistics;
import com.example.shortener.analytics.service.StatisticsService;
import com.example.shortener.security.service.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/links/{shortCode}/stats")
public class StatisticsController {
    private final StatisticsService service;
    private final CurrentUser currentUser;

    public StatisticsController(StatisticsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get personal link statistics", description = "Owner-only lifetime saved GET clicks and 30 daily UTC counts, including today and zero days. Expired links remain available. Recording is asynchronous and best effort; HEAD does not count. from/to are inclusive dates. Historical clicks before tracking are unavailable.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Statistics for an owned link"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid identity"),
            @ApiResponse(responseCode = "404", description = "Link not available to this owner"),
            @ApiResponse(responseCode = "503", description = "Persistence unavailable")})
    public ResponseEntity<LinkStatistics> get(@PathVariable String shortCode, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(shortCode, currentUser.requiredId(jwt)));
    }
}
