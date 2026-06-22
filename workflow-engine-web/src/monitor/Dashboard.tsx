import { useState, useEffect, useCallback } from 'react';
import { apiGet, listInstances, listDrafts } from '../api/client';

export default function Dashboard() {
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

  // Load user drafts for sidebar (paginated)
  const loadDrafts = useCallback(async (page: number) => {
    setDraftLoading(true);
    try {
      const r: any = await listDrafts(page, 10);
      const list = r.items || r;
      setDraftHasMore(list.length >= 10);
      if (page === 1) setDrafts(list);
      else setDrafts(prev => [...prev, ...list]);
      setDraftPage(page);
    } catch {} finally { setDraftLoading(false); }
  }, []);

  useEffect(() => { loadDrafts(1); }, [loadDrafts]);

  // Load stats + instances when draft selected
  const selectDraft = useCallback(async (draft: any) => {
    setSelectedDraft(draft);
    setSelectedInstId(null);
    setTimeline(null);
    setStats(null);
    // Load stats
    apiGet(`/dashboard/stats?definitionId=${encodeURIComponent(draft.id)}`)
      .then(setStats).catch(() => setStats(null));
    // Load first page of instances
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
    } catch {} finally { setInstLoading(false); }
  }, []);

  const loadTimeline = async (instId: string) => {
    setSelectedInstId(instId);
    try {
      const data = await apiGet(`/dashboard/timeline/${instId}`);
      setTimeline(data);
    } catch { setTimeline(null); }
  };

  // ── Render ──
  return (
    <div className="flex h-full">
      {/* Left: Draft list */}
      <div className="w-48 bg-gray-800 border-r border-gray-700 flex flex-col">
        <div className="p-2 text-xs text-gray-400 font-semibold border-b border-gray-700">我的流程</div>
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
              className={`px-3 py-2 cursor-pointer text-xs border-b border-gray-800 hover:bg-gray-750
                ${selectedDraft?.id === d.id ? 'bg-blue-600/30 border-l-2 border-l-blue-500' : ''}`}>
              <div className="text-gray-300 truncate">{d.name}</div>
            </div>
          ))}
          {draftHasMore ? (
            <button onClick={() => loadDrafts(draftPage + 1)} disabled={draftLoading}
              className="w-full text-center py-1.5 text-xs text-purple-400 hover:bg-gray-700 disabled:text-gray-600">
              {draftLoading ? '加载中...' : `加载更多 (${drafts.length} 个)`}
            </button>
          ) : (
            drafts.length > 0 && <div className="text-center py-1 text-[10px] text-gray-600">共 {drafts.length} 个流程</div>
          )}
          {drafts.length === 0 && !draftLoading && (
            <div className="p-3 text-xs text-gray-600 text-center">暂无可显示的流程</div>
          )}
        </div>
      </div>

      {/* Right: Stats + Instances */}
      <div className="flex-1 overflow-y-auto p-4">
        {!selectedDraft ? (
          <div className="text-gray-500 text-sm text-center mt-20">← 选择一个流程查看统计数据</div>
        ) : !stats ? (
          <div className="text-gray-500 text-sm text-center mt-20">加载中...</div>
        ) : (
          <>
            <h2 className="text-gray-200 font-bold mb-1">{selectedDraft.name}</h2>
            <div className="text-[10px] text-gray-600 mb-4">{selectedDraft.id}</div>

            {/* KPI cards */}
            <div className="grid grid-cols-4 gap-3 mb-6">
              <Kpi label="总实例" value={stats.total || 0} color="text-blue-400" />
              <Kpi label="运行中" value={stats.running || 0} color="text-green-400" />
              <Kpi label="已完成" value={stats.completed || 0} color="text-gray-300" />
              <Kpi label="挂起" value={stats.suspended || 0} color="text-yellow-400" />
            </div>

            {/* Avg duration + progress */}
            <div className="bg-gray-750 rounded p-3 mb-6">
              <div className="text-xs text-gray-400">
                平均耗时 <span className="text-gray-200 font-bold">{formatDuration(stats.avgDurationMs || 0)}</span>
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
                <h3 className="text-xs text-gray-400 font-semibold mb-2">实例列表</h3>
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
                      {instLoading ? '加载中...' : `加载更多 (${instances.length} 个)`}
                    </button>
                  ) : (
                    instances.length > 0 && <div className="text-center py-1 text-[10px] text-gray-600">共 {instances.length} 个实例</div>
                  )}
                  {instances.length === 0 && <div className="text-xs text-gray-600">暂无实例</div>}
                </div>
              </div>

              {/* Timeline */}
              <div className="flex-1">
                <h3 className="text-xs text-gray-400 font-semibold mb-2">步骤耗时</h3>
                {!selectedInstId && <div className="text-xs text-gray-600">点击实例查看耗时</div>}
                {timeline && timeline.length === 0 && <div className="text-xs text-gray-500">无历史记录</div>}
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
    <div className="bg-gray-750 rounded p-3 text-center">
      <div className={`text-2xl font-bold ${color}`}>{value}</div>
      <div className="text-[10px] text-gray-500">{label}</div>
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
