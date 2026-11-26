import { useEffect, useState } from 'react';
import type { Node, Edge } from '@xyflow/react';
import { Sparkles, LayoutTemplate, Upload, Plus, Pencil, X, FileCode, Download, Copy, Trash2 } from 'lucide-react';
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
    onSwitch, onNew, onImport, onLoadTemplates, onAiGenerate, onLoadMore, onLoadPage,
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
  onAiGenerate: () => void;
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

  useEffect(() => {
    const close = () => setDraftMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  return (
    <div className="w-52 shrink-0 bg-gray-850 border-r border-gray-700/70 flex flex-col">
      <div className="p-2.5 border-b border-gray-700/70 flex items-center gap-1">
        <span className="text-[11px] uppercase tracking-wider text-gray-500 font-medium flex-1 min-w-0 truncate">
          {t.designer.draftList}
        </span>
        <button onClick={onAiGenerate}
          className="btn-icon !w-6 !h-6 bg-fuchsia-600/90 hover:bg-fuchsia-500 text-white"
          title={t.designer.aiGenerateTitle}>
          <Sparkles size={13} strokeWidth={2.2} />
          <span className="sr-only">✦</span>
        </button>
        <button onClick={onLoadTemplates}
          className="btn-icon !w-6 !h-6 bg-purple-600/90 hover:bg-purple-500 text-white"
          title={t.designer.template}>
          <LayoutTemplate size={13} strokeWidth={2.2} />
          <span className="sr-only">T</span>
        </button>
        <button onClick={onImport}
          className="btn-icon !w-6 !h-6 bg-teal-600/90 hover:bg-teal-500 text-white"
          title={t.designer.importYaml}>
          <Upload size={13} strokeWidth={2.2} />
          <span className="sr-only">{t.designer.import}</span>
        </button>
        <button onClick={onNew}
          className="btn-icon !w-6 !h-6 bg-blue-600 hover:bg-blue-500 text-white"
          title={t.designer.newDraft}>
          <Plus size={14} strokeWidth={2.2} />
          <span className="sr-only">{t.designer.newDraft}</span>
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
            data-draft-id={d.id}
            onClick={() => onSwitch(d.id)}
            onContextMenu={(e) => { e.preventDefault(); setDraftMenu({x:e.clientX, y:e.clientY, draft:d}); }}
            className={`px-2 py-2 cursor-pointer border-b border-gray-800/60 flex justify-between items-center group transition-colors
              ${d.id === activeId ? 'bg-blue-600/20 border-l-2 border-l-blue-500' : 'hover:bg-gray-800/70'}`}>
            <div className="truncate flex-1 min-w-0">
              <div className={`text-xs truncate font-medium text-gray-300 ${d.id === activeId ? '!text-gray-100' : ''}`}>
                {d.name} <span className="text-[10px] text-gray-600 font-normal">v{d.version || 1}</span>
              </div>
              <div className="text-[10px] text-gray-600 mt-0.5">
                {d.nodes.length} nodes
                {(() => {
                  const s = instanceSummary[d.id];
                  if (s && s.total > 0) {
                    return <span className="ml-1.5">· <span className="text-emerald-500">{s.running || 0} running</span> / {s.total}</span>;
                  }
                  return null;
                })()}
              </div>
            </div>
            <div className="hidden group-hover:flex gap-0.5 ml-1">
              <button onClick={(e) => { e.stopPropagation(); onRename(d.id); }}
                className="btn-icon !w-5 !h-5" title={t.designer.rename}
                aria-label={t.designer.rename}>
                <Pencil size={11} strokeWidth={2} />
              </button>
              <button onClick={(e) => { e.stopPropagation(); onDelete(d.id); }}
                className="btn-icon !w-5 !h-5 hover:!text-red-400" title={t.designer.delete}
                aria-label={t.designer.delete}>
                <Trash2 size={11} strokeWidth={2} />
              </button>
            </div>
          </div>
        ))}
        {drafts.length === 0 && (
          <div className="p-4 text-xs text-gray-600 text-center leading-relaxed">
            <LayoutTemplate size={22} strokeWidth={1.5} className="mx-auto mb-2 text-gray-700" />
            {t.designer.clickNew}
          </div>
        )}
        {draftHasMore ? (
          <button onClick={() => onLoadPage(draftPage + 1)}
            disabled={draftLoading}
            className="w-full text-center py-2 text-xs text-blue-400 hover:bg-gray-800 disabled:text-gray-600 transition-colors">
            {draftLoading ? t.dashboard.loading : t.designer.loadMoreCount.replace('{n}', String(drafts.length))}
          </button>
        ) : (
          drafts.length > 0 && <div className="text-center py-2 text-[10px] text-gray-600">{t.designer.totalDrafts.replace('{n}', String(drafts.length))}</div>
        )}
      </div>
      {draftMenu && (
        <div className="fixed z-50 bg-gray-800 border border-gray-700 rounded-lg shadow-popover py-1 min-w-[160px]"
          style={{ left: draftMenu.x, top: draftMenu.y }} onClick={e => e.stopPropagation()}>
          <div className="px-3 py-1.5 text-[11px] text-gray-500 border-b border-gray-700 truncate">
            {draftMenu.draft.name}
          </div>
          <button onClick={() => { onViewYaml(draftMenu.draft); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-xs text-gray-300 hover:bg-gray-700 hover:text-white flex items-center gap-2">
            <FileCode size={13} /> {t.designer.viewYaml}
          </button>
          <button onClick={() => { onDownloadYaml(draftMenu.draft); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-xs text-gray-300 hover:bg-gray-700 hover:text-white flex items-center gap-2">
            <Download size={13} /> {t.designer.downloadYaml}
          </button>
          <div className="border-t border-gray-700 my-1" />
          <button onClick={() => { onCopy(draftMenu.draft.id); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-xs text-gray-300 hover:bg-gray-700 hover:text-white flex items-center gap-2">
            <Copy size={13} /> {t.designer.copy}
          </button>
          <button onClick={() => { onRename(draftMenu.draft.id); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-xs text-gray-300 hover:bg-gray-700 hover:text-white flex items-center gap-2">
            <Pencil size={13} /> {t.designer.rename}
          </button>
          <button onClick={() => { onDelete(draftMenu.draft.id); setDraftMenu(null); }}
            className="w-full text-left px-3 py-1.5 text-xs text-red-400 hover:bg-gray-700 flex items-center gap-2">
            <Trash2 size={13} /> {t.designer.delete}
          </button>
        </div>
      )}
    </div>
  );
}
