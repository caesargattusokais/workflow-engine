import { useT } from '../i18n';

export default function NodePalette() {
  const { t } = useT();
  const items = [
    { type: 'startEvent', label: t.nodes.start, color: 'bg-green-500', shape: 'rounded-full' },
    { type: 'endEvent', label: t.nodes.end, color: 'border-2 border-red-500', shape: 'rounded-full' },
    { type: 'userTask', label: t.nodes.userTaskShort, color: 'bg-blue-600', shape: 'rounded-lg' },
    { type: 'serviceTask', label: t.nodes.serviceTaskShort, color: 'bg-purple-700', shape: 'rounded-lg' },
    { type: 'exclusiveGateway', label: t.nodes.xor, color: 'bg-orange-500', shape: 'rotate-45' },
    { type: 'parallelGateway', label: t.nodes.and, color: 'bg-blue-600', shape: 'rotate-45' },
    { type: 'inclusiveGateway', label: t.nodes.or, color: 'bg-purple-600', shape: 'rotate-45' },
    { type: 'timer', label: t.nodes.timer, color: 'bg-amber-600', shape: 'rounded-lg' },
  ];

  const onDragStart = (e: React.DragEvent, nodeType: string) => {
    e.dataTransfer.setData('application/reactflow', nodeType);
    e.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="w-36 bg-gray-800 border-r border-gray-700 p-2 flex flex-col gap-1">
      <div className="text-xs text-gray-500 text-center mb-2">{t.designer.dragHint}</div>
      {items.map(item => (
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
