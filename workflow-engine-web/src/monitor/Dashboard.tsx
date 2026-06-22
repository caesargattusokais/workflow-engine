import { useState, useEffect } from 'react';
import { apiGet } from '../api/client';

export default function Dashboard() {
  const [stats, setStats] = useState<any>(null);

  useEffect(() => {
    apiGet('/dashboard/stats').then(setStats).catch(() => {});
  }, []);

  if (!stats) return <div className="p-4 text-gray-500 text-sm">加载中...</div>;

  const total = stats.total || 1;
  const pct = (n: number) => Math.round((n / total) * 100);

  return (
    <div className="p-4 overflow-y-auto h-full">
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
            style={{ width: pct(stats.completed) + '%' }}>
            {pct(stats.completed)}%
          </div>
          <div className="bg-yellow-500 h-full text-[10px] text-white flex items-center justify-center"
            style={{ width: pct(stats.suspended) + '%' }} />
          <div className="bg-red-500 h-full text-[10px] text-white flex items-center justify-center"
            style={{ width: pct(stats.terminated) + '%' }} />
          <div className="bg-blue-500 h-full text-[10px] text-white flex items-center justify-center"
            style={{ width: pct(stats.running) + '%' }}>
            {pct(stats.running)}%
          </div>
        </div>
        <div className="flex gap-3 text-[10px] text-gray-500 mt-1">
          <span><span className="inline-block w-2 h-2 bg-green-500 rounded mr-1" />完成</span>
          <span><span className="inline-block w-2 h-2 bg-blue-500 rounded mr-1" />运行</span>
          <span><span className="inline-block w-2 h-2 bg-yellow-500 rounded mr-1" />挂起</span>
        </div>
      </div>

      {/* Per-definition */}
      <div className="mb-6">
        <h3 className="text-xs text-gray-400 font-semibold mb-2">按流程定义</h3>
        <table className="w-full text-xs">
          <thead><tr className="text-gray-500">
            <th className="text-left py-1">定义ID</th>
            <th className="text-right">总计</th>
            <th className="text-right">运行</th>
            <th className="text-right">完成</th>
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
          {Object.entries(stats.workload || {}).sort((a: any, b: any) => b[1] - a[1]).map(([assignee, count]: [string, any]) => (
            <div key={assignee} className="bg-gray-750 rounded px-2 py-1 text-xs text-gray-300">
              {assignee} <span className="text-blue-400 font-bold ml-1">{count}</span>
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
