import { useEffect, useState } from 'react';
import LanguageSwitcher, { useT } from './i18n';
import DesignerPage from './designer/DesignerPage';
import MonitorPage from './monitor/MonitorPage';
import Dashboard from './monitor/Dashboard';
import AiConfigModal from './designer/AiConfigModal';
import { useToasts, ToastContainer, showToast } from './api/toast';
import { getAiConfig } from './api/client';

export default function App() {
  const [tab, setTab] = useState<'designer' | 'monitor' | 'dashboard'>('designer');
  const [monitorDefId, setMonitorDefId] = useState<string | null>(null);
  const [monitorInstId, setMonitorInstId] = useState<string | null>(null);
  const [showAiConfig, setShowAiConfig] = useState(false);
  const [aiConfigured, setAiConfigured] = useState<boolean | null>(null);
  const { t } = useT();
  const { toasts, removeToast } = useToasts();

  // Status dot on the header gear — reflects whether AI is usable right now
  useEffect(() => {
    getAiConfig().then(v => setAiConfigured(!!v?.configured)).catch(() => setAiConfigured(false));
  }, [showAiConfig]);

  const handleNavigate = (t: 'designer' | 'monitor', defId?: string, instId?: string) => {
    if (t === 'monitor' && defId) {
      setMonitorDefId(defId);
      setMonitorInstId(instId || null);
    }
    setTab(t);
  };

  return (
    <div className="h-screen flex flex-col">
      <header className="bg-gray-800 border-b border-gray-700 px-4 py-2 flex gap-4 items-center">
        <button onClick={() => setTab('designer')}
          className={`px-4 py-1 rounded ${tab === 'designer' ? 'bg-blue-600' : 'bg-gray-700'}`}>
          {t.app.designer}
        </button>
        <button onClick={() => setTab('monitor')}
          className={`px-4 py-1 rounded ${tab === 'monitor' ? 'bg-blue-600' : 'bg-gray-700'}`}>
          {t.app.monitor}
        </button>
        <button onClick={() => setTab('dashboard')}
          className={`px-4 py-1 rounded ${tab === 'dashboard' ? 'bg-blue-600' : 'bg-gray-700'}`}>
          {t.app.dashboard}
        </button>
        <div className="flex-1" />
        <button onClick={() => setShowAiConfig(true)}
          title={aiConfigured === false ? t.app.settingsNotConfigured : t.app.settingsConfigured}
          className="flex items-center gap-1.5 px-3 py-1 rounded bg-gray-700 hover:bg-gray-600
            text-gray-200 text-sm transition-colors">
          <span className="text-base leading-none">⚙</span>
          <span>{t.app.settings}</span>
          <span className={`w-1.5 h-1.5 rounded-full ${
            aiConfigured === null ? 'bg-gray-500'
              : aiConfigured ? 'bg-green-400' : 'bg-amber-400'}`} />
        </button>
        <LanguageSwitcher />
      </header>
      <main className="flex-1 overflow-hidden">
        {tab === 'designer' && <DesignerPage onNavigate={handleNavigate} />}
        {tab === 'monitor' && <MonitorPage initialDefId={monitorDefId} initialInstId={monitorInstId} />}
        {tab === 'dashboard' && <Dashboard />}
      </main>
      {showAiConfig && (
        <AiConfigModal
          onClose={() => setShowAiConfig(false)}
          onSaved={msg => showToast(msg, 'success')} />
      )}
      <ToastContainer toasts={toasts} removeToast={removeToast} />
    </div>
  );
}
