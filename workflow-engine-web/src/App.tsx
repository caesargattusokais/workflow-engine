import DesignerPage from './designer/DesignerPage';

export default function App() {
  return (
    <div className="h-screen flex flex-col">
      <header className="bg-gray-800 border-b border-gray-700 px-4 py-2">
        <span className="text-sm text-white font-semibold">Workflow Designer</span>
      </header>
      <main className="flex-1 overflow-hidden">
        <DesignerPage />
      </main>
    </div>
  );
}
