import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { Sparkles } from 'lucide-react';

export default function AiTaskNode({ data }: NodeProps) {
  const mode = (data.mode as string) || 'summary';
  return (
    <NodeShell
      data={data}
      icon={<Sparkles size={15} strokeWidth={2} />}
      tint={'#f472b6'}
      kind="AI task"
      sub={"AI · " + mode}
    />
  );
}
