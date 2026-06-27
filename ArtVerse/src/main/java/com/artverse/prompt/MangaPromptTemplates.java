package com.artverse.prompt;

import com.artverse.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MangaPromptTemplates {

    private final JsonNode root;

    public MangaPromptTemplates() {
        try (InputStream input = new ClassPathResource("prompts/manga-prompts.yml").getInputStream()) {
            this.root = new ObjectMapper(new YAMLFactory()).readTree(input).path("manga");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load manga prompt templates", e);
        }
    }

    public String text(String path) {
        JsonNode node = node(path);
        if (!node.isTextual()) {
            throw new BusinessException(500, "Prompt template is not text: " + path);
        }
        return node.asText();
    }

    public Map<String, String> textMap(String path) {
        JsonNode node = node(path);
        if (!node.isObject()) {
            throw new BusinessException(500, "Prompt template is not a map: " + path);
        }
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isTextual()) {
                throw new BusinessException(500, "Prompt template map value is not text: " + path + "." + field.getKey());
            }
            result.put(field.getKey(), field.getValue().asText());
        }
        return result;
    }

    private JsonNode node(String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = current.path(part);
            if (current.isMissingNode()) {
                throw new BusinessException(500, "Missing prompt template: " + path);
            }
        }
        return current;
    }
}
