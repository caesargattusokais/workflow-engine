import { useEffect, useState } from 'react';
import { useT } from '../i18n';
import { getAiConfig, saveAiConfig, resetAiConfig, testAiConfig, AiConfigView } from '../api/client';

/**
 * AI provider configuration modal — base URL / API key / model, with a
 * connectivity test. Saving takes effect immediately (no restart).
 */
export default function AiConfigModal({ onClose, onSaved }: {
  onClose: () => void;
  onSaved: (msg: string) => void;
}) {
  const { t } = useT();
  const [view, setView] = useState<AiConfigView | null>(null);
  const [baseUrl, setBaseUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('');
  const [busy, setBusy] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean, message: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getAiConfig().then(v => {
      setView(v);
      setBaseUrl(v.baseUrl || '');
      setModel(v.model || '');
    }).catch(e => setError(e.message));
  }, []);

  const payload = () => ({ baseUrl: baseUrl.trim(), apiKey: apiKey.trim(), model: model.trim() });

  const doTest = async () => {
    setTesting(true); setTestResult(null);
    try {
      const r = await testAiConfig(payload());
      setTestResult({ ok: r.ok, message: r.message });
    } catch (e: any) {
      setTestResult({ ok: false, message: e.message });
    } finally {
      setTesting(false);
    }
  };

  const doSave = async () => {
    if (busy) return;
    if (!baseUrl.trim() || !model.trim()) {
      setError(t.designer.aiConfigBaseUrl.split('（')[0] + ' / ' + t.designer.aiConfigModel + ' required');
      return;
    }
    setBusy(true); setError(null);
    try {
      const v = await saveAiConfig(payload());
      setView(v); setApiKey('');
      onSaved(t.designer.aiConfigSaved);
      onClose();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const doReset = async () => {
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

  const sourceLabel = view
    ? (view.source === 'database' ? t.designer.aiConfigSourceDb
      : view.source === 'environment' ? t.designer.aiConfigSourceEnv
      : t.designer.aiConfigSourceNone)
    : '';

  const inputCls = "w-full bg-gray-750 border border-gray-600 rounded p-2.5 text-sm text-gray-200 placeholder-gray-500 focus:border-purple-500 focus:outline-none";

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center" onClick={onClose}>
      <div className="bg-gray-800 rounded-lg shadow-2xl w-[520px] p-6" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg text-gray-200 font-bold">{t.designer.aiConfigTitle}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl">&times;</button>
        </div>

        {view && (
          <div className="mb-4 text-xs text-gray-400 flex items-center gap-2">
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
              placeholder={t.designer.aiConfigBaseUrlHint} className={inputCls} autoFocus />
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
        {error && <div className="mt-3 text-xs rounded p-2 bg-red-900/50 text-red-300">✕ {error}</div>}

        <div className="flex items-center justify-between mt-5">
          <button onClick={doReset} disabled={busy}
            className="text-xs text-gray-400 hover:text-gray-200 disabled:opacity-50 underline underline-offset-2">
            {t.designer.aiConfigReset}
          </button>
          <div className="flex items-center gap-2">
            <button onClick={doTest} disabled={testing || busy}
              className="border border-gray-600 hover:border-gray-400 text-gray-300 text-sm px-3 py-1.5 rounded disabled:opacity-50">
              {testing ? t.designer.aiConfigTesting : t.designer.aiConfigTest}
            </button>
            <button onClick={doSave} disabled={busy}
              className="bg-purple-600 hover:bg-purple-500 disabled:bg-gray-700 disabled:text-gray-500
                text-white text-sm px-4 py-1.5 rounded">
              {t.designer.aiConfigSave}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
