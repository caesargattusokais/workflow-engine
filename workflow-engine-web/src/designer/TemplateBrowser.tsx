import { useT } from '../i18n';
import { LayoutTemplate, X, ArrowRight, FileText } from 'lucide-react';

/** Modal browser for bundled YAML process templates. */
export default function TemplateBrowser({ templates, onImport, onClose }: {
  templates: any[];
  onImport: (file: string) => void;
  onClose: () => void;
}) {
  const { t } = useT();
  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-6"
      onClick={onClose}>
      <div className="bg-gray-800 rounded-lg shadow-2xl w-[620px] max-h-[80vh] overflow-hidden flex flex-col border border-gray-700"
        onClick={e => e.stopPropagation()}>
        <div className="flex items-center gap-3 px-5 py-4 border-b border-gray-700/70">
          <div className="w-8 h-8 rounded-lg bg-purple-600/20 text-purple-400 flex items-center justify-center">
            <LayoutTemplate size={16} strokeWidth={2} />
          </div>
          <h2 className="text-sm font-semibold text-gray-100 flex-1">{t.designer.processTemplates}</h2>
          <button onClick={onClose} className="btn-icon" aria-label="Close">
            <X size={16} strokeWidth={2} />
          </button>
        </div>
        <div className="grid grid-cols-1 gap-2 p-4 overflow-y-auto">
          {templates.map((tpl: any) => (
            <div key={tpl.file} className="group bg-gray-750/60 border border-gray-700/60 rounded-lg p-3.5
                       hover:border-purple-500/60 hover:bg-gray-750 transition-all flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-gray-800 border border-gray-700 flex items-center justify-center text-gray-400 shrink-0">
                <FileText size={16} strokeWidth={1.8} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-xs font-medium text-gray-200 truncate">{tpl.name}</div>
                <div className="text-[11px] text-gray-500 mt-0.5 leading-relaxed">{tpl.desc}</div>
              </div>
              <button onClick={() => onImport(tpl.file)}
                className="btn-ghost hover:!bg-purple-600 hover:!text-white shrink-0">
                {t.designer.useTemplate}
                <ArrowRight size={12} strokeWidth={2} />
              </button>
            </div>
          ))}
          {templates.length === 0 && (
            <div className="text-center text-xs text-gray-500 py-10">No templates found</div>
          )}
        </div>
      </div>
    </div>
  );
}
