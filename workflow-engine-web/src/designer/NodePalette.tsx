import {
  CirclePlay, CircleStop, UserRound, Cog, GitBranch, GitMerge, GitFork,
  Timer, Boxes, Sparkles, GripVertical,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useT } from '../i18n';

interface PaletteItem {
  type: string;
  label: string;
  icon: LucideIcon;
  tint: string; // text color; bg derived at 12% opacity via inline style
}

export default function NodePalette() {
  const { t } = useT();
  const items: PaletteItem[] = [
    { type: 'startEvent', label: t.nodes.start, icon: CirclePlay, tint: '#34d399' },
    { type: 'endEvent', label: t.nodes.end, icon: CircleStop, tint: '#f87171' },
    { type: 'userTask', label: t.nodes.userTaskShort, icon: UserRound, tint: '#60a5fa' },
    { type: 'serviceTask', label: t.nodes.serviceTaskShort, icon: Cog, tint: '#c084fc' },
    { type: 'exclusiveGateway', label: t.nodes.xor, icon: GitBranch, tint: '#fb923c' },
    { type: 'parallelGateway', label: t.nodes.and, icon: GitFork, tint: '#60a5fa' },
    { type: 'inclusiveGateway', label: t.nodes.or, icon: GitMerge, tint: '#c084fc' },
    { type: 'timer', label: t.nodes.timer, icon: Timer, tint: '#fbbf24' },
    { type: 'callActivity', label: t.nodes.callActivity, icon: Boxes, tint: '#a78bfa' },
    { type: 'aiTask', label: t.nodes.aiTaskShort, icon: Sparkles, tint: '#f472b6' },
  ];

  const onDragStart = (e: React.DragEvent, nodeType: string) => {
    e.dataTransfer.setData('application/reactflow', nodeType);
    e.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="w-40 shrink-0 bg-gray-850 border-r border-gray-700/70 p-2.5 flex flex-col gap-1">
      <div className="text-[11px] uppercase tracking-wider text-gray-500 text-center mb-2 font-medium">
        {t.designer.dragHint}
      </div>
      {items.map(item => {
        const Icon = item.icon;
        return (
          <div key={item.type}
            data-type={item.type}
            draggable
            onDragStart={(e) => onDragStart(e, item.type)}
            className="group flex items-center gap-2.5 rounded-md px-2 py-1.5 cursor-grab
                       hover:bg-gray-800 border border-transparent hover:border-gray-700/70 transition-colors"
          >
            <span className="w-6 h-6 rounded-md flex items-center justify-center shrink-0"
              style={{ backgroundColor: item.tint + '1f', color: item.tint }}>
              <Icon size={13} strokeWidth={2} />
            </span>
            <span className="text-xs text-gray-300 group-hover:text-gray-100 truncate">{item.label}</span>
            <GripVertical size={11} className="ml-auto text-gray-700 group-hover:text-gray-500 opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
        );
      })}
    </div>
  );
}
