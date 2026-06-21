# ArtVerse

ArtVerse is a full-stack AI manga creation workspace.
It combines a Spring Boot backend, a Vite React frontend, and an AgentScope-based manga director agent that helps users inspect chapters, rewrite storyboard scenes, and continue work with human-in-the-loop decisions.

## What this project does

- Manage stories and chapters
- Chat with a chapter-scoped manga agent
- Generate and rewrite storyboard scenes
- Keep agent runs observable with AG-UI / SSE
- Support human-in-the-loop decisions during agent execution
- Isolate agent sessions by user, story, chapter, and conversation

## Tech Stack

- Backend: Java 21, Spring Boot, JPA, Flyway
- Frontend: React, TypeScript, Vite, Tailwind CSS
- Agent runtime: AgentScope Harness
- Storage: PostgreSQL, Redis, MinIO

## Project Structure

- `ArtVerse/` - backend service
- `frontend/` - web client
- `ArtVerse/docs/knowledge/` - business knowledge and agent flow notes
- `.agentscope/` - local AgentScope workspace data

## Quick Start

### Backend

```bash
cd ArtVerse
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Useful Commands

```bash
# Backend
cd ArtVerse
mvn -q -DskipTests compile
mvn test

# Frontend
cd frontend
npm run build
npm run lint
```

## Agent Notes

- Manga Agent is chapter-scoped and conversation-scoped.
- New conversations get a fresh AgentScope session.
- The frontend uses AG-UI for live agent progress.
- Human-in-the-loop questions use the `ask_user` tool.

## Documentation

- Business knowledge index: `ArtVerse/docs/knowledge/INDEX.md`
- Manga agent skill: `ArtVerse/docs/knowledge/modules/manga-agent/SKILL.md`
- Manga agent flow: `ArtVerse/docs/knowledge/modules/manga-agent/flow.md`
