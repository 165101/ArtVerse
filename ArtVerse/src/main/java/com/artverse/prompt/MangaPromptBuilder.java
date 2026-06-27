package com.artverse.prompt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MangaPromptBuilder {

    private static final Pattern FORBIDDEN_STORYBOARD_CUE = Pattern.compile(
            "\\b(scene|panel|prompt|negative|page|chapter|dialogue|bubble|speech)\\b",
            Pattern.CASE_INSENSITIVE);

    private final MangaPromptTemplates templates;

    public String storyboardInstruction(int pageCount) {
        return render(templates.text("storyboard.instruction"), Map.of("pageCount", String.valueOf(pageCount)));
    }

    public String storyboardMaterialWrapper(String context) {
        return render(templates.text("storyboard.material_wrapper"), Map.of("context", context == null ? "" : context));
    }

    public boolean isStoryboardPage(String scene) {
        return scene != null
                && scene.contains("【第1格")
                && scene.contains("【第2格")
                && scene.contains("【第3格")
                && scene.contains("【第4格");
    }

    public boolean hasForbiddenStoryboardCue(String scene) {
        if (scene == null) return false;
        return FORBIDDEN_STORYBOARD_CUE.matcher(scene).find()
                || scene.contains("负面：")
                || scene.contains("禁止列表")
                || scene.contains("竖构图:3");
    }

    public String buildImagePrompt(String scene, String profiles, String mangaStyle, String colorMode,
                                   boolean hasRefImages, List<String> allScenes, int imageNumber) {
        int totalPages = allScenes == null || allScenes.isEmpty() ? 1 : allScenes.size();
        StringBuilder sb = new StringBuilder();

        appendSection(sb, render(templates.text("image.header"), Map.of(
                "imageNumber", String.valueOf(imageNumber),
                "totalPages", String.valueOf(totalPages)
        )));

        if (hasRefImages) {
            appendSection(sb, templates.text("image.reference_consistency"));
        } else if (profiles != null && !profiles.isBlank()) {
            appendSection(sb, render(templates.text("image.character_profiles"), Map.of("profiles", profiles)));
        }

        appendContext(sb, allScenes, scene, imageNumber);
        appendSection(sb, render(templates.text("image.current_page"), Map.of("scene", scene)));
        appendSection(sb, templates.text("image.drawing_rules"));
        appendSection(sb, templates.text("image.style_title")
                + "\n" + styleTemplate(mangaStyle)
                + "\n" + colorModifier(colorMode));

        return sb.toString().trim() + "\n";
    }

    private void appendContext(StringBuilder sb, List<String> allScenes, String scene, int imageNumber) {
        sb.append(templates.text("image.context_title")).append("\n");
        if (allScenes == null || allScenes.isEmpty()) {
            sb.append(render(templates.text("image.context_empty"), Map.of("scene", scene))).append("\n\n");
            return;
        }

        if (allScenes.size() <= 6) {
            for (int i = 0; i < allScenes.size(); i++) {
                sb.append(render(templates.text("image.context_all_page"), Map.of(
                        "pageNumber", String.valueOf(i + 1),
                        "scene", allScenes.get(i)
                ))).append("\n");
            }
            sb.append("\n");
            return;
        }

        int currentIndex = Math.max(0, Math.min(imageNumber - 1, allScenes.size() - 1));
        if (currentIndex > 1) {
            sb.append(render(templates.text("image.context_prior_summary"),
                    Map.of("endPage", String.valueOf(currentIndex - 1)))).append("\n");
        }
        if (currentIndex > 0) {
            sb.append(render(templates.text("image.context_previous"),
                    Map.of("scene", allScenes.get(currentIndex - 1)))).append("\n");
        }
        sb.append(render(templates.text("image.context_current"),
                Map.of("scene", allScenes.get(currentIndex)))).append("\n");
        if (currentIndex + 1 < allScenes.size()) {
            sb.append(render(templates.text("image.context_next"),
                    Map.of("scene", allScenes.get(currentIndex + 1)))).append("\n");
        }
        if (currentIndex + 2 < allScenes.size()) {
            sb.append(render(templates.text("image.context_later_summary"), Map.of(
                    "startPage", String.valueOf(currentIndex + 3),
                    "totalPages", String.valueOf(allScenes.size())
            ))).append("\n");
        }
        sb.append("\n");
    }

    private String styleTemplate(String mangaStyle) {
        Map<String, String> styles = templates.textMap("styles");
        return styles.getOrDefault(mangaStyle == null ? "japanese_manga" : mangaStyle,
                styles.get("japanese_manga"));
    }

    private String colorModifier(String colorMode) {
        Map<String, String> colorModes = templates.textMap("color_modes");
        return colorModes.getOrDefault(colorMode == null ? "bw" : colorMode, colorModes.get("bw"));
    }

    private void appendSection(StringBuilder sb, String section) {
        if (section == null || section.isBlank()) {
            return;
        }
        sb.append(section.strip()).append("\n\n");
    }

    private String render(String template, Map<String, String> variables) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }
}
