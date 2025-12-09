package com.smartlocator.dto;

import com.smartlocator.model.ElementMetadata;
import lombok.Data;

@Data
public class GenerateLocatorsRequest {
    private ElementMetadata metadata;
}
