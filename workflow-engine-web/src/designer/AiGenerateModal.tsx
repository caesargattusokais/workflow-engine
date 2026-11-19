import { useState } from 'react';
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
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center"
      onClick={onClose}>
      <div className="bg-gray-800 rounded-lg shadow-2xl w-[560px] p-6"
        onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg text-gray-200 font-bold">{t.designer.aiGenerateTitle}</h2>
          <button onClick={onClose}
            className="text-gray-400 hover:text-white text-xl">&times;</button>
        </div>
        <textarea
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit(); }}
          placeholder={t.designer.aiPromptPlaceholder}
          rows={5}
          autoFocus
          className="w-full bg-gray-700 border border-gray-600 rounded p-3 text-sm text-gray-200
            placeholder-gray-500 focus:border-purple-500 focus:outline-none resize-none" />
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs text-gray-500">Ctrl + Enter</span>
          <button onClick={submit} disabled={busy || !prompt.trim()}
            className="bg-purple-600 hover:bg-purple-500 disabled:bg-gray-700 disabled:text-gray-500
              text-white text-sm px-4 py-1.5 rounded flex items-center gap-2">
            {busy && (
              <svg className="animate-spin h-3.5 w-3.5" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            )}
            {busy ? t.designer.aiGenerating : t.designer.aiGenerateBtn}
          </button>
        </div>
      </div>
    </div>
  );
}
