import type { Node } from '@xyflow/react';

export default function PropertyPanel({ node, onChange }: {
    node: Node | null;
    onChange: (node: Node) => void;
}) {
  if (!node) {
    return (
      <div className="w-56 bg-gray-800 border-l border-gray-700 p-4 text-sm text-gray-500">
        Select a node to edit
      </div>
    );
  }

  const updateData = (key: string, value: unknown) => {
    onChange({ ...node, data: { ...node.data, [key]: value } });
  };

  return (
    <div className="w-56 bg-gray-800 border-l border-gray-700 p-4 text-sm overflow-y-auto">
      <h3 className="text-gray-300 font-bold mb-3 capitalize">{node.type}</h3>

      <label className="block mb-2">
        <span className="text-gray-400 text-xs">Name</span>
        <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
          value={(node.data.name as string) || ''}
          onChange={e => updateData('name', e.target.value)} />
      </label>

      {node.type === 'userTask' && (
        <>
          <label className="block mb-2">
            <span className="text-gray-400 text-xs">Assignee</span>
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
              value={(node.data.assignee as string) || ''} placeholder="e.g. ${applicant}"
              onChange={e => updateData('assignee', e.target.value)} />
          </label>
          <label className="block mb-2">
            <span className="text-gray-400 text-xs">Candidate Groups</span>
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
              value={(Array.isArray(node.data.candidateGroups) ? (node.data.candidateGroups as string[]).join(', ') : '')}
              placeholder="comma-separated"
              onChange={e => updateData('candidateGroups', e.target.value.split(',').map(s => s.trim()))} />
          </label>
        </>
      )}

      {node.type === 'serviceTask' && (
        <label className="block mb-2">
          <span className="text-gray-400 text-xs">Handler Class</span>
          <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
            value={(node.data.handlerClass as string) || ''}
            placeholder="com.myapp.Handler"
            onChange={e => updateData('handlerClass', e.target.value)} />
        </label>
      )}

      <div className="mt-4 pt-3 border-t border-gray-700">
        <span className="text-gray-500 text-xs">ID: {node.id}</span>
      </div>
    </div>
  );
}
