import { useState } from 'react';
import { Sparkles, X, Loader2, CornerDownLeft, Wand2 } from 'lucide-react';
import { useT } from '../i18n';

/** Modal for AI flow generation — natural language to a draft on the canvas. */
export default function AiGenerateModal({ onGenerate, onClose }: {
  onGenerate: (prompt: string) => Promise<void>;
  onClose: () => void;
}) {
  const { t } = useT();
  const [prompt, setPrompt] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!prompt.trim() || busy) return;
    setBusy(true);
    try {
      await onGenerate(prompt.trim());
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-6"
      onClick={onClose}>
      <div className="bg-gray-800 rounded-lg shadow-2xl w-[580px] border border-gray-700"
        onClick={e => e.stopPropagation()}>
        <div className="flex items-center gap-3 px-5 py-4 border-b border-gray-700/70">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-fuchsia-600/30 to-purple-600/20
                          border border-fuchsia-500/30 flex items-center justify-center text-fuchsia-400">
            <Wand2 size={16} strokeWidth={2} />
          </div>
          <h2 className="text-sm font-semibold text-gray-100 flex-1">{t.designer.aiGenerateTitle}</h2>
          <button onClick={onClose} className="btn-icon" aria-label="Close">
            <X size={16} strokeWidth={2} />
          </button>
        </div>
        <div className="p-5">
          <textarea
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit(); }}
            placeholder={t.designer.aiPromptPlaceholder}
            rows={5}
            autoFocus
            className="w-full bg-gray-900/70 border border-gray-700 rounded-lg p-3.5 text-xs text-gray-200
              placeholder:text-gray-600 focus:border-purple-500 transition-colors resize-none leading-relaxed" />
          <div className="flex items-center justify-between mt-4">
            <span className="text-[11px] text-gray-600 flex items-center gap-1.5">
              <CornerDownLeft size={12} /> Ctrl + Enter
            </span>
            <button onClick={submit} disabled={busy || !prompt.trim()}
              className="bg-purple-600 hover:bg-purple-500 disabled:bg-gray-700 disabled:text-gray-500
                text-white text-xs px-4 py-2 rounded-md font-medium flex items-center gap-2 transition-colors">
              {busy
                ? <Loader2 size={14} className="animate-spin" strokeWidth={2} />
                : <Sparkles size={14} strokeWidth={2} />}
              {busy ? t.designer.aiGenerating : t.designer.aiGenerateBtn}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
