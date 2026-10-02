package com.hakimi.aviation.component.FlightData;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hakimi.aviation.dto.FlightBlueprintDTO;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class BlueprintLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public BlueprintLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public List<FlightBlueprintDTO> loadBlueprints(String path) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + path);
            try (InputStream inputStream = resource.getInputStream()) {
                // 使用 Jackson 将 JSON 数组直接转为 DTO 列表
                return objectMapper.readValue(inputStream, new TypeReference<>() {});
            }
        } catch (Exception e) {
            throw new RuntimeException("读取航班蓝图失败: " + path, e);
        }
    }
}
