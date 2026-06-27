package com.artverse.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MangaPromptBuilderTest {

    private final MangaPromptBuilder builder = new MangaPromptBuilder(new MangaPromptTemplates());

    @Test
    void buildsStoryboardInstructionFromYaml() {
        String prompt = builder.storyboardInstruction(3);

        assertThat(prompt)
                .contains("【ArtVerse 漫画分镜生成规范】")
                .contains("恰好 3 个字符串元素")
                .contains("只输出 JSON 数组");
    }

    @Test
    void buildsReadableChineseImagePromptWithCurrentPagePriority() {
        String prompt = builder.buildImagePrompt(
                "第2页：【第1格】主角回头。【第2格】雨水落下。【第3格】手握门把。【第4格】灯光亮起。",
                "角色：林澈，黑色短发，灰色风衣。",
                "japanese_manga",
                "bw",
                false,
                List.of(
                        "第1页：【第1格】雨夜街道。【第2格】主角走近。【第3格】门牌特写。【第4格】他停步。",
                        "第2页：【第1格】主角回头。【第2格】雨水落下。【第3格】手握门把。【第4格】灯光亮起。",
                        "第3页：【第1格】门后阴影。【第2格】对视。【第3格】惊讶。【第4格】切黑。"),
                2);

        assertThat(prompt)
                .contains("你正在绘制一部连续漫画作品的第 2 页")
                .contains("【当前只绘制这一页】")
                .contains("当前页内容优先级最高")
                .contains("所有可读文字必须是简体中文")
                .contains("日式漫画风格")
                .doesNotContain("\u9286")
                .doesNotContain("\u7ED7");
    }

    @Test
    void tellsImageEditsToUseReferencesOnlyForCharacterConsistency() {
        String prompt = builder.buildImagePrompt(
                "第1页：【第1格】少女抬头。【第2格】风吹起发梢。【第3格】她伸手。【第4格】光落下。",
                "",
                "watercolor",
                "color",
                true,
                List.of("第1页：【第1格】少女抬头。【第2格】风吹起发梢。【第3格】她伸手。【第4格】光落下。"),
                1);

        assertThat(prompt)
                .contains("参考图只用于人物一致性")
                .contains("不要照搬参考图的姿势、背景或构图")
                .contains("水彩淡雅漫画风格")
                .contains("色彩模式：全彩");
    }

    @Test
    void fallsBackToDefaultStyleAndColorMode() {
        String prompt = builder.buildImagePrompt(
                "第1页：【第1格】开场。【第2格】推进。【第3格】特写。【第4格】收束。",
                "",
                "unknown",
                "unknown",
                false,
                List.of("第1页：【第1格】开场。【第2格】推进。【第3格】特写。【第4格】收束。"),
                1);

        assertThat(prompt)
                .contains("日式漫画风格")
                .contains("色彩模式：黑白");
    }
}
