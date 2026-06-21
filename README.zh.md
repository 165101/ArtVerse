# ArtVerse

ArtVerse 是一个全栈 AI 漫画创作工作台。
它由 Spring Boot 后端、Vite React 前端，以及基于 AgentScope Harness 的漫画导演智能体组成，帮助用户按章节检查内容、重写分镜，并通过人机协同继续创作。

## 项目能力

- 管理故事与章节
- 与章节级智能体对话
- 生成和重写分镜
- 使用 AG-UI / SSE 观察智能体运行过程
- 支持智能体运行中的人机协同决策
- 按用户、故事、章节、对话隔离智能体会话

## 技术栈

- 后端：Java 21、Spring Boot、JPA、Flyway
- 前端：React、TypeScript、Vite、Tailwind CSS
- 智能体运行时：AgentScope Harness
- 存储：PostgreSQL、Redis、MinIO

## 目录说明

- `ArtVerse/` - 后端服务
- `frontend/` - Web 前端
- `ArtVerse/docs/knowledge/` - 业务知识与智能体流程说明
- `.agentscope/` - 本地 AgentScope 工作区数据

## 快速启动

### 后端

```bash
cd ArtVerse
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 常用命令

```bash
# 后端
cd ArtVerse
mvn -q -DskipTests compile
mvn test

# 前端
cd frontend
npm run build
npm run lint
```

## 智能体说明

- 漫画智能体按章节和对话隔离。
- 新对话会创建新的 AgentScope session。
- 前端使用 AG-UI 展示智能体实时进度。
- 人机协同问题通过 `ask_user` 工具处理。

## 文档

- 业务知识索引：`ArtVerse/docs/knowledge/INDEX.md`
- 漫画智能体技能：`ArtVerse/docs/knowledge/modules/manga-agent/SKILL.md`
- 漫画智能体流程：`ArtVerse/docs/knowledge/modules/manga-agent/flow.md`
