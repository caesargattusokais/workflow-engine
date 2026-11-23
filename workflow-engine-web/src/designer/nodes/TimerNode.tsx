import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { Timer } from 'lucide-react';

export default function TimerNode({ data }: NodeProps) {
  return (
    <NodeShell
      data={data}
      icon={<Timer size={15} strokeWidth={2} />}
      tint={'#fbbf24'}
      kind="Timer"
      sub={String(data.duration || data.deadline || "Timer")}
    />
  );
}
