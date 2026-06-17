import { useState, useEffect } from 'react';

interface Instance {
  id: string; definitionId: string; status: string;
  variables: Record<string, unknown>;
  activeNodeIds: string[];
}

export default function InstanceList({ onSelect, selectedId, instances, onTerminate, onResume, onDelete }:
    { onSelect: (id: string) => void; selectedId: string | null; instances: Instance[];
      onTerminate: (id: string) => void; onResume: (id: string) => void;
      onDelete: (id: string) => void; }) {

  const [menu, setMenu] = useState<{x:number;y:number;inst:Instance}|null>(null);

  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  const onContextMenu = (e: React.MouseEvent, inst: Instance) => {
    e.preventDefault();
    setMenu({ x: e.clientX, y: e.clientY, inst });
  };

  return (
    <div className="w-48 bg-gray-800 border-r border-gray-700 p-2 overflow-y-auto">
      <div className="text-xs text-gray-500 mb-2">Instances</div>
      {instances.length === 0 && (
        <div className="text-gray-600 text-xs">None started</div>
      )}
      {instances.map(inst => (
        <div key={inst.id}
          onClick={() => onSelect(inst.id)}
          onContextMenu={(e) => onContextMenu(e, inst)}
          className={`p-2 rounded mb-1 cursor-pointer text-xs
            ${selectedId === inst.id ? 'bg-blue-600' : 'bg-gray-700 hover:bg-gray-600'}`}
        >
          <div className="flex items-center gap-1">
            <span className={`w-2 h-2 rounded-full inline-block
              ${inst.status === 'RUNNING' ? 'bg-green-500' :
                inst.status === 'COMPLETED' ? 'bg-blue-500' :
                inst.status === 'SUSPENDED' ? 'bg-yellow-500' : 'bg-red-500'}`} />
            <span className="text-gray-300">{inst.id.substring(0, 5)}</span>
          </div>
          <div className="text-gray-500 text-[10px] mt-0.5">{inst.definitionId}</div>
        </div>
      ))}

      {/* Context menu */}
      {menu && (
        <div className="fixed z-50 bg-gray-800 border border-gray-600 rounded shadow-xl py-1 min-w-[130px]"
          style={{ left: menu.x, top: menu.y }} onClick={e => e.stopPropagation()}>
          <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
            {menu.inst.id.substring(0,8)} — {menu.inst.status}
          </div>
          {(menu.inst.status === 'RUNNING' || menu.inst.status === 'SUSPENDED') && (
            <button onClick={() => { onTerminate(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700">
              Terminate
            </button>
          )}
          {menu.inst.status === 'SUSPENDED' && (
            <button onClick={() => { onResume(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-sm text-yellow-400 hover:bg-gray-700">
              Resume
            </button>
          )}
          {(menu.inst.status === 'COMPLETED' || menu.inst.status === 'TERMINATED') && (
            <button onClick={() => { onDelete(menu.inst.id); setMenu(null); }}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-400 hover:bg-gray-700">
              Delete
            </button>
          )}
        </div>
      )}
    </div>
  );
}
