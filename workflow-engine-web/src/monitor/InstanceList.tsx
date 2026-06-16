interface Instance {
  id: string; definitionId: string; status: string;
  variables: Record<string, unknown>;
  activeNodeIds: string[];
}

export default function InstanceList({ onSelect, selectedId, instances }:
    { onSelect: (id: string) => void, selectedId: string | null, instances: Instance[] }) {

  return (
    <div className="w-48 bg-gray-800 border-r border-gray-700 p-2 overflow-y-auto">
      <div className="text-xs text-gray-500 mb-2">Instances</div>
      {instances.length === 0 && (
        <div className="text-gray-600 text-xs">None started</div>
      )}
      {instances.map(inst => (
        <div key={inst.id}
          onClick={() => onSelect(inst.id)}
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
    </div>
  );
}
