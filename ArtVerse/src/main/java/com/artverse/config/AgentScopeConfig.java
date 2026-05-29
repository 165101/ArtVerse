package com.artverse.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class AgentScopeConfig {

    private static final Path DEFAULT_WORKSPACE = Paths.get(".agentscope/workspace");

    @Bean
    public Dotenv dotenv() {
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            return Dotenv.configure().directory(".").load();
        }
        return Dotenv.configure().ignoreIfMissing().load();
    }

    @Bean
    public Path agentScopeWorkspace() {
        try {
            Files.createDirectories(DEFAULT_WORKSPACE);
            Path agentsMd = DEFAULT_WORKSPACE.resolve("AGENTS.md");
            if (!Files.exists(agentsMd)) {
                Files.writeString(agentsMd, """
                        # ArtVerse AI 创作助手

                        你是一个帮助用户创作小说和漫画内容的 AI 助手。

                        ## 行为约定
                        - 创作小说内容要有文学性和画面感
                        - 回答用简洁中文，必要时给出要点列表
                        - 对不确定的内容要主动说明，不要臆造
                        - 保持角色设定的一致性
                        """);
            }
        } catch (IOException e) {
            log.warn("Failed to init AgentScope workspace: {}", e.getMessage());
        }
        return DEFAULT_WORKSPACE;
    }

    @Bean
    public CompactionConfig defaultCompactionConfig() {
        return CompactionConfig.builder()
                .triggerMessages(30)
                .keepMessages(10)
                .flushBeforeCompact(true)
                .build();
    }

    @Bean
    public Model deepSeekModel(ArtVerseProperties properties, Dotenv dotenv) {
        String apiKey = properties.getDeepseek().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = dotenv.get("DEEPSEEK_API_KEY");
        }
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.getDeepseek().getModel())
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .stream(true)
                .build();
    }
}
