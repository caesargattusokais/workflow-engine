import { useEffect, useState } from 'react';
import { useT } from '../i18n';
import {
  getAiConfig, saveAiConfig, resetAiConfig, testAiConfig, AiConfigView,
  getAiGeneration, saveAiGeneration, resetAiGeneration, AiGenerationView,
} from '../api/client';

type Tab = 'connection' | 'generation' | 'knowledge';

/**
 * AI settings centre — connection (provider), generation (prompt + temperature),
 * and a placeholder for the future knowledge base. Saving either tab takes
 * effect immediately without a restart.
 */
export default function AiConfigModal({ onClose, onSaved }: {
  onClose: () => void;
  onSaved: (msg: string) => void;
}) {
  const { t } = useT();
  const [tab, setTab] = useState<Tab>('generation');

  // connection tab state
  const [view, setView] = useState<AiConfigView | null>(null);
  const [baseUrl, setBaseUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('');
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean, message: string } | null>(null);

  // generation tab state
  const [gen, setGen] = useState<AiGenerationView | null>(null);
  const [prompt, setPrompt] = useState('');
  const [defaultPrompt, setDefaultPrompt] = useState('');
  const [temperature, setTemperature] = useState(0.2);
  const [promptDirty, setPromptDirty] = useState(false);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getAiConfig().then(v => {
      setView(v);
      setBaseUrl(v.baseUrl || '');
      setModel(v.model || '');
    }).catch(e => setError(e.message));
    getAiGeneration().then(v => {
      setGen(v);
      setPrompt(v.systemPrompt);
      setDefaultPrompt(v.defaultPrompt);
      setTemperature(v.temperature);
      setPromptDirty(false);
    }).catch(e => setError(e.message));
  }, []);

  const inputCls = "w-full bg-gray-750 border border-gray-600 rounded p-2.5 text-sm text-gray-200 placeholder-gray-500 focus:border-purple-500 focus:outline-none";

  // ── connection tab ──────────────────────────────
  const connPayload = () => ({ baseUrl: baseUrl.trim(), apiKey: apiKey.trim(), model: model.trim() });

  const doTest = async () => {
    setTesting(true); setTestResult(null); setError(null);
    try {
      const r = await testAiConfig(connPayload());
      setTestResult({ ok: r.ok, message: r.message });
    } catch (e: any) {
      setTestResult({ ok: false, message: e.message });
    } finally {
      setTesting(false);
    }
  };

  const doSaveConnection = async () => {
    if (busy) return;
    if (!baseUrl.trim() || !model.trim()) {
      setError(t.designer.aiConfigBaseUrl.split('（')[0] + ' / ' + t.designer.aiConfigModel + ' required');
      return;
    }
    setBusy(true); setError(null);
    try {
      const v = await saveAiConfig(connPayload());
      setView(v); setApiKey('');
      onSaved(t.designer.aiConfigSaved);
      onClose();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const doResetConnection = async () => {
    setBusy(true); setError(null);
    try {
      const v = await resetAiConfig();
      setView(v); setBaseUrl(v.baseUrl || ''); setModel(v.model || ''); setApiKey('');
      onSaved(t.designer.aiConfigResetDone);
      onClose();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  // ── generation tab ──────────────────────────────
  const doSaveGeneration = async () => {
    if (busy) return;
    if (!prompt.trim()) {
      setError(t.designer.aiGenPromptRequired);
      return;
    }
    setBusy(true); setError(null);
    try {
      const v = await saveAiGeneration({ systemPrompt: prompt, temperature });
      setGen(v); setPrompt(v.systemPrompt); setPromptDirty(false);
      onSaved(t.designer.aiGenSaved);
      onClose();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const doRestorePrompt = () => {
    setPrompt(defaultPrompt);
    setPromptDirty(true);
  };

  const doResetGeneration = async () => {
    setBusy(true); setError(null);
    try {
      const v = await resetAiGeneration();
      setGen(v); setPrompt(v.systemPrompt); setTemperature(v.temperature); setPromptDirty(false);
      onSaved(t.designer.aiGenResetDone);
      onClose();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const sourceLabel = view
    ? (view.source === 'database' ? t.designer.aiConfigSourceDb
      : view.source === 'environment' ? t.designer.aiConfigSourceEnv
      : t.designer.aiConfigSourceNone)
    : '';

  const tabCls = (active: boolean) =>
    `px-3 py-1.5 text-sm rounded ${active ? 'bg-purple-600/30 text-purple-300' : 'text-gray-400 hover:text-gray-200'}`;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center" onClick={onClose}>
      <div className="bg-gray-800 rounded-lg shadow-2xl w-[620px] p-6" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg text-gray-200 font-bold">{t.designer.aiConfigTitle}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl">&times;</button>
        </div>

        <div className="flex gap-1 border-b border-gray-700 mb-4 pb-1">
          <button onClick={() => setTab('connection')} className={tabCls(tab === 'connection')}>
            {t.designer.aiTabConnection}
          </button>
          <button onClick={() => setTab('generation')} className={tabCls(tab === 'generation')}>
            {t.designer.aiTabGeneration}
          </button>
          <button onClick={() => setTab('knowledge')} className={tabCls(tab === 'knowledge')}>
            {t.designer.aiTabKnowledge}
          </button>
        </div>

        {tab === 'connection' && (
          <div>
            {view && (
              <div className="mb-3 text-xs text-gray-400 flex items-center gap-2">
                <span>{t.designer.aiConfigStatus}:</span>
                <span className={`px-2 py-0.5 rounded ${view.configured ? 'bg-green-900/60 text-green-300' : 'bg-gray-700 text-gray-400'}`}>
                  {view.configured ? '✓' : '✕'} {sourceLabel}
                </span>
                {view.hasApiKey && <span className="text-gray-500">Key: {view.apiKeyMasked || t.designer.aiConfigNotSet}</span>}
              </div>
            )}
            <div className="space-y-3">
              <div>
                <label className="block text-xs text-gray-400 mb-1">{t.designer.aiConfigBaseUrl}</label>
                <input value={baseUrl} onChange={e => setBaseUrl(e.target.value)}
                  placeholder={t.designer.aiConfigBaseUrlHint} className={inputCls} />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">{t.designer.aiConfigApiKey}</label>
                <input type="password" value={apiKey} onChange={e => setApiKey(e.target.value)}
                  placeholder={view?.apiKeyMasked ? `${t.designer.aiConfigApiKeyHint}（${view.apiKeyMasked}）` : t.designer.aiConfigApiKeyHint}
                  className={inputCls} />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">{t.designer.aiConfigModel}</label>
                <input value={model} onChange={e => setModel(e.target.value)}
                  placeholder={t.designer.aiConfigModelHint} className={inputCls} />
              </div>
            </div>
            {testResult && (
              <div className={`mt-3 text-xs rounded p-2 ${testResult.ok ? 'bg-green-900/50 text-green-300' : 'bg-red-900/50 text-red-300'}`}>
                {testResult.ok ? '✓ ' : '✕ '}{testResult.message}
              </div>
            )}
            <div className="flex items-center justify-between mt-5">
              <button onClick={doResetConnection} disabled={busy}
                className="text-xs text-gray-400 hover:text-gray-200 disabled:opacity-50 underline underline-offset-2">
                {t.designer.aiConfigReset}
              </button>
              <div className="flex items-center gap-2">
                <button onClick={doTest} disabled={testing || busy}
                  className="border border-gray-600 hover:border-gray-400 text-gray-300 text-sm px-3 py-1.5 rounded disabled:opacity-50">
                  {testing ? t.designer.aiConfigTesting : t.designer.aiConfigTest}
                </button>
                <button onClick={doSaveConnection} disabled={busy}
                  className="bg-purple-600 hover:bg-purple-500 disabled:bg-gray-700 disabled:text-gray-500 text-white text-sm px-4 py-1.5 rounded">
                  {t.designer.aiConfigSave}
                </button>
              </div>
            </div>
          </div>
        )}

        {tab === 'generation' && (
          <div>
            {gen && (
              <div className="mb-3 text-xs text-gray-400 flex items-center gap-2">
                <span>{t.designer.aiGenPromptSource}:</span>
                <span className={`px-2 py-0.5 rounded ${gen.isDefaultPrompt ? 'bg-gray-700 text-gray-400' : 'bg-blue-900/60 text-blue-300'}`}>
                  {gen.isDefaultPrompt ? t.designer.aiGenPromptDefault : t.designer.aiGenPromptCustom}
                </span>
              </div>
            )}
            <div className="mb-1 flex items-center justify-between">
              <label className="block text-xs text-gray-400">{t.designer.aiGenSystemPrompt}</label>
              <button onClick={doRestorePrompt}
                className="text-xs text-purple-400 hover:text-purple-300 underline underline-offset-2">
                {t.designer.aiGenRestoreDefault}
              </button>
            </div>
            <textarea value={prompt} onChange={e => { setPrompt(e.target.value); setPromptDirty(true); }}
              rows={12} spellCheck={false}
              className={`${inputCls} font-mono text-xs leading-relaxed resize-y`} />
            <p className="mt-1 text-xs text-gray-500">{t.designer.aiGenPromptHint}</p>

            <div className="mt-4">
              <div className="flex items-center justify-between mb-1">
                <label className="text-xs text-gray-400">{t.designer.aiGenTemperature}</label>
                <span className="text-xs text-gray-300 font-mono">{temperature.toFixed(2)}</span>
              </div>
              <input type="range" min="0" max="2" step="0.05" value={temperature}
                onChange={e => setTemperature(parseFloat(e.target.value))}
                className="w-full accent-purple-500" />
              <p className="mt-1 text-xs text-gray-500">{t.designer.aiGenTemperatureHint}</p>
            </div>

            <div className="flex items-center justify-between mt-5">
              <button onClick={doResetGeneration} disabled={busy}
                className="text-xs text-gray-400 hover:text-gray-200 disabled:opacity-50 underline underline-offset-2">
                {t.designer.aiGenResetAll}
              </button>
              <button onClick={doSaveGeneration} disabled={busy}
                className="bg-purple-600 hover:bg-purple-500 disabled:bg-gray-700 disabled:text-gray-500 text-white text-sm px-4 py-1.5 rounded">
                {t.designer.aiGenSave}
              </button>
            </div>
            {promptDirty && <span className="sr-only" data-testid="prompt-dirty">dirty</span>}
          </div>
        )}

        {tab === 'knowledge' && (
          <div className="py-8 text-center text-sm text-gray-500">
            {t.designer.aiTabKnowledgeHint}
          </div>
        )}

        {error && <div className="mt-3 text-xs rounded p-2 bg-red-900/50 text-red-300">✕ {error}</div>}
      </div>
    </div>
  );
}
