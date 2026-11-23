import { useState, useCallback, useRef, useEffect } from 'react';
import type { Node, Edge } from '@xyflow/react';
import { useT } from '../i18n';
import { StartEventSection, UserTaskSection, ServiceTaskSection, GatewayConditionsSection,
         TimerSection, CallActivitySection, AiTaskSection, OutgoingEdgesSection } from './property/sections';

const NODE_TYPE_LABEL_KEYS: Record<string, keyof ReturnType<typeof useT>['t']['nodes']> = {
  exclusiveGateway: 'exclusiveGateway',
  inclusiveGateway: 'inclusiveGateway',
  parallelGateway: 'parallelGateway',
  userTask: 'userTask',
  serviceTask: 'serviceTask',
  startEvent: 'startEvent',
  endEvent: 'endEvent',
  timer: 'timer',
  callActivity: 'callActivity',
  aiTask: 'aiTask',
};

export default function PropertyPanel({ node, onChange, edges, onSelectEdge, onEdgesChange }: {
    node: Node | null;
    onChange: (node: Node) => void;
    edges?: Edge[];
    onSelectEdge?: (edgeId: string) => void;
    onEdgesChange?: (edges: Edge[]) => void;
}) {
  const { t } = useT();
  const [width, setWidth] = useState(280);
  const dragging = useRef(false);
  const startX = useRef(0);
  const startW = useRef(0);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    dragging.current = true;
    startX.current = e.clientX;
    startW.current = width;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, [width]);

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!dragging.current) return;
      const delta = startX.current - e.clientX;
      setWidth(Math.max(200, Math.min(600, startW.current + delta)));
    };
    const onUp = () => {
      dragging.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
  }, []);

  if (!node) {
    return (
      <div className="bg-gray-800 border-l border-gray-700 p-4 text-sm text-gray-500" style={{ width, minWidth: width }}>
        {t.props.selectNode}
      </div>
    );
  }

  const updateData = (key: string, value: unknown) => {
    const newData = { ...(node.data || {}), [key]: value };
    onChange({ id: node.id, type: node.type, position: node.position, data: newData } as Node);
  };

  const sectionProps = { node, updateData };
  const labelKey = NODE_TYPE_LABEL_KEYS[node.type || ''];
  const label = labelKey ? t.nodes[labelKey] : node.type;

  return (
    <div className="bg-gray-800 border-l border-gray-700 overflow-y-auto flex-shrink-0 relative"
         style={{ width, minWidth: width }}>
      {/* Resize handle — left edge */}
      <div
        onMouseDown={onMouseDown}
        className="absolute left-0 top-0 bottom-0 w-1.5 cursor-col-resize hover:bg-blue-500/50 transition-colors z-10"
        style={{ marginLeft: -1 }}
      />

      <div className="p-4 text-sm">
        <h3 className="text-gray-300 font-bold mb-1 capitalize">{label}</h3>
        <div className="text-[10px] text-gray-600 mb-3">{node.type}</div>

        {/* Name (all nodes) */}
        <label className="block mb-3">
          <span className="text-gray-400 text-xs">{t.props.name}</span>
          <input className="w-full bg-gray-900/70 border border-gray-700 rounded-md px-2 py-1 text-white text-sm mt-0.5"
            value={(node.data.name as string) || ''}
            onChange={e => updateData('name', e.target.value)} />
        </label>

        {node.type === 'startEvent' && <StartEventSection {...sectionProps} />}
        {node.type === 'userTask' && <UserTaskSection {...sectionProps} />}
        {node.type === 'serviceTask' && <ServiceTaskSection {...sectionProps} />}
        {node.type === 'exclusiveGateway' && <GatewayConditionsSection {...sectionProps} kind="exclusive" />}
        {node.type === 'inclusiveGateway' && <GatewayConditionsSection {...sectionProps} kind="inclusive" />}
        {node.type === 'parallelGateway' && (
          <div className="border-t border-gray-700 pt-3 mt-2">
            <div className="text-xs text-gray-500">{t.props.gatewayInfo}</div>
          </div>
        )}
        {node.type === 'timer' && <TimerSection {...sectionProps} />}
        {node.type === 'callActivity' && <CallActivitySection {...sectionProps} />}
        {node.type === 'aiTask' && <AiTaskSection {...sectionProps} />}

        {/* ── Outgoing Edges ── */}
        <OutgoingEdgesSection node={node} edges={edges}
          onSelectEdge={onSelectEdge} onEdgesChange={onEdgesChange} />

        <div className="mt-4 pt-3 border-t border-gray-700">
          <span className="text-gray-500 text-xs">ID: {node.id}</span>
        </div>
      </div>
    </div>
  );
}
