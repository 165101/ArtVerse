# ArtVerse Java 复刻项目后端实现规格文档

> 目标：基于 `D:\PyProject\LoreVista` 的 Python/FastAPI 项目，使用 Java 复刻同等核心能力。本文档以后端为主，采用 Spring Boot 承担确定性业务后端，使用 agentscope-java Harness 承担 AI 创作与生成流程编排。本文已升级为实现规格版，优先服务于其他模型直接拆分任务、生成代码和编写测试。

## 1. 项目定位

ArtVerse 是一个 AI 小说创作与漫画生成工具，核心链路为：

1. 用户创建故事与章节。
2. 用户通过 AI 对话讨论小说设定，或直接粘贴已有小说正文。
3. AI 将聊天/小说内容整理成章节小说正文。
4. AI 将章节内容拆成指定数量的漫画页分镜。
5. 图片模型按分镜逐页生成漫画图。
6. 用户可维护角色卡、参考图、设定组，并可导入/导出完整作品包。

Java 复刻版应优先保持现有前端 API 行为兼容，使当前 React 前端或未来 Java 前端都能复用同一后端接口。

## 2. 现有 Python 项目能力梳理

### 2.1 技术栈

现有项目使用：

- 后端：FastAPI、SQLAlchemy、PostgreSQL、SSE、Pillow、httpx。
- 前端：React、TypeScript、Vite、TailwindCSS、lucide-react。
- AI：DeepSeek API 负责对话、小说生成、分镜拆分；Image2 API 负责漫画图片生成。
- 文件：生成图片、封面、参考图、分镜文本等存放在 `backend/manga_outputs`。

### 2.2 主要数据对象

现有模型包括：

- `Story`：故事，包含标题、描述、封面、默认参考图、默认角色卡。
- `StoryAssetGroup`：故事级设定组，包含组名、角色卡、多张参考图。
- `Chapter`：章节，包含章节号、小说正文、内容来源、分镜文本、章节级角色卡、章节级参考图、色彩模式、生成图片数量。
- `ChatMessage`：章节对话消息，角色为 user/assistant/system。
- `MangaImage`：生成的漫画页图片，包含页码、图片路径、生成提示词。

### 2.3 主要接口能力

Java 版应覆盖以下接口族：

- 故事 CRUD：创建、列表、详情、更新、删除、上传封面。
- 章节 CRUD：列表、详情、创建下一章、删除。
- AI 对话：章节级 SSE 流式对话。
- 小说生成/导入：根据对话生成小说正文，或导入已有小说正文。
- 角色卡：故事级、设定组级、章节级，章节按“章节覆盖 -> 设定组 -> 故事默认”回退。
- 参考图：故事级、设定组级、章节级多图，章节按“章节覆盖 -> 设定组 -> 故事默认”回退。
- 设定组：故事级多设定组 CRUD，章节选择设定组。
- 分镜：生成、读取、更新。
- 漫画生成：SSE 推送分镜、进度、图片、完成、错误。
- 单图重生：更新某一页 prompt 并重新生成该图片。
- 色彩模式：黑白/彩色。
- 图片数量：允许值 `[4, 6, 8, 10, 12, 15, 20]`，已有分镜或图片后不可修改。
- 导入导出：zip 包含 manifest、封面、参考图、设定组、章节、对话、分镜、图片。
- 静态资源：生成图片和缩略图访问。

## 3. 推荐架构

采用“Spring Boot 确定性后端 + AgentScope Harness AI 编排”的混合架构。

### 3.1 分层职责

Spring Boot 负责：

- REST/SSE API。
- 鉴权、参数校验、错误码映射。
- PostgreSQL 数据持久化。
- 文件上传、本地临时图片处理、MinIO 成品图归档、缩略图生成。
- 导入导出 zip。
- 生成任务状态、并发控制、断线重连。
- 调用 AgentScope 编排层或外部模型 API。

AgentScope Harness 负责：

- 长对话上下文管理。
- 小说创作 Agent 的工作区人格与提示词注入。
- 分镜拆分 Agent 的结构化输出约束。
- 图片生成 Agent/工具的任务编排。
- 长会话压缩、会话持久化、必要的子 Agent 分工。

不建议让 Agent 直接承担普通 CRUD。故事、章节、文件、导入导出等确定性业务应由 Spring Service 完成，Agent 只处理语言与生成推理相关工作。

### 3.2 Java 技术选型

推荐依赖：

- Java 21。
- Spring Boot 3.3+。
- Spring WebFlux 或 Spring MVC + `SseEmitter`。
- Spring Data JPA + Hibernate。
- PostgreSQL。
- Flyway 管理数据库迁移。
- agentscope-java `agentscope-harness`。
- Jackson 处理 JSON。
- Apache Commons Compress 或 JDK Zip API 处理作品包。
- Thumbnailator 或 Java ImageIO 处理缩略图。
- WebClient 访问 DeepSeek 与 Image2。
- MinIO Java SDK 访问 MinIO/S3 兼容对象存储。

若需要更自然的流式接口，推荐 WebFlux；若团队更熟悉 MVC，`SseEmitter` 也足够复刻现有行为。

## 4. AgentScope Harness 设计

### 4.1 Harness 使用原则

根据 AgentScope Java Harness 文档，`HarnessAgent` 是 `ReActAgent` 的薄包装，通过 Hook 和 Toolkit 注入工程能力。设计时应利用以下能力：

- Workspace 上下文：用 `AGENTS.md`、`MEMORY.md`、`KNOWLEDGE.md` 固定 Agent 人格、创作规则、分镜规范。
- Session 持久化：用 `RuntimeContext.sessionId` 让同一故事/章节的 AI 会话可恢复。
- Compaction：开启会话压缩，避免长篇创作上下文溢出。
- ToolResultEviction：对大工具结果或大文本进行落盘卸载。
- Subagent：必要时将写作、分镜、图片提示词优化分给不同子 Agent。
- RuntimeContext.userId：作为多用户隔离键，后续扩展账号体系时可复用。

### 4.2 Agent 工作区规划

建议工作区位于 Java 项目运行目录：

```text
.agentscope/workspace/
  AGENTS.md
  KNOWLEDGE.md
  MEMORY.md
  skills/
  subagents/
  ArtVerse/
    prompts/
    sessions/
```

核心文件用途：

- `AGENTS.md`：定义 LoreVista AI 创作助手的整体人格和行为边界。
- `KNOWLEDGE.md`：放置小说章节字数、分镜页格式、漫画风格等稳定规则。
- `MEMORY.md`：长期偏好或跨会话事实，可选启用。
- `subagents/story-writer.md`：小说生成子 Agent。
- `subagents/storyboarder.md`：分镜拆分子 Agent。
- `subagents/image-director.md`：图片 prompt 整理与一致性检查子 Agent。

### 4.3 Agent 划分

后端优先版本建议先实现三个逻辑 Agent，可先由同一个 `HarnessAgent` 包装不同系统提示词实现，后续再拆成声明式 subagent。

#### 4.3.1 ConversationAgent

用途：章节 AI 对话。

输入：章节历史消息、用户新消息、故事/章节上下文、角色卡摘要。

输出：SSE token 流。

要求：

- 保持现有 `/api/chapters/{id}/chat` 行为。
- 用户消息先落库，AI 完整回复完成后落库。
- 用户中断时，已生成内容保存为 assistant 消息并追加 `[已中止]`。
- 失败时回滚本轮用户消息，向 SSE 发送 `error`。

#### 4.3.2 NovelWriterAgent

用途：根据聊天历史生成完整章节小说。

输入：章节对话历史。

输出：章节小说正文。

提示词要求：

- 中文网络小说风格。
- 每话目标 4000-6000 中文字，不低于 3500 字。
- 包含 3-5 个完整场景。
- 强化环境描写、对话、心理活动、微表情、肢体动作。
- 直接输出正文，不输出解释或字数统计。

#### 4.3.3 StoryboardAgent

用途：将章节文本拆成漫画页分镜。

输入：章节聊天/小说内容、角色卡、目标页数。

输出：严格 JSON 数组，长度等于目标页数，每个元素是一页漫画页描述。

约束：

- 每页包含 4-6 个分镜格，最少 4 格。
- 使用 `【第1格】`、`【第2格】` 格式。
- 包含构图、人物、动作、表情、对话气泡、音效字。
- 输出必须可被 JSON 解析，后端负责容错提取和校验。

#### 4.3.4 ImageDirector / ImageGeneration Tool

用途：为每一页调用 Image2 API 生成漫画图。

职责建议拆分：

- Agent 负责整理最终图片 prompt、合并角色卡、参考图说明、完整分镜上下文、黑白/彩色风格要求。
- Java Tool/Service 负责真实 HTTP 调用 Image2、下载或解码图片、校验图片、保存文件。

黑白风格提示词应包含：日式黑白漫画页、竖向多格分镜、清晰边框、中文对话气泡、音效字、高对比光影、精细线条和网点。

彩色风格提示词应包含：日式彩色漫画插画页、赛璐璐上色、高饱和配色、柔和光影、中文对话气泡、音效字。

有参考图时，应强调人物一致性，并优先参考图，不让文字角色卡与视觉参考互相冲突。

### 4.4 AgentScope 集成接口规格

后端不应把 agentscope-java 类型直接暴露给 Controller。建议定义稳定网关接口：

```java
public interface HarnessAgentGateway {
    Flux<String> streamChat(AgentRunRequest request);
    Mono<String> generateText(AgentRunRequest request);
}

public record AgentRunRequest(
    String userId,
    Long storyId,
    Long chapterId,
    AgentTaskType taskType,
    List<AgentMessage> messages,
    Map<String, Object> variables
) {}
```

`RuntimeContextFactory` 负责把业务上下文转换为 Harness RuntimeContext：

```java
RuntimeContext create(String userId, Long storyId, Long chapterId, AgentTaskType taskType) {
    return RuntimeContext.builder()
        .userId(userId)
        .sessionId("story-" + storyId + "-chapter-" + chapterId + "-" + taskType.sessionSuffix())
        .build();
}
```

Agent 服务调用约定：

- `ConversationAgentService.streamChat()`：输入历史消息和本轮用户消息，输出 token 流；Service 层负责落库和回滚。
- `NovelWriterAgentService.generateNovel()`：输入章节聊天历史，输出纯小说正文字符串；不得返回解释、Markdown 标题或字数统计。
- `StoryboardAgentService.generateScenes()`：输入小说正文、聊天摘要、角色卡、目标页数，输出 `List<String>`；内部必须解析并校验 JSON 数组长度。
- `ImagePromptAgentService.buildPrompt()`：输入单页分镜、全章节分镜、角色卡、参考图说明、色彩模式，输出最终 Image2 prompt。

推荐伪代码：

```java
List<String> generateScenes(Long chapterId, String apiKey) {
    Chapter chapter = chapterRepository.getRequired(chapterId);
    String material = chapter.novelContentOrJoinedMessages();
    String profiles = characterProfileService.resolveEffective(chapterId).content();
    String raw = gateway.generateText(new AgentRunRequest(
        currentUserId(), chapter.storyId(), chapter.id(), STORYBOARD,
        List.of(systemPrompt("storyboard"), userPrompt(material)),
        Map.of("pageCount", chapter.imageCount(), "characterProfiles", profiles)
    )).block();
    return sceneParser.parseArray(raw, chapter.imageCount());
}
```

## 5. 后端模块设计

建议包结构：

```text
com.ArtVerse
  config/
  api/
  application/
  domain/
  persistence/
  agents/
  ai/
  storage/
  media/
  exportimport/
  sse/
  common/
```

### 5.1 API 层

Controller 建议拆分：

- `StoryController`
- `ChapterController`
- `ChatController`
- `NovelController`
- `CharacterController`
- `ReferenceImageController`
- `AssetGroupController`
- `StoryboardController`
- `MangaGenerationController`
- `ExportImportController`
- `StaticMediaController`

API 路径应尽量兼容现有前端，例如：

- `POST /api/stories`
- `GET /api/stories`
- `GET /api/stories/{storyId}`
- `PUT /api/stories/{storyId}`
- `DELETE /api/stories/{storyId}`
- `POST /api/stories/{storyId}/upload-cover`
- `GET /api/stories/{storyId}/chapters`
- `POST /api/stories/{storyId}/chapters`
- `GET /api/chapters/{chapterId}`
- `DELETE /api/chapters/{chapterId}`
- `POST /api/chapters/{chapterId}/chat`
- `POST /api/chapters/{chapterId}/generate-novel`
- `POST /api/chapters/{chapterId}/import-novel`
- `POST /api/chapters/{chapterId}/generate-scenes`
- `GET /api/chapters/{chapterId}/scenes`
- `PUT /api/chapters/{chapterId}/scenes`
- `POST /api/chapters/{chapterId}/generate-manga-stream`
- `POST /api/chapters/{chapterId}/regenerate-image/{imageNumber}`

### 5.1.1 前端兼容 DTO 规格

所有 JSON 字段使用 snake_case，时间字段使用 ISO-8601 字符串。错误响应统一为 `{ "detail": "..." }`，缺少外部 API Key 时额外返回 `provider`。

#### Story DTO

`StoryOut`：

```json
{
  "id": 1,
  "title": "故事标题",
  "description": "简介",
  "cover_image": "manga_outputs/covers/cover_1_x.png",
  "ref_image": "manga_outputs/story_1/ref_images/ref_x.png",
  "character_profiles": "角色卡全文",
  "created_at": "2026-05-28T12:00:00Z",
  "chapters": [],
  "asset_groups": [],
  "has_character_profiles": true,
  "has_ref_image": true
}
```

`POST /api/stories` 请求：`{ "title": "...", "description": "..." }`。创建成功返回 `StoryOut`，并自动创建第 1 章。

`PUT /api/stories/{storyId}` 请求可包含 `title`、`description`、`character_profiles` 任意字段；返回更新后的 `StoryOut`。

#### Chapter DTO

`ChapterOut`：

```json
{
  "id": 10,
  "story_id": 1,
  "chapter_number": 1,
  "novel_content": "正文",
  "content_source": "chat",
  "scenes_text": "[\"第一页分镜\"]",
  "character_profiles": "章节角色卡",
  "asset_group_id": 2,
  "ref_image": null,
  "color_mode": "bw",
  "image_count": 10,
  "created_at": "2026-05-28T12:00:00Z",
  "messages": [],
  "images": []
}
```

`MangaImageOut`：

```json
{
  "id": 99,
  "chapter_id": 10,
  "image_number": 1,
  "image_path": "manga_outputs/chapter_10/panel_01_x.png",
  "prompt": "最终图片 prompt",
  "created_at": "2026-05-28T12:00:00Z"
}
```

对象存储字段不默认暴露给前端，前端继续通过 `image_path` 拼接 `/static/manga/...` 或 `/static/manga/_thumb/...` 访问。

#### Chat 与 Novel DTO

`POST /api/chapters/{chapterId}/chat` 请求：

```json
{ "message": "用户输入" }
```

`POST /api/chapters/{chapterId}/generate-novel` 返回：

```json
{ "novel_content": "生成后的章节正文" }
```

`POST /api/chapters/{chapterId}/import-novel` 请求：

```json
{ "content": "用户粘贴的小说正文" }
```

成功返回更新后的 `ChapterOut`。

#### 分镜与漫画 DTO

`POST /api/chapters/{chapterId}/generate-scenes` 返回：

```json
{ "scenes": ["第一页分镜", "第二页分镜"] }
```

`PUT /api/chapters/{chapterId}/scenes` 请求体必须是字符串数组：

```json
["第一页分镜", "第二页分镜"]
```

`POST /api/chapters/{chapterId}/regenerate-image/{imageNumber}` 请求：

```json
{ "prompt": "新的单页分镜或图片 prompt" }
```

成功返回 `MangaImageOut`。

#### 角色卡、参考图、设定组 DTO

角色卡读取响应：

```json
{ "content": "角色卡内容", "source": "chapter" }
```

`source` 枚举：`chapter`、`asset_group`、`story`、`none`。

参考图响应：

```json
{
  "images": [
    { "path": "manga_outputs/story_1/ref_images/ref_x.png", "url": "/static/manga/story_1/ref_images/ref_x.png" }
  ],
  "source": "story"
}
```

设定组响应：

```json
{
  "id": 2,
  "story_id": 1,
  "name": "主角团",
  "character_profiles": "...",
  "ref_images": [],
  "created_at": "2026-05-28T12:00:00Z"
}
```

### 5.2 Application Service 层

核心服务：

- `StoryService`：故事 CRUD、封面管理、删除级联文件。
- `ChapterService`：章节创建、删除、章节号唯一性。
- `ChatService`：对话落库、SSE 流式代理、异常回滚。
- `NovelService`：AI 小说生成、粘贴导入互斥规则。
- `CharacterProfileService`：角色卡读取、保存、回退来源判断。
- `ReferenceImageService`：参考图上传、删除、有效参考图计算。
- `AssetGroupService`：故事设定组 CRUD 与章节选择。
- `SceneService`：分镜生成、解析、保存、读取、更新。
- `MangaGenerationService`：漫画生成任务、SSE 订阅、单图重生。
- `ExportImportService`：作品 zip manifest 导出导入。
- `MediaStorageService`：文件路径、图片校验、缩略图、静态资源。
- `ObjectStorageService`：成品图对象存储接口，默认实现为 `MinioStorageService`。

### 5.3 Agent 服务层

`agents/` 包建议提供：

- `LoreVistaHarnessFactory`：创建和缓存 HarnessAgent。
- `RuntimeContextFactory`：按 userId/storyId/chapterId 生成 sessionId。
- `ConversationAgentService`
- `NovelWriterAgentService`
- `StoryboardAgentService`
- `ImagePromptAgentService`

SessionId 建议：

```text
story-{storyId}-chapter-{chapterId}-chat
story-{storyId}-chapter-{chapterId}-novel
story-{storyId}-chapter-{chapterId}-storyboard
story-{storyId}-chapter-{chapterId}-image
```

这样同一章节不同任务的上下文互不污染，同时可跨进程恢复。

### 5.4 服务与客户端接口契约

核心接口应先定义再实现，方便测试中 mock 外部依赖：

```java
public interface DeepSeekClient {
    Flux<String> streamChat(List<AiMessage> messages, String apiKey);
    Mono<String> generateText(List<AiMessage> messages, String apiKey);
}

public interface Image2Client {
    Mono<GeneratedImage> generate(ImageGenerationRequest request, String apiKey);
}

public interface ObjectStorageService {
    StoredObject putPng(String objectKey, Path localFile, String contentType);
    InputStream get(String bucket, String objectKey);
    Optional<URI> publicOrPresignedUrl(String bucket, String objectKey, Duration ttl);
    void deleteBestEffort(String bucket, String objectKey);
}
```

`GeneratedImage` 必须包含本地临时文件路径、content type、字节数；`StoredObject` 必须包含 bucket、objectKey、contentType、sizeBytes。

Repository 约定：

- 查询单个故事、章节、设定组时提供 `getRequired(id)` 风格方法，不存在统一抛出 404 业务异常。
- 删除故事应通过数据库级联删除记录，并由 Service 收集相关本地文件和 MinIO object key 做 best-effort 清理。
- 创建下一章必须依赖唯一约束处理并发，最多重试 3 次。

事务边界：

- 数据库写入和外部对象删除不能放在同一个原子事务中假装强一致。
- 新图片生成、PNG 校验、MinIO 上传成功后，再开启事务写入 `MangaImage`。
- 旧图片或旧对象只在新记录提交成功后删除。

## 6. 数据库设计

使用 Flyway 创建表。

### 6.1 stories

字段：

- `id BIGSERIAL PRIMARY KEY`
- `title VARCHAR(255) NOT NULL`
- `description TEXT`
- `cover_image VARCHAR(500)`
- `ref_image VARCHAR(500)`：保留兼容旧单图字段，可逐步废弃。
- `character_profiles TEXT`
- `created_at TIMESTAMP NOT NULL DEFAULT now()`

### 6.2 story_asset_groups

字段：

- `id BIGSERIAL PRIMARY KEY`
- `story_id BIGINT NOT NULL REFERENCES stories(id)`
- `name VARCHAR(120) NOT NULL`
- `character_profiles TEXT`
- `created_at TIMESTAMP NOT NULL DEFAULT now()`

### 6.3 chapters

字段：

- `id BIGSERIAL PRIMARY KEY`
- `story_id BIGINT NOT NULL REFERENCES stories(id)`
- `chapter_number INT NOT NULL`
- `novel_content TEXT`
- `content_source VARCHAR(20)`：`chat` 或 `import`。
- `scenes_text TEXT`
- `character_profiles TEXT`
- `asset_group_id BIGINT REFERENCES story_asset_groups(id)`
- `ref_image VARCHAR(500)`：兼容旧单图字段。
- `color_mode VARCHAR(20)`：`bw` 或 `color`。
- `image_count INT`
- `created_at TIMESTAMP NOT NULL DEFAULT now()`

约束：

- `UNIQUE(story_id, chapter_number)`。

### 6.4 chat_messages

字段：

- `id BIGSERIAL PRIMARY KEY`
- `chapter_id BIGINT NOT NULL REFERENCES chapters(id)`
- `role VARCHAR(20) NOT NULL`
- `content TEXT NOT NULL`
- `created_at TIMESTAMP NOT NULL DEFAULT now()`

### 6.5 manga_images

字段：

- `id BIGSERIAL PRIMARY KEY`
- `chapter_id BIGINT NOT NULL REFERENCES chapters(id)`
- `image_number INT NOT NULL`
- `image_path VARCHAR(500) NOT NULL`
- `storage_provider VARCHAR(20) NOT NULL DEFAULT 'local'`：`local` 或 `minio`。
- `bucket VARCHAR(120)`
- `object_key VARCHAR(700)`
- `content_type VARCHAR(100)`
- `size_bytes BIGINT`
- `prompt TEXT`
- `created_at TIMESTAMP NOT NULL DEFAULT now()`

约束：

- `UNIQUE(chapter_id, image_number)`。

### 6.6 Flyway V1 SQL 草案

实现时可按以下 SQL 拆成 `V1__init_schema.sql`，字段名保持 snake_case：

```sql
CREATE TABLE stories (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  cover_image VARCHAR(500),
  ref_image VARCHAR(500),
  character_profiles TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE story_asset_groups (
  id BIGSERIAL PRIMARY KEY,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  character_profiles TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_groups_story_id ON story_asset_groups(story_id);

CREATE TABLE chapters (
  id BIGSERIAL PRIMARY KEY,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  chapter_number INT NOT NULL,
  novel_content TEXT,
  content_source VARCHAR(20),
  scenes_text TEXT,
  character_profiles TEXT,
  asset_group_id BIGINT REFERENCES story_asset_groups(id) ON DELETE SET NULL,
  ref_image VARCHAR(500),
  color_mode VARCHAR(20) NOT NULL DEFAULT 'bw',
  image_count INT NOT NULL DEFAULT 10,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_chapters_story_number UNIQUE(story_id, chapter_number),
  CONSTRAINT ck_chapters_content_source CHECK (content_source IS NULL OR content_source IN ('chat', 'import')),
  CONSTRAINT ck_chapters_color_mode CHECK (color_mode IN ('bw', 'color')),
  CONSTRAINT ck_chapters_image_count CHECK (image_count IN (4, 6, 8, 10, 12, 15, 20))
);
CREATE INDEX idx_chapters_story_id ON chapters(story_id);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_chat_messages_role CHECK (role IN ('user', 'assistant', 'system'))
);
CREATE INDEX idx_chat_messages_chapter_id_created_at ON chat_messages(chapter_id, created_at);

CREATE TABLE manga_images (
  id BIGSERIAL PRIMARY KEY,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  image_number INT NOT NULL,
  image_path VARCHAR(500) NOT NULL,
  storage_provider VARCHAR(20) NOT NULL DEFAULT 'local',
  bucket VARCHAR(120),
  object_key VARCHAR(700),
  content_type VARCHAR(100),
  size_bytes BIGINT,
  prompt TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_manga_images_chapter_number UNIQUE(chapter_id, image_number),
  CONSTRAINT ck_manga_images_number CHECK (image_number > 0),
  CONSTRAINT ck_manga_images_storage_provider CHECK (storage_provider IN ('local', 'minio'))
);
CREATE INDEX idx_manga_images_chapter_id ON manga_images(chapter_id);
CREATE INDEX idx_manga_images_object_key ON manga_images(bucket, object_key);
```

如果后续需要多参考图元数据表，可在 V2 增加 `reference_images(scope_type, scope_id, path, sort_order)`；MVP 可先按本地目录扫描兼容现有行为。

## 7. 文件与对象存储设计

本地根目录仍建议可配置，用于上传校验、图片转码、兼容文本文件和缩略图缓存：

```properties
ArtVerse.storage.root=./manga_outputs
```

目录结构：

```text
manga_outputs/
  covers/
    cover_{storyId}_{uuid}.png
  story_{storyId}/
    ref_images/
      ref_{uuid}.png
  asset_groups/
    group_{groupId}/
      ref_images/
        ref_{uuid}.png
  chapter_{chapterId}/
    scenes.txt
    characters.txt
    color_mode.txt
    image_count.txt
    ref_images/
      ref_{uuid}.png
    panel_01_{uuid}.png
  .thumbs/
    w720/...
```

新实现应以数据库字段为准，文本文件只用于兼容与人工排查。可以保留写入 `scenes.txt`、`characters.txt`、`color_mode.txt`、`image_count.txt`，但读取优先级应为数据库优先、文件兜底。

漫画成品图生成后应上传到 MinIO，MinIO 中的对象 key 建议保持稳定、可追踪：

```text
stories/{storyId}/chapters/{chapterId}/panels/panel_{imageNumber}_{uuid}.png
```

`manga_images.image_path` 保留后端相对路径用于兼容现有前端，例如：

```text
manga_outputs/chapter_12/panel_01_abcd1234.png
```

同时在 `storage_provider`、`bucket`、`object_key` 等字段记录真实对象存储位置。后端读取图片时优先使用 MinIO 字段；若旧数据没有对象存储字段，再回退到本地 `image_path`。

所有对外路径必须是后端相对路径，如：

```text
manga_outputs/chapter_12/panel_01_abcd1234.png
```

静态访问时再映射到：

```text
/static/manga/chapter_12/panel_01_abcd1234.png
/static/manga/_thumb/chapter_12/panel_01_abcd1234.png?w=720
```

为保持前端兼容，`/static/manga/...` 继续作为统一访问入口：

- 如果记录指向 MinIO，后端可代理读取对象并返回图片流。
- 如果配置了公开域名，也可返回或重定向到预签名 URL。
- 缩略图优先按 `object_key` 生成并缓存到本地 `.thumbs/`，源文件不存在时从 MinIO 拉取。

路径安全要求：

- 禁止绝对路径。
- 禁止 `..`。
- 只允许 `.png`、`.jpg`、`.jpeg`、`.webp`。
- 删除文件时只允许删除 storage root 内的文件。
- MinIO object key 禁止以 `/` 开头，禁止包含 `..`、反斜杠和控制字符。
- Bucket 名称只能来自后端配置，不能由请求参数传入。

## 8. 关键业务流程

### 8.1 创建故事

流程：

1. 创建 `Story`。
2. 自动创建第 1 章 `Chapter(chapterNumber=1)`。
3. 返回故事对象。

### 8.2 创建下一章

流程：

1. 查询当前故事最大 `chapter_number`。
2. 新章编号为最大值 + 1。
3. 写入时依赖唯一约束处理并发冲突。
4. 冲突时最多重试 3 次。

### 8.3 AI 对话 SSE

流程：

1. 校验章节存在。
2. 若 `content_source=import`，返回 409。
3. 保存用户消息，并将章节 `content_source` 设为 `chat`。
4. 读取同一故事中小于等于当前章节的所有消息，构造上下文。
5. 调用 `ConversationAgentService.streamChat()`。
6. 对每个 token 发送 SSE：`event: token`。
7. 完成后保存 assistant 消息，发送 `event: done`。
8. 中断时保存已生成内容并标注 `[已中止]`。
9. 错误时删除本轮用户消息，发送 `event: error`。

### 8.4 导入小说正文

互斥规则：

- 已经有 AI 对话或 `content_source=chat` 时，不允许导入。
- 已经有漫画图片时，不允许导入。
- 内容不能为空。
- 默认最大 50000 字，可配置。

流程：

1. 清除本章已有分镜/图片状态。
2. 删除本章聊天消息。
3. 新增一条 `role=user` 的 ChatMessage 保存导入正文。
4. 设置 `novel_content` 和 `content_source=import`。

### 8.5 生成分镜

流程：

1. 校验章节存在且有消息。
2. 读取目标图片数量，默认 10。
3. 读取有效角色卡。
4. 调用 `StoryboardAgent`。
5. 后端解析 JSON 数组并校验长度等于目标页数。
6. 保存到 `chapters.scenes_text`，并写入 `chapter_{id}/scenes.txt`。
7. 返回 `{ scenes: [...] }`。

解析容错：

- 支持模型返回 markdown code fence。
- 支持从正文中提取第一个 JSON array。
- 如果仍无法解析，返回 502，提示“AI returned invalid scene JSON”。

### 8.6 更新分镜

规则：

- 请求体必须是字符串数组。
- 数组长度必须等于当前 image_count。
- 每一项不能为空字符串。

保存：更新 `scenes_text` 与 `scenes.txt`。

### 8.7 漫画生成 SSE

流程：

1. 校验章节存在。
2. 读取 image_count 与已保存分镜。
3. 分镜为空返回 400。
4. 分镜数量不等于 image_count 返回 400。
5. 若当前章节已有活跃生成任务，则直接订阅已有任务事件。
6. 若无活跃任务，创建 `MangaGenerationJob`。
7. 任务启动后先发送 `event: scenes`。
8. 对每一页：
   - 若图片已存在，直接发送 `event: image`。
   - 否则发送 `event: progress`。
   - 计算有效参考图。
   - 调用 Image2 生成图片。
   - 校验并转为 PNG 后上传到 MinIO。
   - 保存 `MangaImage` 的兼容路径与 MinIO 对象字段。
   - 发送 `event: image`。
9. 全部完成发送 `event: done`。
10. 任一图片失败，发送 `event: error` 并结束任务。

任务状态建议：

- 单机版本可使用内存 Map：`Map<Long, MangaGenerationJob>`。
- 为支持重启恢复，后续可增加 `generation_jobs` 表。
- SSE 事件保留最近 300 条，断线重连时先回放历史事件。

### 8.8 单图重新生成

流程：

1. 校验 `imageNumber` 在 `1..image_count`。
2. prompt 不能为空。
3. 如果完整分镜存在，则替换对应页分镜并保存。
4. 生成新图片。
5. 校验并转为 PNG 后上传到 MinIO。
6. 数据库中已有图片则更新路径、对象字段和 prompt，否则新增。
7. 提交成功后删除旧图片文件或旧 MinIO 对象。

注意：旧图应在新图生成成功、MinIO 上传成功且数据库提交成功后再删除；删除旧对象失败只记录告警，不回滚已成功的重生结果。

### 8.9 角色卡回退

读取章节有效角色卡的顺序：

1. `Chapter.character_profiles`。
2. 兼容文件 `chapter_{id}/characters.txt`。
3. 当前章节选择的 `StoryAssetGroup.character_profiles`。
4. `Story.character_profiles`。
5. 空字符串。

返回给前端时应同时返回 `source`：`chapter`、`asset_group`、`story`、`none`。

### 8.10 参考图回退

读取章节有效参考图的顺序：

1. 章节级 `chapter_{id}/ref_images`。
2. 章节旧字段 `Chapter.ref_image`。
3. 当前章节选择的设定组参考图。
4. 故事级 `story_{id}/ref_images`。
5. 故事旧字段 `Story.ref_image`。
6. 空列表。

新增参考图时：

- Base64 输入先解码并校验图片。
- 统一转为 PNG 存储。
- 单层最多 4 张，可配置。

### 8.11 SSE 事件格式规格

所有 SSE 响应使用 `Content-Type: text/event-stream; charset=utf-8`，事件 data 使用单行 JSON；token 事件可直接发送字符串以兼容现有前端。

#### Chat SSE

成功序列：

```text
event: token
data: 这是

event: token
data: 一段回复

event: done
data: {"content":"这是一段回复"}
```

错误序列：

```text
event: error
data: {"detail":"DeepSeek API Key is missing. Please set it in the frontend settings or backend .env.","provider":"DeepSeek"}
```

客户端断开时，服务端保存已生成 assistant 内容并追加 `\n\n[已中止]`；如果尚未生成任何 token，可只保留用户消息并等待下次刷新。

#### Manga SSE

成功序列：

```text
event: scenes
data: {"scenes":["第一页分镜","第二页分镜"]}

event: progress
data: {"image_number":1,"total":10,"message":"正在生成第 1/10 页"}

event: image
data: {"image_number":1,"image_path":"manga_outputs/chapter_10/panel_01_x.png","url":"/static/manga/chapter_10/panel_01_x.png","thumb_url":"/static/manga/_thumb/chapter_10/panel_01_x.png?w=720"}

event: done
data: {"images":10}
```

失败序列：

```text
event: error
data: {"detail":"Image2 generation failed at page 3"}
```

重连规则：活跃 `MangaGenerationJob` 保存最近 300 条事件；新订阅者连接后先回放历史事件，再接收新事件。

## 9. 外部 API 适配

### 9.1 DeepSeek

配置：

```properties
ArtVerse.deepseek.base-url=https://api.deepseek.com
ArtVerse.deepseek.model=deepseek-v4-flash
ArtVerse.deepseek.api-key=
```

同时支持从请求头透传用户 Key：

- `X-DeepSeek-API-Key`

优先级：请求头 > 后端配置。

缺少 Key 时返回 400：

```json
{ "detail": "DeepSeek API Key is missing. Please set it in the frontend settings or backend .env.", "provider": "DeepSeek" }
```

### 9.2 Image2

配置：

```properties
ArtVerse.image.base-url=https://api.duojie.games/v1
ArtVerse.image.model=gpt-image-2
ArtVerse.image.size=1024x1536
ArtVerse.image.api-key=
```

请求头：

- `X-Image-API-Key`

优先级：请求头 > 后端配置。

调用规则：

- 无参考图：调用 `/images/generations` JSON 接口。
- 有参考图：调用 `/images/edits` multipart 接口。
- 单参考图字段名用 `image`。
- 多参考图字段名用 `image[]`。
- 支持返回 `b64_json` 或 `url`。
- 返回图片必须校验并转为 PNG。
- 单图最多重试 3 次，间隔 5 秒。
- 读超时建议 600 秒，生成图片可能排队。

### 9.3 MinIO

配置：

```properties
ArtVerse.minio.endpoint=http://localhost:9000
ArtVerse.minio.bucket=artverse-manga
ArtVerse.minio.region=us-east-1
ArtVerse.minio.access-key=
ArtVerse.minio.secret-key=
ArtVerse.minio.secure=false
ArtVerse.minio.public-base-url=
ArtVerse.minio.presigned-url-expire-seconds=3600
```

调用规则：

- MinIO 只作为漫画成品图的权威对象存储；封面、参考图和兼容文本文件可继续先按本地文件设计实现。
- 上传前必须完成图片真实格式校验、PNG 转换和大小限制检查。
- object key 由后端生成，格式建议为 `stories/{storyId}/chapters/{chapterId}/panels/panel_{imageNumber}_{uuid}.png`。
- 上传成功后再写入或更新 `MangaImage` 记录。
- 下载图片时优先按 `bucket + object_key` 从 MinIO 获取；旧数据缺少对象字段时回退到本地路径。
- 删除故事、章节或单图替换时，对象删除采用 best-effort，失败记录告警并留给后台清理任务重试。

## 10. 错误处理

统一错误格式建议：

```json
{ "detail": "错误说明" }
```

常见状态码：

- 400：参数错误、内容为空、分镜数量不匹配、缺少 API Key。
- 401：后端启用 API_TOKEN 且请求缺失或错误。
- 404：故事/章节/设定组/图片不存在。
- 409：业务冲突，例如导入和聊天互斥、已有分镜后修改图片数量、文件被占用。
- 413：上传图片、导入小说、导入 zip 超限。
- 415：不支持的图片格式。
- 499：客户端取消生成任务，可选。
- 502：AI 返回不可解析结构、Image2 或 MinIO 上游服务失败。

## 11. 配置项

建议配置：

```properties
server.port=8000
spring.datasource.url=jdbc:postgresql://localhost:5432/manga_novel
spring.datasource.username=postgres
spring.datasource.password=postgres

ArtVerse.cors-origins=http://localhost:5173,http://127.0.0.1:5173
ArtVerse.api-token=
ArtVerse.storage.root=./manga_outputs

ArtVerse.upload.max-image-bytes=10485760
ArtVerse.import.max-zip-bytes=524288000
ArtVerse.import.max-novel-chars=50000
ArtVerse.character.max-chars=20000
ArtVerse.ref.max-images-per-level=4

ArtVerse.manga.default-image-count=10
ArtVerse.manga.allowed-image-counts=4,6,8,10,12,15,20

ArtVerse.deepseek.base-url=https://api.deepseek.com
ArtVerse.deepseek.model=deepseek-v4-flash
ArtVerse.deepseek.api-key=

ArtVerse.image.base-url=https://api.duojie.games/v1
ArtVerse.image.model=gpt-image-2
ArtVerse.image.size=1024x1536
ArtVerse.image.api-key=

ArtVerse.minio.endpoint=http://localhost:9000
ArtVerse.minio.bucket=artverse-manga
ArtVerse.minio.region=us-east-1
ArtVerse.minio.access-key=
ArtVerse.minio.secret-key=
ArtVerse.minio.secure=false
ArtVerse.minio.public-base-url=
ArtVerse.minio.presigned-url-expire-seconds=3600

ArtVerse.agentscope.workspace=.agentscope/workspace
ArtVerse.agentscope.compaction.trigger-messages=30
ArtVerse.agentscope.compaction.keep-messages=10
```

## 12. 安全与边界

必须实现：

- API Token 可选鉴权，与现有 `X-API-Token`/Bearer 兼容。
- CORS 白名单可配置。
- 上传图片大小限制。
- Base64 严格解码。
- 图片真实格式校验，不只信扩展名。
- Zip Slip 防护：导入 zip 成员名禁止绝对路径和 `..`。
- Zip 解压总大小限制。
- 静态文件读取必须限制在 storage root 内。
- 删除文件必须限制在 storage root 内。
- MinIO object key 必须由服务端生成并做规范化校验。
- MinIO access key、secret key 和预签名 URL 不写入日志。
- 外部 API Key 不写入日志。
- SSE 连接断开后及时清理订阅者。

## 13. 导入导出设计

格式：

```json
{
  "format": "ArtVerse.story.export",
  "version": 2,
  "exported_at": "ISO-8601",
  "story": {
    "title": "...",
    "description": "...",
    "character_profiles": "...",
    "cover_image": "assets/cover.png",
    "ref_images": [],
    "asset_groups": [],
    "chapters": []
  }
}
```

导出：

- 使用 zip 存储 `manifest.json`。
- 图片按稳定路径放入 `assets/`。
- 若图片存储在 MinIO，导出时通过 `ObjectStorageService` 读取对象流写入 zip。
- 章节按 `chapter_number` 排序。
- 设定组使用导出内 `group_key`，导入后重新映射为新数据库 id。

导入：

- 校验 format 与 version。
- version 大于当前支持版本时拒绝。
- 校验章节号正数且不重复。
- 导入图片先写入本地临时文件并校验，再上传到 MinIO，最后写入新的对象字段。
- 导入失败时回滚数据库，并删除本次已写入文件和已上传对象。
- 无章节时自动创建第 1 章。

## 14. 测试策略

### 14.1 单元测试

覆盖：

- 分镜 JSON 提取与校验。
- 文件路径安全校验。
- MinIO object key 生成与安全校验。
- 参考图回退顺序。
- 角色卡回退顺序。
- 图片数量修改规则。
- Zip manifest 校验。

### 14.2 集成测试

使用 Testcontainers PostgreSQL 覆盖：

- 故事创建自动建第 1 章。
- 并发创建下一章不重复。
- 删除故事级联删除章节、消息、图片记录。
- 导入小说与 AI 对话互斥。
- 导出后再导入能恢复故事结构。

### 14.3 AI 服务测试

外部 API 应通过接口抽象并 mock：

- `DeepSeekClient`
- `Image2Client`
- `HarnessAgentGateway`

测试不直接调用真实付费 API。真实 API 只用于手动验收。

### 14.4 SSE 测试

覆盖事件序列：

- chat：`token* -> done`。
- chat error：`error`，并回滚用户消息。
- manga：`scenes -> progress/image* -> done`。
- manga failure：生成到某页失败后发送 `error`。
- manga reconnect：活跃任务可重新订阅并回放历史事件。

### 14.5 对象存储测试

覆盖：

- 漫画图片生成后上传 MinIO 成功，再保存 `MangaImage`。
- MinIO 上传失败时发送 SSE `error`，不写入成功图片记录。
- 单图重生成功后删除旧对象失败不会回滚新图。
- 导出 zip 能从 MinIO 读取图片对象。
- 静态图片接口能按 `object_key` 代理或生成可访问地址。

## 15. 实施任务包

以下任务包可分配给其他模型顺序实现。每个任务完成后都应能独立编译或通过对应测试。

### 15.1 任务 A：项目骨架与基础设施

输入：本文档第 3、5、10、11、12 节。

产物：

- Spring Boot 3.3+ Java 21 项目。
- 配置属性类：`ArtVerseProperties`、`DeepSeekProperties`、`ImageProperties`、`MinioProperties`。
- CORS、API Token Filter、统一异常处理。
- 基础响应错误格式 `{ "detail": "..." }`。

验收：应用可启动，未配置 token 时放行，配置 token 时兼容 `X-API-Token` 与 `Authorization: Bearer`。

### 15.2 任务 B：数据库与领域模型

输入：第 6 节和 Flyway SQL 草案。

产物：

- Flyway `V1__init_schema.sql`。
- JPA Entity、Repository。
- 基础枚举：`ContentSource`、`ColorMode`、`StorageProvider`。

验收：Testcontainers PostgreSQL 可迁移成功；唯一约束和检查约束生效。

### 15.3 任务 C：故事与章节 CRUD

输入：第 5.1.1、8.1、8.2 节。

产物：`StoryController`、`ChapterController`、`StoryService`、`ChapterService`。

验收：创建故事自动创建第 1 章；并发创建下一章不会产生重复章节号。

### 15.4 任务 D：媒体、本地文件与 MinIO

输入：第 7、9.3、12、14.5 节。

产物：`MediaStorageService`、`ObjectStorageService`、`MinioStorageService`、`StaticMediaController`。

验收：图片路径和 object key 安全校验通过；可上传 PNG 到 MinIO；`/static/manga/...` 可读取 MinIO 图片或本地旧图。

### 15.5 任务 E：角色卡、参考图、设定组

输入：第 8.9、8.10 节。

产物：`CharacterProfileService`、`ReferenceImageService`、`AssetGroupService` 及对应 Controller。

验收：章节覆盖、设定组、故事默认、空值的回退顺序和 `source` 返回正确。

### 15.6 任务 F：AI 客户端与 AgentScope 网关

输入：第 4、5.4、9.1、9.2 节。

产物：`DeepSeekClient`、`Image2Client`、`HarnessAgentGateway`、`RuntimeContextFactory`。

验收：外部客户端可 mock；缺少 API Key 返回指定 400；Agent sessionId 格式符合文档。

### 15.7 任务 G：聊天、小说生成、导入与分镜

输入：第 8.3、8.4、8.5、8.6、8.11 节。

产物：`ChatService`、`NovelService`、`SceneService` 和相关 SSE/REST Controller。

验收：chat SSE 事件格式正确；导入和聊天互斥；分镜 JSON 容错解析和长度校验正确。

### 15.8 任务 H：漫画生成、重生、导入导出

输入：第 8.7、8.8、13、14.4、14.5 节。

产物：`MangaGenerationService`、`MangaGenerationJob`、`ExportImportService`。

验收：漫画生成事件序列正确；生成后上传 MinIO；重生成功后再删除旧对象；导出导入可恢复作品。

## 16. MVP 实现顺序

推荐按以下顺序实现，便于其他模型拆任务：

1. Spring Boot 项目骨架、配置、统一错误、CORS、API Token。
2. Flyway 表结构与 JPA 实体。
3. 故事/章节 CRUD。
4. 文件存储、MinIO 对象存储、静态图片、缩略图。
5. 角色卡、参考图、设定组。
6. DeepSeekClient、Image2Client 抽象。
7. AgentScope Harness 初始化与工作区提示词。
8. AI 对话 SSE。
9. 小说导入与小说生成。
10. 分镜生成、读取、更新。
11. 漫画生成任务与 SSE。
12. 单图重新生成。
13. 导入导出 zip。
14. 集成测试与前端联调。

## 17. 验收标准

后端优先复刻完成时应满足：

- 当前 LoreVista 前端只改 API base 或不改即可跑通主要流程。
- 能创建故事、章节，保存并恢复会话。
- 能配置 DeepSeek 与 Image2 Key。
- AI 对话可以 SSE 流式显示。
- 可导入小说正文并生成分镜。
- 可生成指定数量漫画图片，上传到 MinIO，并实时推送进度。
- 角色卡和参考图能按故事/设定组/章节回退。
- 黑白/彩色模式与图片数量规则生效。
- 单图重生不破坏旧图，成功后替换并清理旧对象。
- 导出 zip 后可重新导入并恢复作品。
- 关键路径有自动化测试。

## 18. 参考资料

- AgentScope Java Harness 概览：https://java.agentscope.io/zh/harness/overview.html
- AgentScope Java Harness 架构：https://java.agentscope.io/zh/harness/architecture.html
- AgentScope Java Harness 工具：https://java.agentscope.io/zh/harness/tool.html
- AgentScope Java Harness 子 Agent：https://java.agentscope.io/zh/harness/subagent.html
