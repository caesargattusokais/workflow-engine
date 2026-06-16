import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function StartEventNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="source" position={Position.Bottom} className="!bg-green-500" />
      <div className="w-9 h-9 rounded-full bg-green-500 border-2 border-green-600
                      flex items-center justify-center text-xs font-bold text-white shadow-lg">
        S
      </div>
      <div className="text-center text-xs text-gray-300 mt-1">{data.name as string}</div>
    </div>
  );
}
