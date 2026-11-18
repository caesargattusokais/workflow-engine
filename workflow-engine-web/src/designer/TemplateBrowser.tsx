import { useT } from '../i18n';

/** Modal browser for bundled YAML process templates. */
export default function TemplateBrowser({ templates, onImport, onClose }: {
  templates: any[];
  onImport: (file: string) => void;
  onClose: () => void;
}) {
  const { t } = useT();
  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center"
      onClick={onClose}>
      <div className="bg-gray-800 rounded-lg shadow-2xl w-[600px] max-h-[80vh] overflow-y-auto p-6"
        onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg text-gray-200 font-bold">{t.designer.processTemplates}</h2>
          <button onClick={onClose}
            className="text-gray-400 hover:text-white text-xl">&times;</button>
        </div>
        <div className="grid grid-cols-1 gap-3">
          {templates.map((tpl: any) => (
            <div key={tpl.file} className="bg-gray-750 border border-gray-600 rounded-lg p-4 hover:border-purple-500 transition-colors">
              <div className="flex items-center justify-between mb-1">
                <span className="text-gray-200 font-semibold">{tpl.name}</span>
                <button onClick={() => onImport(tpl.file)}
                  className="bg-purple-600 hover:bg-purple-500 text-white text-xs px-3 py-1 rounded">
                  {t.designer.useTemplate}
                </button>
              </div>
              <div className="text-xs text-gray-500">{tpl.desc}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
