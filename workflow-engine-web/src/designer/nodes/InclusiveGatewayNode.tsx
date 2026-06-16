import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function InclusiveGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="w-8 h-8 bg-purple-600 rotate-45 border border-purple-400
                      flex items-center justify-center shadow-lg">
        <span className="text-white text-base font-bold" style={{ transform: 'rotate(-45deg)' }}>~</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
    </div>
  );
}
