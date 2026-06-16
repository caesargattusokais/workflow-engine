import { useState } from 'react';

export default function App() {
  const [tab, setTab] = useState<'designer' | 'monitor'>('designer');

  return (
    <div className="h-screen flex flex-col">
      <header className="bg-gray-800 border-b border-gray-700 px-4 py-2 flex gap-4">
        <button
          onClick={() => setTab('designer')}
          className={`px-4 py-1 rounded ${tab === 'designer' ? 'bg-blue-600' : 'bg-gray-700'}`}
        >Designer</button>
        <button
          onClick={() => setTab('monitor')}
          className={`px-4 py-1 rounded ${tab === 'monitor' ? 'bg-blue-600' : 'bg-gray-700'}`}
        >Monitor</button>
      </header>
      <main className="flex-1 overflow-hidden">
        {tab === 'designer' ? <p className="p-8 text-gray-400">Designer — coming soon</p> : null}
        {tab === 'monitor' ? <p className="p-8 text-gray-400">Monitor — coming soon</p> : null}
      </main>
    </div>
  );
}
