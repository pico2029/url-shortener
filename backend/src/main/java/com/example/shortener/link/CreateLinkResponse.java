package com.example.shortener.link;

public record CreateLinkResponse(String originalUrl, String shortCode, String shortUrl) {}
