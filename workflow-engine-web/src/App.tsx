import { useState } from 'react';
import LanguageSwitcher, { useT } from './i18n';
import DesignerPage from './designer/DesignerPage';
import MonitorPage from './monitor/MonitorPage';

export default function App() {
  const [tab, setTab] = useState<'designer' | 'monitor'>('designer');
  const { t } = useT();

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
        <div className="flex-1" />
        <LanguageSwitcher />
      </header>
      <main className="flex-1 overflow-hidden">
        {tab === 'designer' && <DesignerPage onNavigate={setTab} />}
        {tab === 'monitor' && <MonitorPage />}
      </main>
    </div>
  );
}
