import { useState, useEffect, useRef, useCallback } from 'react';
import { apiGet, listInstances } from '../api/client';

export default function Dashboard() {
  const [stats, setStats] = useState<any>(null);
  const [instances, setInstances] = useState<any[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [timeline, setTimeline] = useState<any[] | null>(null);
  const [instPage, setInstPage] = useState(1);
  const [instHasMore, setInstHasMore] = useState(true);
  const [instLoadingState, setInstLoadingState] = useState(false);
  const instLoading = useRef(false);

  const loadInstances = useCallback(async (page: number) => {
    if (instLoading.current) return;
    instLoading.current = true;
    setInstLoadingState(true);
    try {
      const r: any = await listInstances(page, 10);
      const list = r.items || r;
      setInstHasMore(list.length >= 10);
      if (page === 1) {
        setInstances(list);
      } else {
        setInstances(prev => [...prev, ...list]);
      }
      setInstPage(page);
    } catch {} finally {
      instLoading.current = false;
      setInstLoadingState(false);
    }
  }, []);

  useEffect(() => {
    apiGet('/dashboard/stats').then(setStats).catch(() => {});
    loadInstances(1);
  }, [loadInstances]);

  const loadTimeline = async (id: string) => {
    setSelectedId(id);
    const data = await apiGet(`/dashboard/timeline/${id}`);
    setTimeline(data);
  };

  if (!stats) return <div className="p-4 text-gray-500 text-sm">加载中...</div>;

  const total = stats.total || 1;
  const pct = (n: number) => Math.round((n / total) * 100);

  return (
    <div className="p-4 overflow-y-auto h-full flex gap-4">
      <div className="flex-1">
        <h2 className="text-gray-200 font-bold mb-4">数据看板</h2>

        {/* KPI cards */}
        <div className="grid grid-cols-4 gap-3 mb-6">
          <Kpi label="总实例" value={stats.total} color="text-blue-400" />
          <Kpi label="运行中" value={stats.running} color="text-green-400" />
          <Kpi label="已完成" value={stats.completed} color="text-gray-300" />
          <Kpi label="挂起" value={stats.suspended} color="text-yellow-400" />
        </div>

        {/* Progress bar */}
        <div className="bg-gray-750 rounded p-3 mb-6">
          <div className="text-xs text-gray-400 mb-2">
            平均耗时 <span className="text-gray-200 font-bold">{formatDuration(stats.avgDurationMs)}</span>
          </div>
          <div className="w-full h-4 bg-gray-700 rounded-full overflow-hidden flex">
            <div className="bg-green-500 h-full text-[10px] text-white flex items-center justify-center"
              style={{ width: pct(stats.completed) + '%' }}>{pct(stats.completed)}%</div>
            <div className="bg-yellow-500 h-full text-[10px] text-white flex items-center justify-center"
              style={{ width: pct(stats.suspended) + '%' }} />
            <div className="bg-red-500 h-full text-[10px] text-white flex items-center justify-center"
              style={{ width: pct(stats.terminated) + '%' }} />
            <div className="bg-blue-500 h-full text-[10px] text-white flex items-center justify-center"
              style={{ width: pct(stats.running) + '%' }}>{pct(stats.running)}%</div>
          </div>
        </div>

        {/* Per-definition */}
        <div className="mb-6">
          <h3 className="text-xs text-gray-400 font-semibold mb-2">按流程定义</h3>
          <table className="w-full text-xs">
            <thead><tr className="text-gray-500">
              <th className="text-left py-1">定义ID</th><th className="text-right">总计</th>
              <th className="text-right">运行</th><th className="text-right">完成</th>
            </tr></thead>
            <tbody>
              {Object.entries(stats.byDefinition || {}).map(([defId, counts]: [string, any]) => (
                <tr key={defId} className="border-t border-gray-700">
                  <td className="py-1 text-gray-300">{defId}</td>
                  <td className="text-right text-gray-400">{sum(counts)}</td>
                  <td className="text-right text-green-400">{counts.RUNNING || 0}</td>
                  <td className="text-right text-blue-400">{counts.COMPLETED || 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Workload */}
        <div>
          <h3 className="text-xs text-gray-400 font-semibold mb-2">审批人工作量</h3>
          <div className="flex flex-wrap gap-2">
            {Object.entries(stats.workload || {}).sort((a: any, b: any) => b[1] - a[1]).map(([a, c]: [string, any]) => (
              <div key={a} className="bg-gray-750 rounded px-2 py-1 text-xs text-gray-300">
                {a} <span className="text-blue-400 font-bold ml-1">{c}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Instance Timeline Panel ── */}
      <div className="w-80 bg-gray-800 rounded p-3 flex flex-col" style={{ minHeight: 0 }}>
        <h3 className="text-xs text-gray-400 font-semibold mb-2">实例耗时分析</h3>
        <div className="flex-1 overflow-y-auto mb-2"
          onScroll={(e) => {
            const el = e.currentTarget;
            if (el.scrollHeight - el.scrollTop - el.clientHeight < 50 && instHasMore && !instLoading.current) {
              loadInstances(instPage + 1);
            }
          }}>
          {instances.map((inst: any) => (
            <div key={inst.id}
              onClick={() => loadTimeline(inst.id)}
              className={`p-1.5 rounded mb-0.5 cursor-pointer text-xs flex items-center gap-1.5
                ${selectedId === inst.id ? 'bg-blue-600/30' : 'hover:bg-gray-750'}`}>
              <span className={`w-1.5 h-1.5 rounded-full ${
                inst.status === 'RUNNING' ? 'bg-green-500' :
                inst.status === 'COMPLETED' ? 'bg-blue-500' :
                inst.status === 'SUSPENDED' ? 'bg-yellow-500' : 'bg-red-500'}`} />
              <span className="text-gray-300 truncate flex-1">{inst.id.substring(0,8)}</span>
              <span className="text-[10px] text-gray-500">{inst.definitionId}</span>
            </div>
          ))}
          {instHasMore && (
            <button onClick={() => loadInstances(instPage + 1)}
              disabled={instLoadingState}
              className="w-full text-center py-1.5 text-xs text-blue-400 hover:bg-gray-700 rounded disabled:text-gray-600">
              {instLoadingState ? '加载中...' : '加载更多'}
            </button>
          )}
        </div>
        <div className="border-t border-gray-700 pt-2 flex-1 overflow-y-auto">
          {!selectedId && <div className="text-xs text-gray-600 text-center mt-2">点击左侧实例查看耗时</div>}
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

function sum(obj: any) { return Object.values(obj || {}).reduce((a: number, b: any) => a + b, 0) as number; }

function formatDuration(ms: number) {
  if (ms < 1000) return ms + 'ms';
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
  if (ms < 3600000) return (ms / 60000).toFixed(1) + 'min';
  return (ms / 3600000).toFixed(1) + 'h';
}
