import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { PlayCircle } from 'lucide-react';

export default function StartEventNode({ data }: NodeProps) {
  return (
    <NodeShell
      data={data}
      icon={<PlayCircle size={15} strokeWidth={2} />}
      tint={'#34d399'}
      kind="Start"
      sub={data.name as string || "Start"}
    />
  );
}
