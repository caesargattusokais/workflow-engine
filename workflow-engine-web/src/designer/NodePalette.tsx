const PALETTE_ITEMS = [
  { type: 'startEvent', label: 'Start', color: 'bg-green-500', shape: 'rounded-full' },
  { type: 'endEvent', label: 'End', color: 'border-2 border-red-500', shape: 'rounded-full' },
  { type: 'userTask', label: 'User Task', color: 'bg-blue-600', shape: 'rounded-lg' },
  { type: 'serviceTask', label: 'Service Task', color: 'bg-purple-700', shape: 'rounded-lg' },
  { type: 'exclusiveGateway', label: '判断 (XOR)', color: 'bg-orange-500', shape: 'rotate-45' },
  { type: 'parallelGateway', label: '并行 (AND)', color: 'bg-blue-600', shape: 'rotate-45' },
  { type: 'inclusiveGateway', label: '条件 (OR)', color: 'bg-purple-600', shape: 'rotate-45' },
];

export default function NodePalette() {
  const onDragStart = (e: React.DragEvent, nodeType: string) => {
    e.dataTransfer.setData('application/reactflow', nodeType);
    e.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="w-36 bg-gray-800 border-r border-gray-700 p-2 flex flex-col gap-1">
      <div className="text-xs text-gray-500 text-center mb-2">Drag to canvas</div>
      {PALETTE_ITEMS.map(item => (
        <div key={item.type}
          draggable
          onDragStart={(e) => onDragStart(e, item.type)}
          className="bg-gray-700 hover:bg-gray-600 rounded p-2 text-xs cursor-grab
                     flex items-center gap-2 transition-colors"
        >
          <span className={`w-4 h-4 inline-block ${item.color} ${item.shape}`} />
          {item.label}
        </div>
      ))}
    </div>
  );
}
