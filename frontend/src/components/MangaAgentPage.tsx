import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import { Bot, BookOpenText, Loader2, Send, Sparkles } from 'lucide-react';
import {
  type AgentRunTimelineEvent,
  type AgentUserInputRequest,
  getMangaAgentRunState,
  getMangaAgentMessages,
  getOpenMangaAgentRun,
  listChapters,
  listStories,
  runMangaAgentStream,
  resumeMangaAgentRunStream,
  type Chapter,
  type MangaAgentMessage,
  type MangaAgentRunEvent,
  type MangaAgentRunSnapshot,
  type Story,
} from '../api';

interface Message {
  role: 'user' | 'assistant' | 'system';
  content: string;
  requestId?: string;
}

type AgentStreamResult = {
  reply: string;
  requestId?: string;
  request_id?: string;
  waiting?: boolean;
};

const STARTER_PROMPTS = [
  '帮我检查这一话的漫画进度，并告诉我下一步做什么',
  '根据当前内容，先帮我生成这一话的分镜',
  '帮我看看分镜是否还需要润色，再给出修改建议',
];

function toMessages(items: MangaAgentMessage[]): Message[] {
  return items
    .flatMap((item) => {
      if (item.role !== 'user' && item.role !== 'assistant' && item.role !== 'system') {
        return [];
      }
      const content = item.role === 'system' ? formatSystemMessage(item.content) : item.content;
      if (!content) {
        return [];
      }
      return [{
        role: item.role,
        content,
        requestId: item.requestId ?? item.request_id,
      }];
    });
}

function formatSystemMessage(content: string): string | null {
  const type = content.match(/type=([^,}]+)/)?.[1]?.trim();
  const message = content.match(/message=(.*?)(, [a-zA-Z_]+=|}$)/)?.[1]?.trim();
  if (type === 'agent_run_degraded_after_tool_success') {
    return null;
  }
  if (type === 'agent_run_failed') {
    return `系统提示：智能体本次响应失败。${message ? `原因：${message}` : '请稍后重试。'}`;
  }
  return `系统提示：${content}`;
}

function appendRunEvent(events: AgentRunTimelineEvent[], event: AgentRunTimelineEvent): AgentRunTimelineEvent[] {
  if (event.type === 'text_delta') {
    return events;
  }
  const last = events[events.length - 1];
  if (last && last.type === event.type && last.label === event.label && last.status === event.status) {
    return events;
  }
  return [...events, event].slice(-24);
}

function createRequestId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function requestIdOf(value: { requestId?: string; request_id?: string } | null | undefined) {
  return value?.requestId ?? value?.request_id;
}

function renderInlineMarkdown(text: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index} className="font-semibold text-white">{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('`') && part.endsWith('`')) {
      return <code key={index} className="rounded bg-white/10 px-1 py-0.5 text-[0.9em] text-amber-100">{part.slice(1, -1)}</code>;
    }
    return <Fragment key={index}>{part}</Fragment>;
  });
}

function splitTableRow(line: string) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
}

function isTableSeparator(line: string) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
}

function isBlockStart(line: string, nextLine?: string) {
  return /^#{1,4}\s+/.test(line)
    || /^\s*[-*]\s+/.test(line)
    || /^\s*\d+\.\s+/.test(line)
    || /^\s*---+\s*$/.test(line)
    || (line.includes('|') && !!nextLine && isTableSeparator(nextLine));
}

function MarkdownMessage({ content }: { content: string }) {
  const lines = content.split(/\r?\n/);
  const blocks: ReactNode[] = [];

  for (let i = 0; i < lines.length;) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed) {
      i += 1;
      continue;
    }

    if (/^\s*---+\s*$/.test(line)) {
      blocks.push(<hr key={i} className="my-4 border-white/10" />);
      i += 1;
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      const level = heading[1].length;
      const className = level <= 2
        ? 'mt-1 text-base font-semibold text-white'
        : 'mt-3 text-sm font-semibold text-amber-100';
      blocks.push(<div key={i} className={className}>{renderInlineMarkdown(heading[2])}</div>);
      i += 1;
      continue;
    }

    if (line.includes('|') && lines[i + 1] && isTableSeparator(lines[i + 1])) {
      const headers = splitTableRow(line);
      const rows: string[][] = [];
      i += 2;
      while (i < lines.length && lines[i].trim() && lines[i].includes('|')) {
        rows.push(splitTableRow(lines[i]));
        i += 1;
      }
      blocks.push(
        <div key={i} className="my-4 overflow-x-auto rounded-2xl border border-white/10">
          <table className="w-full min-w-[520px] border-collapse text-left text-xs">
            <thead className="bg-white/10 text-amber-100">
              <tr>
                {headers.map((header, idx) => (
                  <th key={idx} className="border-b border-white/10 px-3 py-2 font-semibold">
                    {renderInlineMarkdown(header)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, rowIdx) => (
                <tr key={rowIdx} className="odd:bg-white/[0.025]">
                  {headers.map((_, cellIdx) => (
                    <td key={cellIdx} className="border-t border-white/5 px-3 py-2 align-top text-gray-200">
                      {renderInlineMarkdown(row[cellIdx] || '')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }

    if (/^\s*[-*]\s+/.test(line) || /^\s*\d+\.\s+/.test(line)) {
      const ordered = /^\s*\d+\.\s+/.test(line);
      const items: string[] = [];
      while (i < lines.length && (ordered ? /^\s*\d+\.\s+/.test(lines[i]) : /^\s*[-*]\s+/.test(lines[i]))) {
        items.push(lines[i].replace(ordered ? /^\s*\d+\.\s+/ : /^\s*[-*]\s+/, '').trim());
        i += 1;
      }
      const ListTag = ordered ? 'ol' : 'ul';
      blocks.push(
        <ListTag key={i} className={(ordered ? 'list-decimal' : 'list-disc') + ' my-3 space-y-1 pl-5 text-sm text-gray-200'}>
          {items.map((item, idx) => <li key={idx}>{renderInlineMarkdown(item)}</li>)}
        </ListTag>,
      );
      continue;
    }

    const paragraph: string[] = [];
    while (i < lines.length && lines[i].trim() && !isBlockStart(lines[i], lines[i + 1])) {
      paragraph.push(lines[i]);
      i += 1;
    }
    blocks.push(
      <p key={i} className="my-2 whitespace-pre-wrap text-sm leading-7 text-gray-200">
        {renderInlineMarkdown(paragraph.join('\n'))}
      </p>,
    );
  }

  return <div className="space-y-1">{blocks}</div>;
}

export default function MangaAgentPage() {
  const [stories, setStories] = useState<Story[]>([]);
  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [storyId, setStoryId] = useState('');
  const [chapterId, setChapterId] = useState('');
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [bootLoading, setBootLoading] = useState(true);
  const [chapterLoading, setChapterLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [error, setError] = useState('');
  const [runStatus, setRunStatus] = useState('正在思考当前章节...');
  const [runEvents, setRunEvents] = useState<AgentRunTimelineEvent[]>([]);
  const [draftReply, setDraftReply] = useState('');
  const [userInputRequest, setUserInputRequest] = useState<AgentUserInputRequest | null>(null);
  const [customAnswer, setCustomAnswer] = useState('');
  const chapterIdRef = useRef('');
  const activeRequestIdRef = useRef<string | null>(null);
  const runPollTimerRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    chapterIdRef.current = chapterId;
  }, [chapterId]);

  useEffect(() => () => {
    if (runPollTimerRef.current !== undefined) {
      window.clearTimeout(runPollTimerRef.current);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setBootLoading(true);
      try {
        const list = await listStories();
        if (!active) return;
        setStories(list);
        if (list.length > 0) setStoryId(String(list[0].id));
      } catch (err: any) {
        if (active) setError(err.message || '加载故事失败');
      } finally {
        if (active) setBootLoading(false);
      }
    };
    load();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    const load = async () => {
      if (!storyId) {
        setChapters([]);
        setChapterId('');
        return;
      }
      setChapterLoading(true);
      setError('');
      try {
        const list = await listChapters(Number(storyId));
        if (!active) return;
        setChapters(list);
        setChapterId((prev) => {
          if (prev && list.some((chapter) => String(chapter.id) === prev)) return prev;
          return list[0] ? String(list[0].id) : '';
        });
      } catch (err: any) {
        if (active) setError(err.message || '加载章节失败');
      } finally {
        if (active) setChapterLoading(false);
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [storyId]);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setInput('');
      setError('');
      setLoading(false);
      setMessages([]);
      setRunEvents([]);
      setDraftReply('');
      setUserInputRequest(null);
      setCustomAnswer('');
      activeRequestIdRef.current = null;
      if (runPollTimerRef.current !== undefined) {
        window.clearTimeout(runPollTimerRef.current);
        runPollTimerRef.current = undefined;
      }
      if (!chapterId) {
        return;
      }
      setHistoryLoading(true);
      try {
        const list = await getMangaAgentMessages(Number(chapterId));
        if (!active) return;
        setMessages(toMessages(list));
        const openRun = await getOpenMangaAgentRun(Number(chapterId));
        if (!active || !openRun) return;
        restoreRunSnapshot(openRun, chapterId);
      } catch (err: any) {
        if (active) setError(err.message || '加载对话记录失败');
      } finally {
        if (active) setHistoryLoading(false);
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [chapterId]);

  const clearRunPoll = () => {
    if (runPollTimerRef.current !== undefined) {
      window.clearTimeout(runPollTimerRef.current);
      runPollTimerRef.current = undefined;
    }
  };

  const reloadMessages = async (id: number, requestChapterId: string) => {
    const list = await getMangaAgentMessages(id);
    if (chapterIdRef.current === requestChapterId) {
      setMessages(toMessages(list));
    }
  };

  const applyMangaAgentEvent = (
    event: MangaAgentRunEvent,
    fallbackRequestId: string,
  ): AgentStreamResult | Error | null => {
    if (event.type === 'status') {
      setRunStatus(event.data.message || '智能体正在处理当前章节...');
      return null;
    }
    if (event.type === 'run_event') {
      setRunEvents((prev) => appendRunEvent(prev, event.data));
      if (event.data.label) setRunStatus(event.data.label);
      if (event.data.type === 'text_delta' && event.data.text) {
        setDraftReply((prev) => prev + event.data.text);
      }
      return null;
    }
    if (event.type === 'tool') {
      const toolLabel = event.data.tool === 'save_structured_storyboard' || event.data.tool === 'save_storyboard'
        ? '分镜保存'
        : event.data.tool === 'generate_storyboard'
          ? '分镜生成'
          : '工具调用';
      if (event.data.succeeded && event.data.saved) {
        setRunStatus(`${toolLabel}已完成，正在整理回复...`);
      } else if (!event.data.succeeded) {
        setRunStatus(`${toolLabel}尝试未通过，智能体正在修正...`);
      }
      return null;
    }
    if (event.type === 'user_input_requested') {
      const requestId = requestIdOf(event.data) ?? fallbackRequestId;
      activeRequestIdRef.current = requestId;
      setUserInputRequest({ ...event.data, requestId });
      setRunStatus('等待你的选择');
      return { reply: '', requestId, waiting: true };
    }
    if (event.type === 'done') {
      const requestId = requestIdOf(event.data) ?? fallbackRequestId;
      activeRequestIdRef.current = null;
      clearRunPoll();
      return {
        reply: event.data.reply || '',
        requestId,
      };
    }
    if (event.type === 'error') {
      activeRequestIdRef.current = null;
      clearRunPoll();
      return new Error(event.data.detail || event.data.error || '智能体请求失败');
    }
    return null;
  };

  const restoreRunSnapshot = (snapshot: MangaAgentRunSnapshot, requestChapterId: string) => {
    const requestId = requestIdOf(snapshot) ?? snapshot.requestId;
    activeRequestIdRef.current = requestId;
    setRunEvents([]);
    setDraftReply('');
    setUserInputRequest(null);

    for (const persisted of snapshot.events || []) {
      applyMangaAgentEvent({
        type: persisted.eventName,
        data: persisted.data,
      } as MangaAgentRunEvent, requestId);
    }

    if (snapshot.status === 'WAITING_USER') {
      const request = snapshot.userInputRequest;
      if (request) {
        setUserInputRequest({ ...request, requestId });
      }
      setRunStatus('等待你的选择');
      setLoading(false);
      clearRunPoll();
      return;
    }

    if (snapshot.status === 'RUNNING') {
      setLoading(true);
      if (!snapshot.events || snapshot.events.length === 0) {
        setRunStatus('智能体仍在处理当前章节...');
      }
      scheduleRunPoll(Number(requestChapterId), requestId, requestChapterId);
      return;
    }

    activeRequestIdRef.current = null;
    clearRunPoll();
    setLoading(false);
    setUserInputRequest(null);
    setDraftReply('');
    if (snapshot.status === 'FAILED') {
      setError(snapshot.errorMessage || '智能体请求失败');
    }
    void reloadMessages(Number(requestChapterId), requestChapterId);
  };

  const scheduleRunPoll = (id: number, requestId: string, requestChapterId: string) => {
    clearRunPoll();
    runPollTimerRef.current = window.setTimeout(async () => {
      if (chapterIdRef.current !== requestChapterId || activeRequestIdRef.current !== requestId) return;
      try {
        const snapshot = await getMangaAgentRunState(id, requestId);
        if (chapterIdRef.current === requestChapterId) {
          restoreRunSnapshot(snapshot, requestChapterId);
        }
      } catch (err: any) {
        if (chapterIdRef.current === requestChapterId) {
          setError(err.message || '同步智能体状态失败');
          setLoading(false);
        }
      }
    }, 3000);
  };

  const send = async (override?: string) => {
    const requestChapterId = chapterId;
    const id = Number(requestChapterId);
    const text = (override ?? input).trim();
    if (!id || !text || loading) return;

    const requestId = createRequestId();
    setLoading(true);
    setError('');
    setRunStatus('智能体已开始处理当前章节...');
    setMessages((prev) => [...prev, { role: 'user', content: text, requestId }]);
    setRunEvents([]);
    setDraftReply('');
    setUserInputRequest(null);
    setCustomAnswer('');
    setInput('');
    activeRequestIdRef.current = requestId;

    try {
      const result = await runMangaAgentWithStream(id, text, requestId, requestChapterId);
      if (chapterIdRef.current === requestChapterId && !result.waiting && result.reply.trim()) {
        setMessages((prev) => [
          ...prev,
          { role: 'assistant', content: result.reply, requestId: result.requestId ?? result.request_id ?? requestId },
        ]);
      }
    } catch (err: any) {
      if (chapterIdRef.current !== requestChapterId) return;
      setError(err.message || '请求失败');
      try {
        const list = await getMangaAgentMessages(id);
        if (chapterIdRef.current === requestChapterId) {
          setMessages(toMessages(list));
        }
      } catch {
        return;
      }
    } finally {
      if (chapterIdRef.current === requestChapterId) {
        setLoading(false);
      }
    }
  };

  const runMangaAgentWithStream = (
    id: number,
    text: string,
    requestId: string,
    requestChapterId: string,
  ): Promise<AgentStreamResult> => {
    return consumeMangaAgentStream(
      id,
      requestId,
      requestChapterId,
      (onEvent) => runMangaAgentStream(id, text, requestId, onEvent),
    );
  };

  const resumeMangaAgentWithStream = (
    id: number,
    requestId: string,
    answer: string,
    requestChapterId: string,
  ): Promise<AgentStreamResult> => {
    return consumeMangaAgentStream(
      id,
      requestId,
      requestChapterId,
      (onEvent) => resumeMangaAgentRunStream(id, requestId, answer, onEvent),
    );
  };

  const consumeMangaAgentStream = (
    id: number,
    requestId: string,
    requestChapterId: string,
    startStream: (onEvent: (event: MangaAgentRunEvent) => void) => AbortController,
  ): Promise<AgentStreamResult> => {
    return new Promise((resolve, reject) => {
      let settled = false;
      let controller: AbortController | null = null;
      controller = startStream((event: MangaAgentRunEvent) => {
        if (chapterIdRef.current !== requestChapterId || settled) return;
        const outcome = applyMangaAgentEvent(event, requestId);
        if (!outcome) return;
        settled = true;
        controller?.abort();
        if (outcome instanceof Error) {
          reject(outcome);
        } else {
          resolve(outcome);
        }
      });

      window.setTimeout(async () => {
        if (settled) return;
        controller?.abort();
        setRunStatus('连接等待较久，正在同步后台运行状态...');
        try {
          const snapshot = await getMangaAgentRunState(id, requestId);
          restoreRunSnapshot(snapshot, requestChapterId);
          if (snapshot.status === 'WAITING_USER') {
            settled = true;
            resolve({ reply: '', requestId, waiting: true });
          } else if (snapshot.status === 'SUCCEEDED' || snapshot.status === 'DEGRADED') {
            settled = true;
            resolve({ reply: snapshot.finalReply || '', requestId });
          } else if (snapshot.status === 'FAILED') {
            settled = true;
            reject(new Error(snapshot.errorMessage || '智能体请求失败'));
          }
        } catch (err) {
          settled = true;
          reject(err);
        }
      }, 240000);
    });
  };

  const resumeWithAnswer = async (answer: string) => {
    const currentRequest = userInputRequest;
    const id = Number(chapterId);
    const requestId = currentRequest?.requestId ?? currentRequest?.request_id;
    if (!id || !requestId || loading) return;
    setLoading(true);
    setError('');
    setRunStatus('已收到你的选择，智能体正在继续...');
    setUserInputRequest(null);
    setCustomAnswer('');
    activeRequestIdRef.current = requestId;
    try {
      const result = await resumeMangaAgentWithStream(id, requestId, answer, chapterId);
      if (!result.waiting && result.reply.trim()) {
        setMessages((prev) => [
          ...prev,
          { role: 'assistant', content: result.reply, requestId: result.requestId ?? result.request_id ?? requestId },
        ]);
      }
      setDraftReply('');
      setRunEvents([]);
    } catch (err: any) {
      setError(err.message || '继续任务失败');
    } finally {
      setLoading(false);
    }
  };

  const activeStory = stories.find((story) => String(story.id) === storyId) ?? null;
  const activeChapter = chapters.find((chapter) => String(chapter.id) === chapterId) ?? null;
  const emptyState = messages.length === 0 && !historyLoading;

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[radial-gradient(circle_at_top,_rgba(139,92,246,0.12),_transparent_28%),linear-gradient(180deg,_#09090b_0%,_#111827_45%,_#09090b_100%)] text-gray-100">
      <header className="border-b border-white/10 bg-black/15 px-5 py-4 backdrop-blur-sm">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/5 shadow-[0_0_40px_rgba(251,191,36,0.08)]">
            <Sparkles size={18} className="text-amber-300" />
          </div>
          <div className="min-w-0">
            <h1 className="text-lg font-semibold text-white">墨染创作</h1>
            <p className="text-sm text-gray-400">用对话串起分镜、章节与漫画生成的创作首页</p>
          </div>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col gap-4 p-4 lg:flex-row">
        <aside className="w-full shrink-0 rounded-3xl border border-white/10 bg-black/20 p-4 backdrop-blur-sm lg:w-[320px]">
          <div className="space-y-4">
            <div>
              <p className="mb-2 text-xs uppercase tracking-[0.22em] text-gray-500">故事</p>
              <select
                value={storyId}
                onChange={(e) => setStoryId(e.target.value)}
                className="w-full rounded-2xl border border-white/10 bg-white/5 px-3 py-3 text-sm text-gray-100 outline-none transition focus:border-amber-400/50"
              >
                {stories.length === 0 ? <option value="">暂无故事</option> : null}
                {stories.map((story) => (
                  <option key={story.id} value={story.id} className="bg-gray-900 text-gray-100">
                    {story.title}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <p className="mb-2 text-xs uppercase tracking-[0.22em] text-gray-500">章节</p>
              <div className="relative">
                <select
                  value={chapterId}
                  onChange={(e) => setChapterId(e.target.value)}
                  disabled={chapterLoading || chapters.length === 0}
                  className="w-full rounded-2xl border border-white/10 bg-white/5 px-3 py-3 text-sm text-gray-100 outline-none transition focus:border-amber-400/50 disabled:opacity-40"
                >
                  {chapters.length === 0 ? <option value="">暂无章节</option> : null}
                  {chapters.map((chapter) => (
                    <option key={chapter.id} value={chapter.id} className="bg-gray-900 text-gray-100">
                      第 {chapter.chapter_number} 话
                    </option>
                  ))}
                </select>
                {chapterLoading && <Loader2 size={16} className="absolute right-3 top-1/2 -translate-y-1/2 animate-spin text-gray-500" />}
              </div>
            </div>

            <div className="rounded-3xl border border-amber-300/10 bg-amber-300/[0.06] p-4">
              <p className="text-xs uppercase tracking-[0.22em] text-amber-200/70">当前工作台</p>
              <div className="mt-3 space-y-2">
                <div>
                  <div className="text-xs text-gray-500">故事名</div>
                  <div className="text-sm text-gray-100">{activeStory?.title || '未选择故事'}</div>
                </div>
                <div>
                  <div className="text-xs text-gray-500">章节</div>
                  <div className="text-sm text-gray-100">{activeChapter ? `第 ${activeChapter.chapter_number} 话` : '未选择章节'}</div>
                </div>
              </div>
            </div>

            <div>
              <p className="mb-2 text-xs uppercase tracking-[0.22em] text-gray-500">快捷发起</p>
              <div className="space-y-2">
                {STARTER_PROMPTS.map((prompt) => (
                  <button
                    key={prompt}
                    onClick={() => void send(prompt)}
                    disabled={!chapterId || loading || historyLoading}
                    className="w-full rounded-2xl border border-white/10 bg-white/[0.04] px-3 py-3 text-left text-sm text-gray-300 transition hover:border-amber-300/30 hover:bg-white/[0.08] hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </aside>

        <main className="flex min-h-0 min-w-0 flex-1 flex-col rounded-[32px] border border-white/10 bg-black/20 backdrop-blur-sm">
          <div className="flex items-center gap-3 border-b border-white/10 px-5 py-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white/5">
              <Bot size={18} className="text-gray-200" />
            </div>
            <div className="min-w-0">
              <div className="text-sm font-medium text-white">漫画智能体</div>
              <div className="text-xs text-gray-500">对话记录按当前章节隔离保存</div>
            </div>
          </div>

          {error && <div className="mx-4 mt-4 rounded-2xl border border-red-500/30 bg-red-950/40 px-3 py-2 text-sm text-red-200">{error}</div>}

          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
            {bootLoading || historyLoading ? (
              <div className="flex h-full items-center justify-center">
                <Loader2 size={28} className="animate-spin text-amber-300" />
              </div>
            ) : emptyState ? (
              <div className="flex h-full flex-col items-center justify-center px-6 text-center">
                <div className="mb-6 flex h-24 w-24 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] shadow-[0_0_60px_rgba(245,158,11,0.08)]">
                  <BookOpenText size={34} className="text-amber-200" />
                </div>
                <h2 className="text-3xl font-semibold text-white">墨染创作</h2>
                <p className="mt-3 max-w-xl text-sm leading-7 text-gray-400">
                  从这一页开始，用对话推进你的漫画创作。选定故事与章节后，智能体会帮你检查上下文、生成分镜、整理下一步。
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {messages.map((msg, idx) => (
                  <div key={`${msg.requestId || 'msg'}-${idx}`} className={msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
                    <div
                      className={
                        'max-w-[85%] rounded-3xl px-4 py-3 shadow-sm ' +
                        (msg.role === 'user'
                          ? 'whitespace-pre-wrap bg-amber-300 text-sm leading-7 text-gray-950'
                          : msg.role === 'system'
                            ? 'border border-red-400/20 bg-red-950/30 text-red-100'
                            : 'border border-white/10 bg-white/[0.04] text-gray-200')
                      }
                    >
                      {msg.role === 'assistant' || msg.role === 'system' ? <MarkdownMessage content={msg.content} /> : msg.content}
                    </div>
                  </div>
                ))}
                {loading && (
                  <div className="flex justify-start">
                    <div className="max-w-[85%] rounded-3xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-gray-300">
                      <div className="mb-3 inline-flex items-center gap-2 text-gray-400">
                        <Loader2 size={15} className="animate-spin" />
                        {runStatus}
                      </div>
                      {runEvents.length > 0 && (
                        <div className="space-y-2 border-l border-white/10 pl-3">
                          {runEvents.slice(-8).map((event, eventIdx) => (
                            <div key={`${event.type}-${event.createdAt || eventIdx}`} className="text-xs leading-5 text-gray-400">
                              <span className={event.status === 'success' || event.status === 'succeeded' ? 'text-emerald-300' : 'text-amber-200'}>
                                {event.label || event.type}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                      {draftReply && (
                        <div className="mt-3 border-t border-white/10 pt-3 text-sm leading-7 text-gray-200">
                          <MarkdownMessage content={draftReply} />
                        </div>
                      )}
                    </div>
                  </div>
                )}
                {userInputRequest && (
                  <div className="flex justify-start">
                    <div className="max-w-[85%] rounded-3xl border border-amber-300/20 bg-amber-300/[0.06] px-4 py-4 text-sm text-gray-100">
                      <div className="text-xs uppercase tracking-[0.18em] text-amber-200/70">需要你的决定</div>
                      <div className="mt-2 text-base font-medium text-white">{userInputRequest.question}</div>
                      {userInputRequest.reason && <div className="mt-1 text-xs text-gray-400">{userInputRequest.reason}</div>}
                      <div className="mt-4 space-y-2">
                        {userInputRequest.options.map((option) => (
                          <button
                            key={option.id}
                            onClick={() => void resumeWithAnswer(option.label)}
                            className="w-full rounded-2xl border border-white/10 bg-black/20 px-3 py-3 text-left transition hover:border-amber-300/40 hover:bg-white/[0.06]"
                          >
                            <div className="flex items-center gap-2 text-sm font-medium text-white">
                              <span>{option.label}</span>
                              {option.recommended && <span className="rounded-full bg-amber-300/15 px-2 py-0.5 text-[11px] text-amber-100">推荐</span>}
                            </div>
                            {option.description && <div className="mt-1 text-xs leading-5 text-gray-400">{option.description}</div>}
                          </button>
                        ))}
                      </div>
                      {userInputRequest.allowFreeText && (
                        <div className="mt-3 flex gap-2">
                          <input
                            value={customAnswer}
                            onChange={(e) => setCustomAnswer(e.target.value)}
                            placeholder="输入其他选择"
                            className="min-w-0 flex-1 rounded-2xl border border-white/10 bg-black/20 px-3 py-2 text-sm text-gray-100 outline-none focus:border-amber-300/40"
                          />
                          <button
                            onClick={() => void resumeWithAnswer(customAnswer)}
                            disabled={!customAnswer.trim()}
                            className="rounded-2xl bg-amber-300 px-4 py-2 text-sm font-medium text-gray-950 disabled:cursor-not-allowed disabled:opacity-40"
                          >
                            继续
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="border-t border-white/10 p-4">
            <div className="flex gap-3">
              <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    void send();
                  }
                }}
                rows={2}
                placeholder={chapterId ? '例如：帮我先检查这一话是否可以直接生成漫画' : '先在左侧选择故事和章节'}
                className="min-h-[58px] flex-1 resize-none rounded-3xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-gray-100 outline-none transition placeholder:text-gray-500 focus:border-amber-300/40"
              />
              <button
                onClick={() => void send()}
                disabled={loading || historyLoading || !chapterId || !input.trim()}
                className="inline-flex h-auto min-w-[110px] items-center justify-center gap-2 rounded-3xl bg-amber-300 px-4 py-3 text-sm font-medium text-gray-950 transition hover:bg-amber-200 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {loading ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                发送
              </button>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
