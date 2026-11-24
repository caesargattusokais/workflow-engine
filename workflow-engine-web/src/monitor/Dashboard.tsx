import { useState, useEffect, useCallback, useRef } from 'react';
import {
  BarChart3, Layers3, CircleDot, Clock3, Activity, CheckCircle2,
  Loader2, PauseCircle, ListTodo, ArrowUpRight, Workflow, Sparkles, Zap,
} from 'lucide-react';
import { apiGet, listInstances, listDrafts } from '../api/client';
import { showToast } from '../api/toast';
import { useT } from '../i18n';

export default function Dashboard() {
  const { t } = useT();
  const [drafts, setDrafts] = useState<any[]>([]);
  const [selectedDraft, setSelectedDraft] = useState<any>(null);
  const [stats, setStats] = useState<any>(null);
  const [instances, setInstances] = useState<any[]>([]);
  const [instPage, setInstPage] = useState(1);
  const [instHasMore, setInstHasMore] = useState(false);
  const [instLoading, setInstLoading] = useState(false);
  const [selectedInstId, setSelectedInstId] = useState<string | null>(null);
  const [timeline, setTimeline] = useState<any[] | null>(null);
  const [draftPage, setDraftPage] = useState(1);
  const [draftHasMore, setDraftHasMore] = useState(false);
  const [draftLoading, setDraftLoading] = useState(false);
  const [globalStats, setGlobalStats] = useState<any>(null);
  const [recent, setRecent] = useState<any[]>([]);

  const loadDrafts = useCallback(async (page: number) => {
    setDraftLoading(true);
    try {
      const r: any = await listDrafts(page, 10);
      const list = r.items || r;
      setDraftHasMore(list.length >= 10);
      if (page === 1) setDrafts(list);
      else setDrafts(prev => [...prev, ...list]);
      setDraftPage(page);
    } catch (e: any) { showToast(e.message || 'Failed to load drafts', 'error'); } finally { setDraftLoading(false); }
  }, []);

  useEffect(() => {
    loadDrafts(1);
    apiGet('/dashboard/stats').then(setGlobalStats).catch(e => { showToast(e.message, 'error'); setGlobalStats(null); });
    // Recent activity feed (global, newest first).
    listInstances(1, 8).then((r: any) => setRecent(r.items || r)).catch(() => setRecent([]));
  }, [loadDrafts]);

  const selectDraft = useCallback(async (draft: any) => {
    setSelectedDraft(draft);
    setSelectedInstId(null);
    setTimeline(null);
    setStats(null);
    apiGet(`/dashboard/stats?definitionId=${encodeURIComponent(draft.id)}`)
      .then(setStats).catch(e => { showToast(e.message, 'error'); setStats(null); });
    loadInstances(draft.id, 1);
  }, []);

  const loadInstances = useCallback(async (defId: string, page: number) => {
    setInstLoading(true);
    try {
      const r: any = await listInstances(page, 10, defId);
      const list = r.items || r;
      setInstHasMore(list.length >= 10);
      if (page === 1) setInstances(list);
      else setInstances(prev => [...prev, ...list]);
      setInstPage(page);
    } catch (e: any) { showToast(e.message || 'Failed to load instances', 'error'); } finally { setInstLoading(false); }
  }, []);

  const timelineLoadIdRef = useRef(0);
  const loadTimeline = async (instId: string) => {
    const loadId = ++timelineLoadIdRef.current;
    setSelectedInstId(instId);
    try {
      const data = await apiGet(`/dashboard/timeline/${instId}`);
      if (timelineLoadIdRef.current !== loadId) return;
      setTimeline(data);
    } catch (e: any) {
      if (timelineLoadIdRef.current !== loadId) return;
      showToast(e.message, 'error'); setTimeline(null);
    }
  };

  const activeStats = selectedDraft ? stats : globalStats;

  return (
    <div className="flex h-full bg-app">
      {/* Left: Flow list */}
      <aside className="w-56 shrink-0 bg-gray-850 border-r border-white/[0.06] flex flex-col">
        <div className="px-4 py-4 eyebrow flex items-center gap-2 border-b border-white/[0.06]">
          <Layers3 size={13} className="text-accent" strokeWidth={2} />
          {t.dashboard.myFlows}
        </div>
        <div className="flex-1 overflow-y-auto px-2 py-2"
          onScroll={(e) => {
            const el = e.currentTarget;
            if (el.scrollHeight - el.scrollTop - el.clientHeight < 50 && draftHasMore && !draftLoading) {
              loadDrafts(draftPage + 1);
            }
          }}>
          <button onClick={() => { setSelectedDraft(null); setSelectedInstId(null); setTimeline(null); }}
            className={`w-full text-left px-3 py-2 rounded-lg mb-1 text-xs flex items-center gap-2 transition-colors
              ${!selectedDraft ? 'bg-accent/15 text-gray-100 ring-1 ring-inset ring-accent/30' : 'text-gray-400 hover:bg-white/[0.04] hover:text-gray-200'}`}>
            <Workflow size={14} className={!selectedDraft ? 'text-accent' : 'text-gray-500'} />
            <span className="font-medium">{t.dashboard.allFlows || 'All Flows'}</span>
          </button>
          <div className="my-2 border-t border-white/[0.05]" />
          {drafts.map((d: any) => (
            <div key={d.id}
              onClick={() => selectDraft(d)}
              className={`px-3 py-2 cursor-pointer text-xs rounded-lg mb-0.5 flex items-center gap-2 transition-colors
                ${selectedDraft?.id === d.id ? 'bg-accent/15 ring-1 ring-inset ring-accent/30' : 'hover:bg-white/[0.04]'}`}>
              <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${selectedDraft?.id === d.id ? 'bg-accent' : 'bg-gray-600'}`} />
              <span className={`truncate font-medium ${selectedDraft?.id === d.id ? 'text-gray-100' : 'text-gray-300'}`}>{d.name}</span>
            </div>
          ))}
          {draftHasMore ? (
            <button onClick={() => loadDrafts(draftPage + 1)} disabled={draftLoading}
              className="w-full text-center py-1.5 text-xs text-accent hover:bg-white/[0.04] disabled:text-gray-600">
              {draftLoading ? t.dashboard.loading : `${t.dashboard.loadMore} (${drafts.length})`}
            </button>
          ) : (
            drafts.length > 0 && (
              <div className="text-center py-2 text-[11px] text-gray-600">
                {t.dashboard.totalFlows.replace('{n}', String(drafts.length))}
              </div>
            )
          )}
          {drafts.length === 0 && !draftLoading && (
            <div className="p-3 text-xs text-gray-600 text-center">{t.dashboard.noFlows}</div>
          )}
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-y-auto bg-accent-glow">
        {!activeStats ? (
          <EmptyState
            icon={<BarChart3 size={28} />}
            title={globalStats ? `${t.dashboard.allFlows || 'All Flows'} · ${globalStats.total || 0} ${t.dashboard.totalInstances}` : t.dashboard.selectPrompt}
            hint={globalStats ? '' : undefined}
          />
        ) : (
          <div className="max-w-[1200px] mx-auto px-8 py-7">
            {/* Header */}
            <div className="mb-7">
              <div className="eyebrow mb-1.5">{selectedDraft ? selectedDraft.id.substring(0, 12) : 'OVERVIEW'}</div>
              <h1 className="title-lg flex items-center gap-2.5">
                {selectedDraft ? selectedDraft.name : (t.dashboard.allFlows || 'All Flows')}
                <span className="text-[11px] font-normal text-gray-500 px-2 py-0.5 rounded-full bg-white/[0.05] border border-white/[0.06]">
                  {t.dashboard.health}
                </span>
              </h1>
            </div>

            {/* KPI row — big numbers, icon tiles, gradient, keep E2E classes */}
            <div className="grid grid-cols-5 gap-3.5 mb-5">
              <Kpi label={t.dashboard.totalInstances} value={activeStats.total || 0} icon={<Workflow size={16} />} tone="indigo" />
              <Kpi label={t.dashboard.running} value={activeStats.running || 0} icon={<Loader2 size={16} />} tone="green" />
              <Kpi label={t.dashboard.completed} value={activeStats.completed || 0} icon={<CheckCircle2 size={16} />} tone="blue" />
              <Kpi label={t.dashboard.suspended} value={activeStats.suspended || 0} icon={<PauseCircle size={16} />} tone="amber" />
              <Kpi label={t.dashboard.pendingTasks || 'Pending Tasks'} value={activeStats.pendingTasks || 0} icon={<ListTodo size={16} />} tone="pink" />
            </div>

            {/* Health + duration panel */}
            <div className="grid grid-cols-[minmax(0,300px)_1fr] gap-4 mb-5">
              <HealthCard stats={activeStats} t={t} />
              <div className="card-hover p-5 flex flex-col justify-center">
                <div className="flex items-center gap-2 eyebrow mb-4">
                  <Clock3 size={13} className="text-gray-500" />
                  {t.dashboard.avgDuration}
                </div>
                <div className="flex items-end gap-2 mb-4">
                  <span className="text-[34px] font-semibold leading-none tracking-tight tabular-nums text-gray-50">
                    {formatDuration(activeStats.avgDurationMs || 0)}
                  </span>
                </div>
                <div className="w-full h-2.5 bg-gray-900/70 rounded-full overflow-hidden flex gap-0.5">
                  <Bar pct={pct(activeStats.completed, activeStats.total)} color="bg-blue-500" />
                  <Bar pct={pct(activeStats.suspended, activeStats.total)} color="bg-amber-500" />
                  <Bar pct={pct(activeStats.terminated, activeStats.total)} color="bg-red-500" />
                  <Bar pct={pct(activeStats.running, activeStats.total)} color="bg-green-500" />
                </div>
                <div className="flex flex-wrap gap-x-4 gap-y-1 mt-3 text-[11px] text-gray-500">
                  <Legend color="bg-blue-500" label={t.dashboard.completed} />
                  <Legend color="bg-green-500" label={t.dashboard.running} />
                  <Legend color="bg-amber-500" label={t.dashboard.suspended} />
                  <Legend color="bg-red-500" label="Terminated" />
                </div>
              </div>
            </div>

            {/* Selected flow: instance list + step timeline. Global: recent activity. */}
            {selectedDraft ? (
              <div className="grid grid-cols-2 gap-4">
                <section className="card-hover p-5">
                  <h3 className="eyebrow mb-3 flex items-center gap-2">
                    <CircleDot size={13} className="text-gray-500" /> {t.dashboard.instanceList}
                  </h3>
                  <div className="max-h-64 overflow-y-auto -mx-1">
                    {instances.map((inst: any) => (
                      <div key={inst.id}
                        onClick={() => loadTimeline(inst.id)}
                        className={`px-3 py-2 rounded-lg mb-0.5 cursor-pointer text-xs flex items-center gap-2 transition-colors
                          ${selectedInstId === inst.id ? 'bg-accent/20 ring-1 ring-inset ring-accent/30' : 'hover:bg-white/[0.04]'}`}>
                        <span className={`w-2 h-2 rounded-full shrink-0 ${
                          inst.status === 'RUNNING' ? 'bg-green-400 shadow-[0_0_8px_rgba(74,222,128,0.6)]' :
                          inst.status === 'COMPLETED' ? 'bg-blue-400' :
                          inst.status === 'SUSPENDED' ? 'bg-amber-400' : 'bg-red-400'}`} />
                        <span className="text-gray-300 truncate flex-1 font-mono">{inst.id.substring(0, 8)}</span>
                        <span className="text-[11px] text-gray-500">{inst.status}</span>
                      </div>
                    ))}
                    {instHasMore ? (
                      <button onClick={() => loadInstances(selectedDraft.id, instPage + 1)} disabled={instLoading}
                        className="w-full text-center py-2 text-xs text-accent hover:bg-white/[0.04] rounded-lg disabled:text-gray-600">
                        {instLoading ? t.dashboard.loading : `${t.dashboard.loadMore} (${instances.length})`}
                      </button>
                    ) : (
                      instances.length > 0 && (
                        <div className="text-center py-2 text-[11px] text-gray-600">{instances.length} {t.dashboard.instanceList}</div>
                      )
                    )}
                    {instances.length === 0 && <div className="text-xs text-gray-600 py-4 text-center">{t.monitor.noInstances}</div>}
                  </div>
                </section>

                <section className="card-hover p-5">
                  <h3 className="eyebrow mb-3 flex items-center gap-2">
                    <Activity size={13} className="text-gray-500" /> {t.dashboard.stepDuration}
                  </h3>
                  {!selectedInstId && (
                    <div className="flex flex-col items-center justify-center h-48 text-center">
                      <div className="w-11 h-11 rounded-xl bg-white/[0.04] flex items-center justify-center text-gray-600 mb-3">
                        <Clock3 size={20} />
                      </div>
                      <div className="text-xs text-gray-500">{t.dashboard.clickForTimeline}</div>
                    </div>
                  )}
                  {timeline && timeline.length === 0 && <div className="text-xs text-gray-500 py-4">{t.dashboard.noHistory}</div>}
                  {timeline && timeline.map((step: any, i: number) => (
                    <div key={i} className="flex items-center gap-3 py-2 border-b border-white/[0.05] text-xs last:border-0">
                      <div className={`w-2 h-2 rounded-full shrink-0 ${
                        step.action === 'enter' ? 'bg-blue-400' : step.action === 'complete' ? 'bg-green-400' : 'bg-gray-500'}`} />
                      <div className="flex-1 min-w-0">
                        <div className="text-gray-300 truncate">{step.nodeName || step.nodeId}</div>
                        <div className="text-[11px] text-gray-600">{step.action} · {step.time?.substring(11, 19)}</div>
                      </div>
                      {step.durationMs != null && (
                        <div className="text-gray-400 font-mono tabular-nums">{formatDuration(step.durationMs)}</div>
                      )}
                    </div>
                  ))}
                </section>
              </div>
            ) : (
              <div className="grid grid-cols-[minmax(0,1fr)_280px] gap-4">
                <section className="card-hover p-5">
                  <h3 className="eyebrow mb-3 flex items-center gap-2">
                    <Activity size={13} className="text-gray-500" /> {t.dashboard.recentActivity}
                  </h3>
                  {recent.length === 0 ? (
                    <div className="text-xs text-gray-600 py-6 text-center">{t.dashboard.noRecent}</div>
                  ) : (
                    <div className="grid grid-cols-2 gap-x-6">
                      {recent.slice(0, 8).map((inst: any, i: number) => (
                        <div key={inst.id || i} className="flex items-center gap-3 py-2.5 border-b border-white/[0.05] text-xs">
                          <span className={`w-2 h-2 rounded-full shrink-0 ${
                            inst.status === 'RUNNING' ? 'bg-green-400 shadow-[0_0_8px_rgba(74,222,128,0.5)]' :
                            inst.status === 'COMPLETED' ? 'bg-blue-400' :
                            inst.status === 'SUSPENDED' ? 'bg-amber-400' : 'bg-red-400'}`} />
                          <div className="flex-1 min-w-0">
                            <div className="text-gray-300 font-mono truncate">{inst.id?.substring(0, 8)}</div>
                            <div className="text-[11px] text-gray-600 truncate">{inst.definitionName || inst.definitionId || '—'}</div>
                          </div>
                          <span className="text-[11px] text-gray-500">{inst.status}</span>
                          <ArrowUpRight size={13} className="text-gray-600" />
                        </div>
                      ))}
                    </div>
                  )}
                </section>

                <QuickStart t={t} />
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}

/* ---------- Sub-components ---------- */

const TONES: Record<string, { text: string; bg: string; ring: string }> = {
  indigo: { text: 'text-indigo-300', bg: 'bg-indigo-500/15', ring: 'ring-indigo-400/20' },
  green: { text: 'text-emerald-300', bg: 'bg-emerald-500/15', ring: 'ring-emerald-400/20' },
  blue: { text: 'text-sky-300', bg: 'bg-sky-500/15', ring: 'ring-sky-400/20' },
  amber: { text: 'text-amber-300', bg: 'bg-amber-500/15', ring: 'ring-amber-400/20' },
  pink: { text: 'text-pink-300', bg: 'bg-pink-500/15', ring: 'ring-pink-400/20' },
};

function Kpi({ label, value, icon, tone }: { label: string; value: number; icon: React.ReactNode; tone: string }) {
  const c = TONES[tone] || TONES.indigo;
  return (
    <div className="card-hover p-4">
      <div className="flex items-start justify-between">
        <div className="eyebrow leading-4 min-h-[32px]">{label}</div>
        <div className={`icon-tile ${c.bg} ${c.text} ring-1 ring-inset ${c.ring}`}>{icon}</div>
      </div>
      {/* Keep text-2xl font-bold for E2E; inline style wins over the utility so the number actually renders large. */}
      <div className="text-2xl font-bold leading-none tracking-tight mt-3"
        style={{
          fontSize: 38, fontWeight: 650, letterSpacing: '-0.04em',
          background: 'linear-gradient(180deg,#ffffff,#b8c0cf)',
          WebkitBackgroundClip: 'text', backgroundClip: 'text', WebkitTextFillColor: 'transparent',
        }}>{value}</div>
    </div>
  );
}

function HealthCard({ stats, t }: { stats: any; t: any }) {
  const total = stats.total || 0;
  const completed = stats.completed || 0;
  const rate = total ? Math.round(completed / total * 100) : 0;
  const R = 42, C = 2 * Math.PI * R;
  return (
    <div className="card-hover p-5 flex items-center gap-5">
      <div className="relative shrink-0" style={{ width: 108, height: 108 }}>
        <svg viewBox="0 0 100 100" className="w-full h-full -rotate-90">
          <circle cx="50" cy="50" r={R} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="9" />
          <circle cx="50" cy="50" r={R} fill="none" stroke="url(#healthGrad)" strokeWidth="9"
            strokeLinecap="round" strokeDasharray={C} strokeDashoffset={C * (1 - rate / 100)}
            style={{ transition: 'stroke-dashoffset 0.6s cubic-bezier(0.2,0.8,0.2,1)' }} />
          <defs>
            <linearGradient id="healthGrad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#6d74e8" />
              <stop offset="100%" stopColor="#4ade80" />
            </linearGradient>
          </defs>
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-[22px] font-semibold leading-none text-gray-50 tabular-nums">{rate}%</span>
          <span className="text-[10px] text-gray-500 mt-1">{t.dashboard.successRate}</span>
        </div>
      </div>
      <div className="min-w-0">
        <div className="eyebrow mb-2">{t.dashboard.health}</div>
        <div className="title-md mb-1">{completed} / {total}</div>
        <div className="text-[11px] text-gray-500 leading-relaxed">
          {t.dashboard.healthRate}: {rate}%
        </div>
      </div>
    </div>
  );
}

function QuickStart({ t }: { t: any }) {
  const items = [
    { icon: <Sparkles size={15} />, title: t.designer?.aiGenerateTitle || 'AI generate', desc: t.designer?.aiPromptPlaceholder || 'Describe your flow in natural language', tone: 'text-accent bg-accent/15' },
    { icon: <Layers3 size={15} />, title: t.designer?.templates || 'Templates', desc: t.designer?.templateHint || 'Start from a ready-made flow', tone: 'text-emerald-300 bg-emerald-500/15' },
  ];
  return (
    <section className="card-hover p-5 relative overflow-hidden">
      <div className="absolute -top-10 -right-10 w-32 h-32 rounded-full bg-accent/10 blur-3xl pointer-events-none" />
      <h3 className="eyebrow mb-4 flex items-center gap-2 relative">
        <Zap size={13} className="text-accent" /> {t.dashboard.quickStart || 'Quick start'}
      </h3>
      <div className="space-y-3 relative">
        {items.map((it, i) => (
          <div key={i} className="flex items-start gap-3 p-3 rounded-lg bg-white/[0.025] border border-white/[0.05]">
            <div className={`icon-tile w-8 h-8 ${it.tone} ring-1 ring-inset ring-white/[0.06]`}>{it.icon}</div>
            <div className="min-w-0">
              <div className="text-xs font-medium text-gray-200 mb-0.5">{it.title}</div>
              <div className="text-[11px] text-gray-500 leading-relaxed">{it.desc}</div>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-4 text-[11px] text-gray-600 leading-relaxed border-t border-white/[0.05] pt-3">
        {(t.designer?.dragHint || 'Drag nodes onto the canvas')} — {t.dashboard.quickStartHint || 'switch to the Designer tab to begin'}
      </div>
    </section>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return <span className="inline-flex items-center gap-1.5"><span className={`w-2 h-2 rounded-sm ${color}`} />{label}</span>;
}

function Bar({ pct, color }: { pct: number; color: string }) {
  if (pct <= 0) return null;
  return <div className={`${color} h-full transition-all duration-500`} style={{ width: pct + '%' }} />;
}

function EmptyState({ icon, title, hint }: { icon: React.ReactNode; title: string; hint?: string }) {
  return (
    <div className="h-full flex flex-col items-center justify-center text-center px-6">
      <div className="relative w-24 h-24 mb-5">
        <div className="absolute inset-0 rounded-3xl bg-accent/10 blur-2xl" />
        <div className="relative w-full h-full rounded-3xl bg-gradient-to-br from-white/[0.07] to-white/[0.02]
          border border-white/[0.08] flex items-center justify-center text-accent shadow-card">
          {icon}
        </div>
      </div>
      <div className="text-sm text-gray-300 font-medium">{title}</div>
      {hint && <div className="text-[11px] text-gray-600 mt-2 max-w-[280px]">{hint}</div>}
    </div>
  );
}

function pct(n: number, total: number) { return Math.round((n || 0) / (total || 1) * 100); }

function formatDuration(ms: number) {
  if (!ms || ms < 0) return '0ms';
  if (ms < 1000) return ms + 'ms';
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
  if (ms < 3600000) return (ms / 60000).toFixed(1) + 'min';
  return (ms / 3600000).toFixed(1) + 'h';
}
