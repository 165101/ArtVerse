package com.artverse.application.workflow;

import org.springframework.stereotype.Component;

/**
 * Builds a minimal classification prompt for the intent classifier.
 * The LLM is asked to return a single JSON object with route, intent,
 * confidence, and reason — no conversational filler.
 */
@Component
public class ClassificationPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an intent classifier for a manga creation workflow engine.
            Your ONLY job is to classify the user's latest message into exactly one route.

            ## Routes

            - DIRECTOR: The user wants to generate, save, modify, or rewrite storyboard scenes.
              Examples: "生成分镜", "create a storyboard", "修改第3条分镜", "把分镜保存一下", "generate storyboard".
            - REVIEW: The user wants to inspect, check, or audit existing work — review scenes,
              check for risks or problems, evaluate quality, or see what needs fixing.
              Examples: "质检", "检查分镜有什么问题", "review the storyboard", "审核".
            - CHAT: The user is asking a general question, checking progress/status, or making
              conversation that does NOT modify story content.
              Examples: "进度怎么样", "本章完成了吗", "how does this work", "为什么这样设计", "status".
            - HITL: The user is confirming, choosing, approving, or responding to a previous
              prompt from the agent. This is for human-in-the-loop decisions.
              Examples: "确认", "选第二个", "同意继续", "approve", "confirm".

            ## Context you may consider

            - If the chapter already has storyboard scenes (sceneCount > 0), REVIEW becomes more likely
              when the user mentions checking or inspecting.
            - If the chapter has NO storyboard scenes (sceneCount == 0), DIRECTOR becomes more
              likely when the user mentions the storyboard.
            - General questions about status, progress, or explanations should route to CHAT.

            ## Output format

            Return ONLY a single JSON object on one line. No markdown fences, no explanation outside the JSON.
            The JSON must have these exact keys:
            {"route": "<DIRECTOR|REVIEW|CHAT|HITL>", "intent": "<short label>", "confidence": <0.0-1.0>, "reason": "<brief reason in Chinese>"}

            Choose the SINGLE best route. Confidence should reflect how clear the intent is:
            - 0.85-0.95: very clear keyword match or explicit instruction
            - 0.65-0.84: reasonable interpretation but somewhat ambiguous
            - 0.40-0.64: best guess, the message is vague
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserMessage(String userMessage, MangaWorkflowContextSnapshot context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Chapter: ").append(nullToBlank(context.chapterDisplayName())).append("\n");
        sb.append("Story style: ").append(nullToBlank(context.storyStyle())).append("\n");
        sb.append("Existing scenes: ").append(context.sceneCount()).append("\n");
        sb.append("Generated images: ").append(context.imageCount()).append("\n");
        if (context.warnings() != null && !context.warnings().isEmpty()) {
            sb.append("Warnings: ").append(String.join(", ", context.warnings())).append("\n");
        }
        sb.append("\nUser message:\n").append(userMessage == null ? "" : userMessage.trim());
        return sb.toString();
    }

    private static String nullToBlank(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}
