import { useState, useEffect } from 'react';
import { useT } from '../i18n';

interface Instance {
  id: string; definitionId: string; definitionVersion: number; status: string;
  variables: Record<string, unknown>;
  activeNodeIds: string[];
}

interface DefGroup {
  defId: string;
  defName: string;
  instances: Instance[];
  instHasMore: boolean;
  instLoading: boolean;
}

export default function InstanceList({ onSelect, selectedId, groups, onTerminate, onResume, onDelete, onRestart,
    onLoadInstances, defHasMore, defLoading, onLoadMoreDefs, onRefresh }:
    { onSelect: (id: string) => void; selectedId: string | null; groups: DefGroup[];
      onTerminate: (id: string) => void; onResume: (id: string) => void;
      onDelete: (id: string) => void; onRestart: (id: string) => void;
      onLoadInstances: (defId: string) => void;
      defHasMore: boolean; defLoading: boolean; onLoadMoreDefs: () => void;
      onRefresh?: () => void; }) {

  const { t } = useT();
  const [menu, setMenu] = useState<{x:number;y:number;inst:Instance}|null>(null);
  const [collapsed, setCollapsed] = useState<Record<string,boolean>>({});

  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  const onContextMenu = (e: React.MouseEvent, inst: Instance) => {
    e.preventDefault();
    setMenu({ x: e.clientX, y: e.clientY, inst });
  };

  const statusDot = (s: string) => {
    const colors: Record<string, string> = { RUNNING: 'bg-green-500', COMPLETED: 'bg-blue-500', SUSPENDED: 'bg-yellow-500', TERMINATED: 'bg-red-500' };
    return colors[s] || 'bg-gray-500';
  };

  const statusLabel = (s: string) => {
    const m: Record<string, string> = {
      RUNNING: t.monitor.running,
      COMPLETED: t.monitor.completed,
      TERMINATED: t.monitor.terminated,
      SUSPENDED: t.monitor.suspended,
    };
    return m[s] || s;
  };

  const totalInstances = groups.reduce((sum, g) => sum + g.instances.length, 0);

  return (
    <div className="w-56 bg-gray-800 border-r border-gray-700 p-2 overflow-y-auto"
      onScroll={(e) => {
        const el = e.currentTarget;
        if (el.scrollHeight - el.scrollTop - el.clientHeight < 50) {
          // Try loading more definitions when scrolled to bottom
          if (defHasMore && !defLoading) onLoadMoreDefs();
        }
      }}>
      <div className="text-xs text-gray-500 mb-2 flex items-center justify-between">
        <span>{t.monitor.instances} ({totalInstances})</span>
        {onRefresh && (
          <button onClick={onRefresh} className="text-gray-500 hover:text-gray-300 text-[10px]" title={t.monitor.refresh}>
            {t.monitor.refresh}
          </button>
        )}
      </div>
      {groups.length === 0 && (
        <div className="text-gray-600 text-xs">{t.monitor.noInstances}</div>
      )}
      {groups.map(g => (
        <div key={g.defId} className="mb-2">
          <div className="text-[10px] text-gray-500 px-1 mb-0.5 font-semibold truncate cursor-pointer hover:text-gray-300"
            onClick={() => setCollapsed(prev => ({...prev, [g.defId]: !prev[g.defId]}))}>
            {collapsed[g.defId] ? '▸' : '▾'} {g.defName} ({g.instances.length})
          </div>
          {!collapsed[g.defId] && (
            <>
              {g.instances.map(inst => (
                <div key={inst.id}
                  onClick={() => onSelect(inst.id)}
                  onContextMenu={(e) => onContextMenu(e, inst)}
                  className={`p-1.5 rounded mb-0.5 cursor-pointer text-xs ml-1
                    ${selectedId === inst.id ? 'bg-blue-600' : 'bg-gray-750 hover:bg-gray-600'}`}
                >
                  <div className="flex items-center gap-1">
                    <span className={`w-2 h-2 rounded-full inline-block ${statusDot(inst.status)}`} />
                    <span className="text-gray-300">{inst.id.substring(0, 7)}</span>
                    <span className="text-[10px] text-gray-500">{statusLabel(inst.status)}</span>
                    {inst.definitionVersion > 0 && <span className="text-[9px] text-gray-600 ml-0.5">v{inst.definitionVersion}</span>}
                  </div>
                </div>
              ))}
              {/* Per-definition pagination */}
              {g.instHasMore ? (
                <button onClick={() => onLoadInstances(g.defId)}
                  disabled={g.instLoading}
                  className="w-full text-center py-1 text-xs text-blue-400 hover:bg-gray-700 rounded disabled:text-gray-600 ml-1">
                  {g.instLoading ? '加载中...' : `加载更多 (已显示 ${g.instances.length})`}
                </button>
              ) : (
                <div className="text-[10px] text-gray-600 ml-1 py-0.5">
                  {g.instances.length > 0 ? `共 ${g.instances.length} 个实例` : '无实例'}
                </div>
              )}
            </>
          )}
        </div>
      ))}

      {/* Pagination footer */}
      <div className="border-t border-gray-700 mt-1 pt-1">
        {defHasMore ? (
          <button onClick={onLoadMoreDefs}
            disabled={defLoading}
            className="w-full text-center py-1.5 text-xs text-purple-400 hover:bg-gray-700 rounded disabled:text-gray-600">
            {defLoading ? '加载中...' : `加载更多定义 (当前 ${groups.length} 个)`}
          </button>
        ) : (
          <div className="text-center py-1 text-[10px] text-gray-600">
            共 {groups.length} 个流程定义
          </div>
        )}
      </div>

      {/* Context menu */}
      {menu && (
        <div className="fixed z-50 bg-gray-800 border border-gray-600 rounded shadow-xl py-1 min-w-[130px]"
          style={{ left: menu.x, top: menu.y }} onClick={e => e.stopPropagation()}>
          <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
            {menu.inst.id.substring(0,8)} — {statusLabel(menu.inst.status)}
          </div>
          {(menu.inst.status === 'RUNNING' || menu.inst.status === 'SUSPENDED') && (
            <button onClick={() => { onTerminate(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700">
              {t.monitor.terminate}
            </button>
          )}
          {menu.inst.status === 'SUSPENDED' && (
            <button onClick={() => { onResume(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-sm text-yellow-400 hover:bg-gray-700">
              {t.monitor.resume}
            </button>
          )}
          {(menu.inst.status === 'COMPLETED' || menu.inst.status === 'TERMINATED') && (
            <>
              <button onClick={() => { onRestart(menu.inst.id); setMenu(null); }}
                className="w-full text-left px-3 py-1.5 text-sm text-green-400 hover:bg-gray-700">
                Restart
              </button>
              <button onClick={() => { onDelete(menu.inst.id); setMenu(null); }}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-400 hover:bg-gray-700">
                {t.designer.delete}
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
