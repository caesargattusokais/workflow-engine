import { useState, useEffect } from 'react';
import { useT } from '../i18n';
import { RefreshCw, ChevronDown, ChevronRight, Layers3, Inbox, PlayCircle, Trash2, Square, RotateCcw } from 'lucide-react';

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

const STATUSES = ['', 'RUNNING', 'COMPLETED', 'SUSPENDED', 'TERMINATED'];

export default function InstanceList({ onSelect, selectedId, groups, onTerminate, onResume, onDelete, onRestart,
    onLoadInstances, defHasMore, defLoading, onLoadMoreDefs, onRefresh, statusFilter, onStatusChange }:
    { onSelect: (id: string) => void; selectedId: string | null; groups: DefGroup[];
      onTerminate: (id: string) => void; onResume: (id: string) => void;
      onDelete: (id: string) => void; onRestart: (id: string) => void;
      onLoadInstances: (defId: string) => void;
      defHasMore: boolean; defLoading: boolean; onLoadMoreDefs: () => void;
      onRefresh?: () => void;
      statusFilter?: string; onStatusChange?: (s: string) => void; }) {

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
    const colors: Record<string, string> = { RUNNING: 'bg-emerald-400', COMPLETED: 'bg-blue-400', SUSPENDED: 'bg-amber-400', TERMINATED: 'bg-red-400' };
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
    <div className="w-60 bg-gray-850 border-r border-gray-700/70 p-2.5 overflow-y-auto"
      onScroll={(e) => {
        const el = e.currentTarget;
        if (el.scrollHeight - el.scrollTop - el.clientHeight < 50) {
          if (defHasMore && !defLoading) onLoadMoreDefs();
        }
      }}>
      <div className="flex items-center gap-2 mb-2.5 px-1">
        <Layers3 size={13} className="text-gray-500" strokeWidth={2} />
        <span className="text-[11px] uppercase tracking-wider text-gray-400 font-medium flex-1">
          {t.monitor.instances} ({totalInstances})
        </span>
        {onRefresh && (
          <button onClick={onRefresh} className="btn-icon !w-6 !h-6" title={t.monitor.refresh}>
            <RefreshCw size={12} strokeWidth={2} />
            <span className="sr-only">{t.monitor.refresh}</span>
          </button>
        )}
      </div>
      {onStatusChange && (
        <div className="flex flex-wrap gap-1 mb-3">
          {STATUSES.map(s => (
            <button key={s} onClick={() => onStatusChange(s)}
              className={`text-[10px] px-2 py-0.5 rounded-full font-medium transition-colors
                ${(statusFilter || '') === s
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-500 hover:bg-gray-700 hover:text-gray-300'}`}>
              {s || 'ALL'}
            </button>
          ))}
        </div>
      )}
      {groups.length === 0 && (
        <div className="text-gray-600 text-[11px] flex flex-col items-center py-8">
          <Inbox size={20} strokeWidth={1.5} className="mb-2 text-gray-700" />
          {t.monitor.noInstances}
        </div>
      )}
      {groups.map(g => (
        <div key={g.defId} className="mb-3">
          <div className="text-[11px] text-gray-500 px-1 mb-1 font-medium truncate cursor-pointer hover:text-gray-300 flex items-center gap-1"
            onClick={() => setCollapsed(prev => ({...prev, [g.defId]: !prev[g.defId]}))}>
            {collapsed[g.defId] ? <ChevronRight size={11} /> : <ChevronDown size={11} />}
            <span className="truncate">{g.defName}</span>
            <span className="text-gray-700 font-mono">({g.instances.length})</span>
          </div>
          {!collapsed[g.defId] && (
            <>
              {g.instances.map(inst => (
                <div key={inst.id}
                  onClick={() => onSelect(inst.id)}
                  onContextMenu={(e) => onContextMenu(e, inst)}
                  className={`p-1.5 rounded mb-0.5 cursor-pointer text-xs ml-1 border transition-colors
                    ${selectedId === inst.id
                      ? 'bg-blue-600 border-blue-500/50 text-white'
                      : 'bg-gray-750 border-transparent hover:bg-gray-700'}`}
                >
                  <div className="flex items-center gap-1.5">
                    <span className={`w-1.5 h-1.5 rounded-full inline-block shrink-0 ${statusDot(inst.status)} ${selectedId === inst.id ? 'ring-2 ring-white/20' : ''}`} />
                    <span className={`font-mono truncate ${selectedId === inst.id ? 'text-white' : 'text-gray-300'}`}>{inst.id.substring(0, 7)}</span>
                    <span className={`text-[10px] ml-auto shrink-0 ${selectedId === inst.id ? 'text-blue-100' : 'text-gray-500'}`}>{statusLabel(inst.status)}</span>
                    {inst.definitionVersion > 0 && <span className="text-[10px] text-gray-600 font-mono shrink-0">v{inst.definitionVersion}</span>}
                  </div>
                </div>
              ))}
              {g.instHasMore ? (
                <button onClick={() => onLoadInstances(g.defId)}
                  disabled={g.instLoading}
                  className="w-full text-center py-1.5 text-[11px] text-blue-400 hover:bg-gray-800 rounded-md disabled:text-gray-600 ml-1 transition-colors">
                  {g.instLoading ? t.dashboard.loading : t.designer.loadMoreShown.replace('{n}', String(g.instances.length))}
                </button>
              ) : (
                <div className="text-[10px] text-gray-600 ml-1 py-0.5">
                  {g.instances.length > 0 ? t.designer.totalInstances.replace('{n}', String(g.instances.length)) : t.designer.noInstances}
                </div>
              )}
            </>
          )}
        </div>
      ))}

      <div className="border-t border-gray-700/70 mt-2 pt-2">
        {defHasMore ? (
          <button onClick={onLoadMoreDefs}
            disabled={defLoading}
            className="w-full text-center py-1.5 text-[11px] text-purple-400 hover:bg-gray-800 rounded-md disabled:text-gray-600 transition-colors">
            {defLoading ? t.dashboard.loading : t.designer.loadMoreDefs.replace('{n}', String(groups.length))}
          </button>
        ) : (
          <div className="text-center py-1 text-[10px] text-gray-600">
            {t.designer.totalDefs.replace('{n}', String(groups.length))}
          </div>
        )}
      </div>

      {menu && (
        <div className="fixed z-50 bg-gray-800 border border-gray-700 rounded-lg shadow-popover py-1 min-w-[150px]"
          style={{ left: menu.x, top: menu.y }} onClick={e => e.stopPropagation()}>
          <div className="px-3 py-1.5 text-[10px] text-gray-500 border-b border-gray-700 font-mono">
            {menu.inst.id.substring(0,8)} — {statusLabel(menu.inst.status)}
          </div>
          {(menu.inst.status === 'RUNNING' || menu.inst.status === 'SUSPENDED') && (
            <button onClick={() => { onTerminate(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-xs text-red-400 hover:bg-gray-700 flex items-center gap-2">
              <Square size={12} /> {t.monitor.terminate}
            </button>
          )}
          {menu.inst.status === 'SUSPENDED' && (
            <button onClick={() => { onResume(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-xs text-amber-400 hover:bg-gray-700 flex items-center gap-2">
              <PlayCircle size={12} /> {t.monitor.resume}
            </button>
          )}
          {(menu.inst.status === 'COMPLETED' || menu.inst.status === 'TERMINATED') && (
            <>
              <button onClick={() => { onRestart(menu.inst.id); setMenu(null); }}
                className="w-full text-left px-3 py-1.5 text-xs text-emerald-400 hover:bg-gray-700 flex items-center gap-2">
                <RotateCcw size={12} /> Restart
              </button>
              <button onClick={() => { onDelete(menu.inst.id); setMenu(null); }}
                className="w-full text-left px-3 py-1.5 text-xs text-gray-400 hover:bg-gray-700 flex items-center gap-2">
                <Trash2 size={12} /> {t.designer.delete}
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
