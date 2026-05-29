package com.artverse.application;

import com.artverse.agents.*;
import com.artverse.common.BusinessException;
import com.artverse.domain.Chapter;
import com.artverse.media.MediaStorageService;
import com.artverse.persistence.ChapterRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneService {

    private final ChapterRepository chapterRepository;
    private final HarnessAgentGateway harnessAgentGateway;
    private final CharacterProfileService characterProfileService;
    private final MediaStorageService mediaStorageService;
    private final ObjectMapper objectMapper;

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*\\]", Pattern.DOTALL);

    @Transactional(readOnly = true)
    public List<String> getScenes(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));
        return parseScenesText(chapter.getScenesText());
    }

    @Transactional
    public List<String> generateScenes(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        String material = chapter.novelContentOrJoinedMessages();
        if (material.isBlank()) {
            throw new BusinessException(400, "No content to generate scenes from");
        }

        Map<String, Object> profileResult = characterProfileService.resolveEffective(chapterId);
        String profiles = (String) profileResult.get("content");

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(new AgentMessage("system", buildSceneSystemPrompt()));
        messages.add(new AgentMessage("user", buildSceneUserPrompt(material, profiles, chapter.getImageCount())));

        AgentRunRequest request = new AgentRunRequest(
                "default",
                chapter.getStory().getId(),
                chapterId,
                AgentTaskType.STORYBOARD,
                messages,
                Map.of("pageCount", chapter.getImageCount(), "characterProfiles", profiles)
        );

        String raw;
        try {
            raw = harnessAgentGateway.generateText(request).block();
        } catch (Exception e) {
            throw new BusinessException(502, "AI 服务不可用: " + e.getMessage());
        }
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(502, "AI returned empty scene response");
        }

        List<String> scenes = parseAndValidateScenes(raw, chapter.getImageCount());
        chapter.setScenesText(objectMapper.valueToTree(scenes).toString());
        chapterRepository.save(chapter);

        // Write to file for compatibility
        try {
            Path chapterDir = mediaStorageService.getChapterDir(chapterId);
            Files.createDirectories(chapterDir);
            Files.writeString(chapterDir.resolve("scenes.txt"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(scenes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write scenes.txt: {}", e.getMessage());
        }

        return scenes;
    }

    @Transactional
    public List<String> updateScenes(Long chapterId, List<String> scenes) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        if (scenes == null || scenes.isEmpty()) {
            throw new BusinessException(400, "Scenes cannot be empty");
        }
        if (scenes.size() != chapter.getImageCount()) {
            throw new BusinessException(400, "Scenes count must equal image count (" + chapter.getImageCount() + ")");
        }
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i) == null || scenes.get(i).isBlank()) {
                throw new BusinessException(400, "Scene " + (i + 1) + " cannot be empty");
            }
        }

        chapter.setScenesText(objectMapper.valueToTree(scenes).toString());
        chapterRepository.save(chapter);

        // Write to file
        try {
            Path chapterDir = mediaStorageService.getChapterDir(chapterId);
            Files.createDirectories(chapterDir);
            Files.writeString(chapterDir.resolve("scenes.txt"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(scenes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write scenes.txt: {}", e.getMessage());
        }

        return scenes;
    }

    public List<String> parseAndValidateScenes(String raw, int expectedCount) {
        List<String> scenes = parseScenesText(raw);
        if (scenes.size() != expectedCount) {
            throw new BusinessException(502, "AI returned " + scenes.size() + " scenes but expected " + expectedCount);
        }
        return scenes;
    }

    public List<String> parseScenesText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Try direct JSON parse
        try {
            return objectMapper.readValue(text, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
        }

        // Try extracting JSON array from markdown code fence
        String extracted = extractJsonFromCodeFence(text);
        if (extracted != null) {
            try {
                return objectMapper.readValue(extracted, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
            }
        }

        // Try regex for first JSON array
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return objectMapper.readValue(matcher.group(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
            }
        }

        throw new BusinessException(502, "AI returned invalid scene JSON");
    }

    private String extractJsonFromCodeFence(String text) {
        int start = text.indexOf("```json");
        if (start == -1) start = text.indexOf("```");
        if (start == -1) return null;

        int codeStart = text.indexOf("\n", start);
        if (codeStart == -1) return null;
        codeStart++;

        int codeEnd = text.indexOf("```", codeStart);
        if (codeEnd == -1) return null;

        return text.substring(codeStart, codeEnd).trim();
    }

    private String buildSceneSystemPrompt() {
        return """
                你是一位专业的漫画分镜师。请将小说内容拆分成漫画页分镜。

                输出要求：
                - 严格 JSON 数组格式，每个元素是一页漫画的描述
                - 每页包含 4-6 个分镜格，最少 4 格
                - 使用 【第1格】、【第2格】 格式描述每格内容
                - 包含构图、人物、动作、表情、对话气泡、音效字
                - 直接输出 JSON 数组，不输出其他内容
                """;
    }

    private String buildSceneUserPrompt(String material, String profiles, int pageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下内容拆分成 ").append(pageCount).append(" 页漫画分镜。\n\n");
        sb.append("【小说内容】\n").append(material).append("\n\n");
        if (profiles != null && !profiles.isBlank()) {
            sb.append("【角色设定】\n").append(profiles).append("\n\n");
        }
        sb.append("请输出 ").append(pageCount).append(" 个元素的 JSON 数组。");
        return sb.toString();
    }
}
