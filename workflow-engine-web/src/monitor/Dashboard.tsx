import { useState, useEffect, useCallback, useRef } from 'react';
import { BarChart3, Layers3, CircleDot, Clock3 } from 'lucide-react';
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

  useEffect(() => { loadDrafts(1); apiGet('/dashboard/stats').then(setGlobalStats).catch(e => { showToast(e.message, 'error'); setGlobalStats(null); }); }, [loadDrafts]);

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
      if (timelineLoadIdRef.current !== loadId) return; // stale — another instance was picked
      setTimeline(data);
    } catch (e: any) {
      if (timelineLoadIdRef.current !== loadId) return;
      showToast(e.message, 'error'); setTimeline(null);
    }
  };

  return (
    <div className="flex h-full">
      {/* Left: Draft list */}
      <div className="w-52 bg-gray-850 border-r border-gray-700/70 flex flex-col">
        <div className="px-3 py-3 text-[11px] uppercase tracking-wider text-gray-400 font-medium border-b border-gray-700/70 flex items-center gap-2">
          <Layers3 size={12} strokeWidth={2} />
          {t.dashboard.myFlows}
        </div>
        <div className="flex-1 overflow-y-auto"
          onScroll={(e) => {
            const el = e.currentTarget;
            if (el.scrollHeight - el.scrollTop - el.clientHeight < 50 && draftHasMore && !draftLoading) {
              loadDrafts(draftPage + 1);
            }
          }}>
          {drafts.map((d: any) => (
            <div key={d.id}
              onClick={() => selectDraft(d)}
              className={`px-3 py-2 cursor-pointer text-xs border-b border-gray-800/60 hover:bg-gray-800/70 transition-colors
                ${selectedDraft?.id === d.id ? 'bg-blue-600/20 border-l-2 border-l-blue-500' : ''}`}>
              <div className={`truncate font-medium ${selectedDraft?.id === d.id ? 'text-gray-100' : 'text-gray-300'}`}>{d.name}</div>
            </div>
          ))}
          {draftHasMore ? (
            <button onClick={() => loadDrafts(draftPage + 1)} disabled={draftLoading}
              className="w-full text-center py-1.5 text-xs text-purple-400 hover:bg-gray-700 disabled:text-gray-600">
              {draftLoading ? t.dashboard.loading : `${t.dashboard.loadMore} (${drafts.length})`}
            </button>
          ) : (
            drafts.length > 0 && (
              <div className="text-center py-1 text-[10px] text-gray-600">
                {t.dashboard.totalFlows.replace('{n}', String(drafts.length))}
              </div>
            )
          )}
          {drafts.length === 0 && !draftLoading && (
            <div className="p-3 text-xs text-gray-600 text-center">{t.dashboard.noFlows}</div>
          )}
        </div>
      </div>

      {/* Right: Stats + Instances */}
      <div className="flex-1 overflow-y-auto p-6">
        {!selectedDraft ? (
          <>
            {/* Global stats when no draft selected */}
            {globalStats ? (
              <div>
                <h2 className="text-base font-semibold text-gray-100 mb-5 flex items-center gap-2">
                  <BarChart3 size={16} className="text-blue-400" strokeWidth={2} />
                  {t.dashboard.allFlows || 'All Flows'}
                </h2>
                <div className="grid grid-cols-5 gap-3 mb-6">
                  <Kpi label={t.dashboard.totalInstances} value={globalStats.total || 0} color="text-blue-400" />
                  <Kpi label={t.dashboard.running} value={globalStats.running || 0} color="text-green-400" />
                  <Kpi label={t.dashboard.completed} value={globalStats.completed || 0} color="text-gray-300" />
                  <Kpi label={t.dashboard.suspended} value={globalStats.suspended || 0} color="text-yellow-400" />
                  <Kpi label={t.dashboard.pendingTasks || 'Pending Tasks'} value={globalStats.pendingTasks || 0} color="text-purple-400" />
                </div>
                {(globalStats.total || 0) > 0 && (
                  <div className="bg-gray-800/70 border border-gray-700/60 rounded-lg p-4">
                    <div className="flex items-center gap-2 text-[11px] text-gray-400">
                      <Clock3 size={12} className="text-gray-500" />
                      {t.dashboard.avgDuration} <span className="text-gray-100 font-semibold">{formatDuration(globalStats.avgDurationMs || 0)}</span>
                    </div>
                    <div className="w-full h-4 bg-gray-700 rounded-full overflow-hidden flex mt-2">
                      <Bar pct={pct(globalStats.completed, globalStats.total)} color="bg-green-500" />
                      <Bar pct={pct(globalStats.suspended, globalStats.total)} color="bg-yellow-500" />
                      <Bar pct={pct(globalStats.terminated, globalStats.total)} color="bg-red-500" />
                      <Bar pct={pct(globalStats.running, globalStats.total)} color="bg-blue-500" label={pct(globalStats.running, globalStats.total) + '%'} />
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-gray-500 text-sm text-center mt-20">{t.dashboard.selectPrompt}</div>
            )}
          </>
        ) : !stats ? (
          <div className="text-gray-500 text-sm text-center mt-20">{t.dashboard.loading}</div>
        ) : (
          <>
            <h2 className="text-base font-semibold text-gray-100 mb-1">{selectedDraft.name}</h2>
            <div className="text-[10px] text-gray-600 font-mono mb-5">{selectedDraft.id}</div>

            {/* KPI cards */}
            <div className="grid grid-cols-5 gap-3 mb-6">
              <Kpi label={t.dashboard.totalInstances} value={stats.total || 0} color="text-blue-400" />
              <Kpi label={t.dashboard.running} value={stats.running || 0} color="text-green-400" />
              <Kpi label={t.dashboard.completed} value={stats.completed || 0} color="text-gray-300" />
              <Kpi label={t.dashboard.suspended} value={stats.suspended || 0} color="text-yellow-400" />
              <Kpi label={t.dashboard.pendingTasks || 'Pending Tasks'} value={stats.pendingTasks || 0} color="text-purple-400" />
            </div>

            {/* Avg duration + progress */}
            <div className="bg-gray-800/70 border border-gray-700/60 rounded-lg p-4 mb-6">
              <div className="flex items-center gap-2 text-[11px] text-gray-400">
                <Clock3 size={12} className="text-gray-500" />
                {t.dashboard.avgDuration} <span className="text-gray-100 font-semibold">{formatDuration(stats.avgDurationMs || 0)}</span>
              </div>
              {(stats.total || 0) > 0 && (
                <div className="w-full h-4 bg-gray-700 rounded-full overflow-hidden flex mt-2">
                  <Bar pct={pct(stats.completed, stats.total)} color="bg-green-500" />
                  <Bar pct={pct(stats.suspended, stats.total)} color="bg-yellow-500" />
                  <Bar pct={pct(stats.terminated, stats.total)} color="bg-red-500" />
                  <Bar pct={pct(stats.running, stats.total)} color="bg-blue-500" label={pct(stats.running, stats.total) + '%'} />
                </div>
              )}
            </div>

            {/* Instance list + Timeline */}
            <div className="flex gap-4" style={{ minHeight: 0 }}>
              <div className="flex-1">
                <h3 className="text-[11px] uppercase tracking-wider text-gray-500 font-medium mb-3">{t.dashboard.instanceList}</h3>
                <div className="max-h-64 overflow-y-auto">
                  {instances.map((inst: any) => (
                    <div key={inst.id}
                      onClick={() => loadTimeline(inst.id)}
                      className={`p-1.5 rounded mb-0.5 cursor-pointer text-xs flex items-center gap-1.5
                        ${selectedInstId === inst.id ? 'bg-blue-600/30' : 'hover:bg-gray-750'}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${
                        inst.status === 'RUNNING' ? 'bg-green-500' :
                        inst.status === 'COMPLETED' ? 'bg-blue-500' :
                        inst.status === 'SUSPENDED' ? 'bg-yellow-500' : 'bg-red-500'}`} />
                      <span className="text-gray-300 truncate flex-1">{inst.id.substring(0, 8)}</span>
                      <span className="text-[10px] text-gray-500">{inst.status}</span>
                    </div>
                  ))}
                  {instHasMore ? (
                    <button onClick={() => loadInstances(selectedDraft.id, instPage + 1)}
                      disabled={instLoading}
                      className="w-full text-center py-1.5 text-xs text-blue-400 hover:bg-gray-700 rounded disabled:text-gray-600">
                      {instLoading ? t.dashboard.loading : `${t.dashboard.loadMore} (${instances.length})`}
                    </button>
                  ) : (
                    instances.length > 0 && (
                      <div className="text-center py-1 text-[10px] text-gray-600">
                        {t.monitor.noInstances ? `${instances.length} ${t.dashboard.instanceList}` : `${instances.length}`}
                      </div>
                    )
                  )}
                  {instances.length === 0 && <div className="text-xs text-gray-600">{t.monitor.noInstances}</div>}
                </div>
              </div>

              {/* Timeline */}
              <div className="flex-1">
                <h3 className="text-[11px] uppercase tracking-wider text-gray-500 font-medium mb-3">{t.dashboard.stepDuration}</h3>
                {!selectedInstId && <div className="text-xs text-gray-600">{t.dashboard.clickForTimeline}</div>}
                {timeline && timeline.length === 0 && <div className="text-xs text-gray-500">{t.dashboard.noHistory}</div>}
                {timeline && timeline.map((step: any, i: number) => (
                  <div key={i} className="flex items-center gap-2 py-1.5 border-b border-gray-750 text-xs">
                    <div className={`w-2 h-2 rounded-full ${step.action === 'enter' ? 'bg-blue-500' : step.action === 'complete' ? 'bg-green-500' : 'bg-gray-500'}`} />
                    <div className="flex-1">
                      <div className="text-gray-300">{step.nodeName || step.nodeId}</div>
                      <div className="text-[10px] text-gray-500">{step.action} · {step.time?.substring(11, 19)}</div>
                    </div>
                    {step.durationMs != null && (
                      <div className="text-gray-400 font-mono">{formatDuration(step.durationMs)}</div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function Kpi({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="bg-gray-800/70 border border-gray-700/60 rounded-lg p-4 hover:border-gray-600 transition-colors">
      <div className={`text-2xl font-bold leading-none tracking-tight ${color}`} style={{ fontSize: 26, fontWeight: 600 }}>{value}</div>
      <div className="text-[11px] text-gray-500 mt-2">{label}</div>
    </div>
  );
}

function Bar({ pct, color, label }: { pct: number; color: string; label?: string }) {
  if (pct <= 0) return null;
  return (
    <div className={`${color} h-full text-[10px] text-white flex items-center justify-center`}
      style={{ width: pct + '%' }}>
      {label}
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
