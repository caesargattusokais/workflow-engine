import { useState, useEffect } from 'react';
import { useT } from '../i18n';

interface Instance {
  id: string; definitionId: string; definitionVersion: number; status: string;
  variables: Record<string, unknown>;
  activeNodeIds: string[];
}

export default function InstanceList({ onSelect, selectedId, instances, defNames, onTerminate, onResume, onDelete, onRestart, onScrollToBottom, hasMore, loading }:
    { onSelect: (id: string) => void; selectedId: string | null; instances: Instance[]; defNames: Record<string, string>;
      onTerminate: (id: string) => void; onResume: (id: string) => void;
      onDelete: (id: string) => void; onRestart: (id: string) => void;
      onScrollToBottom?: () => void; hasMore?: boolean; loading?: boolean; }) {

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

  // Group instances by definitionId
  const grouped: Record<string, Instance[]> = {};
  for (const inst of instances) {
    (grouped[inst.definitionId] ||= []).push(inst);
  }

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

  return (
    <div className="w-56 bg-gray-800 border-r border-gray-700 p-2 overflow-y-auto"
      onScroll={(e) => {
        if (!onScrollToBottom) return;
        const el = e.currentTarget;
        if (el.scrollHeight - el.scrollTop - el.clientHeight < 50) onScrollToBottom();
      }}>
      <div className="text-xs text-gray-500 mb-2">{t.monitor.instances}</div>
      {instances.length === 0 && (
        <div className="text-gray-600 text-xs">{t.monitor.noInstances}</div>
      )}
      {Object.entries(grouped).map(([defId, insts]) => (
        <div key={defId} className="mb-2">
          <div className="text-[10px] text-gray-500 px-1 mb-0.5 font-semibold truncate cursor-pointer hover:text-gray-300"
            onClick={() => setCollapsed(prev => ({...prev, [defId]: !prev[defId]}))}>
            {collapsed[defId] ? '▸' : '▾'} {defNames[defId] || defId} ({insts.length})
          </div>
          {!collapsed[defId] && insts.map(inst => (
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
        </div>
      ))}

      {/* Load more button */}
      {hasMore && (
        <button onClick={onScrollToBottom}
          disabled={loading}
          className="w-full text-center py-1.5 text-xs text-blue-400 hover:bg-gray-700 rounded disabled:text-gray-600">
          {loading ? '加载中...' : '加载更多'}
        </button>
      )}

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
