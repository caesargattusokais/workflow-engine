import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ExclusiveGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-orange-400" />
      <div className="w-10 h-10 bg-orange-500 rotate-45 border-2 border-orange-300
                      flex items-center justify-center shadow-lg shadow-orange-500/30">
        <span className="text-white text-lg font-black" style={{ transform: 'rotate(-45deg)' }}>?</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-orange-400" />
      <div className="text-center mt-1">
        <div className="text-xs text-orange-400 font-semibold">判断</div>
        <div className="text-[10px] text-gray-500">{data.name as string}</div>
      </div>
    </div>
  );
}
