import { useEffect, useState } from 'react';
import type { Node, Edge } from '@xyflow/react';
import { useT } from '../i18n';

interface Draft {
  id: string;
  name: string;
  version: number;
  nodes: Node[];
  edges: Edge[];
  createdAt: number;
}

/** Left sidebar: draft list with infinite scroll, toolbar and context menu. */
export default function DraftList({ drafts, activeId, instanceSummary,
    draftHasMore, draftLoading, draftPage,
    onSwitch, onNew, onImport, onLoadTemplates, onLoadMore, onLoadPage,
    onViewYaml, onDownloadYaml, onCopy, onRename, onDelete }: {
  drafts: Draft[];
  activeId: string | null;
  instanceSummary: Record<string, { running: number, total: number }>;
  draftHasMore: boolean;
  draftLoading: boolean;
  draftPage: number;
  onSwitch: (id: string) => void;
  onNew: () => void;
  onImport: () => void;
  onLoadTemplates: () => void;
  onLoadMore: () => void;
  onLoadPage: (page: number) => void;
  onViewYaml: (draft: Draft) => void;
  onDownloadYaml: (draft: Draft) => void;
  onCopy: (id: string) => void;
  onRename: (id: string) => void;
  onDelete: (id: string) => void;
}) {
  const { t } = useT();
  const [draftMenu, setDraftMenu] = useState<{x:number;y:number;draft:Draft}|null>(null);

  // Close draft context menu on click outside
  useEffect(() => {
    const close = () => setDraftMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  return (
    <div className="w-40 bg-gray-850 border-r border-gray-700 flex flex-col">
      <div className="p-2 border-b border-gray-700 flex items-center gap-1">
        <span className="text-xs text-gray-500 flex-1">{t.designer.draftList}</span>
        <button onClick={onLoadTemplates}
          className="text-xs bg-purple-600 hover:bg-purple-500 text-white px-1.5 py-0.5 rounded" title={t.designer.template}>
          T
        </button>
        <button onClick={onImport}
          className="text-xs bg-teal-600 hover:bg-teal-500 text-white px-1.5 py-0.5 rounded" title={t.designer.importYaml}>
          {t.designer.import}
        </button>
        <button onClick={onNew}
          className="text-xs bg-blue-600 hover:bg-blue-500 text-white px-1.5 py-0.5 rounded">
          {t.designer.newDraft}
        </button>
      </div>
      <div className="flex-1 overflow-y-auto"
        onScroll={(e) => {
          const el = e.currentTarget;
          if (el.scrollHeight - el.scrollTop - el.clientHeight < 50 && draftHasMore && !draftLoading) {
            onLoadPage(draftPage + 1);
          }
        }}>
        {drafts.map(d => (
          <div key={d.id}
            onClick={() => onSwitch(d.id)}
            onContextMenu={(e) => { e.preventDefault(); setDraftMenu({x:e.clientX, y:e.clientY, draft:d}); }}
            className={`px-2 py-1.5 cursor-pointer border-b border-gray-800 text-xs flex justify-between items-center group
              ${d.id === activeId ? 'bg-blue-600/30 border-l-2 border-l-blue-500' : 'hover:bg-gray-700'}`}>
            <div className="truncate flex-1">
              <div className="text-gray-300 truncate">{d.name} <span className="text-[10px] text-gray-600">v{d.version || 1}</span></div>
              <div className="text-[10px] text-gray-600">
                {d.nodes.length} nodes
                {(() => {
                  const s = instanceSummary[d.id];
                  if (s && s.total > 0) {
                    return <span className="ml-1">| <span className="text-green-500">{s.running || 0} running</span> / {s.total} total</span>;
                  }
                  return null;
                })()}
              </div>
            </div>
            <div className="hidden group-hover:flex gap-0.5 ml-1">
              <button onClick={(e) => { e.stopPropagation(); onRename(d.id); }}
                className="text-gray-500 hover:text-gray-300 text-[10px]" title={t.designer.rename}>✎</button>
              <button onClick={(e) => { e.stopPropagation(); onDelete(d.id); }}
                className="text-gray-500 hover:text-red-400 text-[10px]" title={t.designer.delete}>✕</button>
            </div>
          </div>
        ))}
        {drafts.length === 0 && (
          <div className="p-3 text-xs text-gray-600 text-center">
            {t.designer.clickNew}
          </div>
        )}
        {draftHasMore ? (
          <button onClick={() => onLoadPage(draftPage + 1)}
            disabled={draftLoading}
            className="w-full text-center py-1.5 text-xs text-blue-400 hover:bg-gray-700 disabled:text-gray-600">
            {draftLoading ? t.dashboard.loading : t.designer.loadMoreCount.replace('{n}', String(drafts.length))}
          </button>
        ) : (
          drafts.length > 0 && <div className="text-center py-1 text-[10px] text-gray-600">{t.designer.totalDrafts.replace('{n}', String(drafts.length))}</div>
        )}
      </div>
      {/* Draft context menu */}
      {draftMenu && (
        <div className="fixed z-50 bg-gray-800 border border-gray-600 rounded shadow-xl py-1 min-w-[130px]"
          style={{ left: draftMenu.x, top: draftMenu.y }} onClick={e => e.stopPropagation()}>
          <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
            {draftMenu.draft.name}
          </div>
          <button onClick={() => { onViewYaml(draftMenu.draft); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
            {t.designer.viewYaml}
          </button>
          <button onClick={() => { onDownloadYaml(draftMenu.draft); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
            {t.designer.downloadYaml}
          </button>
          <div className="border-t border-gray-700" />
          <button onClick={() => { onCopy(draftMenu.draft.id); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
            {t.designer.copy}
          </button>
          <button onClick={() => { onRename(draftMenu.draft.id); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
            {t.designer.rename}
          </button>
          <button onClick={() => { onDelete(draftMenu.draft.id); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700">
            {t.designer.delete}
          </button>
        </div>
      )}
    </div>
  );
}
