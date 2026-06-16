import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function EndEventNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-red-400" />
      <div className="w-9 h-9 rounded-full border-3 border-red-500 bg-gray-800
                      flex items-center justify-center text-xs text-red-400 shadow-lg"
           style={{ borderWidth: 3 }}>
        E
      </div>
      <div className="text-center text-xs text-gray-300 mt-1">{data.name as string}</div>
    </div>
  );
}
