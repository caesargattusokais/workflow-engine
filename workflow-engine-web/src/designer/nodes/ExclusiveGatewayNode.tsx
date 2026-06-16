import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ExclusiveGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-orange-400" />
      <div className="w-8 h-8 bg-orange-500 rotate-45 border border-orange-400 shadow-lg" />
      <Handle type="source" position={Position.Bottom} className="!bg-orange-400" />
      <div className="text-center text-xs text-gray-300 mt-2">{data.name as string}</div>
    </div>
  );
}
