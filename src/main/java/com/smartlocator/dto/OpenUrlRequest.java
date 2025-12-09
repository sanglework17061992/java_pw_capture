package com.smartlocator.dto;

import lombok.Data;

@Data
public class OpenUrlRequest {
    private String url;
    private String browserType; // chromium, firefox, webkit
}
