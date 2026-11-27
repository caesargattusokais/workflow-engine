import { useCallback, useEffect, useState } from 'react';
import { Users, Plus, Trash2, Shield, UserMinus, LogOut, RefreshCw, ChevronDown } from 'lucide-react';
import { useT } from '../i18n';
import { showToast } from '../api/toast';
import {
  listGroups, listGroupMembers, listGroupCandidates, createGroup, deleteGroup,
  addGroupMember, removeGroupMember, leaveGroup, setGroupAdmin,
  type GroupView, type GroupMember, type LoginUser,
} from '../api/client';

interface Props {
  currentUser: LoginUser;
  /** Reload /auth/me so the header group selector reflects membership changes. */
  onMembershipChanged: () => void;
}

export default function GroupManagePage({ currentUser, onMembershipChanged }: Props) {
  const { t } = useT();
  const isGlobalAdmin = (currentUser.roles || []).some(r => r.toLowerCase() === 'admin');
  const [groups, setGroups] = useState<GroupView[]>([]);
  const [candidates, setCandidates] = useState<{ username: string; displayName: string }[]>([]);
  const [members, setMembers] = useState<Record<string, GroupMember[]>>({});
  const [expanded, setExpanded] = useState<string | null>(null);
  const [newId, setNewId] = useState('');
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [selectedUser, setSelectedUser] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [gs, cs] = await Promise.all([listGroups(), listGroupCandidates()]);
      setGroups(gs || []);
      setCandidates(cs || []);
    } catch (e) {
      showToast(`${t.groups.loadFailed}: ${e}`, 'error');
    }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  const loadMembers = async (id: string) => {
    try {
      const list = await listGroupMembers(id);
      setMembers(prev => ({ ...prev, [id]: list || [] }));
    } catch (e) {
      showToast(`${t.groups.loadFailed}: ${e}`, 'error');
    }
  };

  const run = async (action: () => Promise<unknown>, success: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await action();
      showToast(success, 'success');
      await load();
      if (expanded) await loadMembers(expanded);
      onMembershipChanged();
    } catch (e) {
      showToast(`${t.groups.actionFailed}: ${e}`, 'error');
    } finally {
      setBusy(false);
    }
  };

  const doCreate = () => {
    if (!newId.trim()) return;
    run(() => createGroup(newId.trim(), newName.trim(), newDesc.trim()), t.groups.created)
      .then(() => { setNewId(''); setNewName(''); setNewDesc(''); });
  };

  const toggle = (id: string) => {
    const next = expanded === id ? null : id;
    setExpanded(next);
    if (next && !members[id]) loadMembers(id);
  };

  const isMemberOf = (g: GroupView) => g.member || g.groupAdmin;
  const canManage = (g: GroupView) => g.groupAdmin || isGlobalAdmin;

  return (
    <div className="h-full overflow-auto bg-gray-900 p-6 text-slate-200">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-5">
          <div>
            <h1 className="text-lg font-semibold text-white flex items-center gap-2">
              <Users size={18} className="text-blue-400" />{t.groups.title}
            </h1>
            <p className="text-xs text-slate-400 mt-1">{t.groups.subtitle}</p>
          </div>
          <button onClick={load} className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs
            bg-gray-800 hover:bg-gray-700 border border-gray-700">
            <RefreshCw size={13} />{t.groups.refresh}
          </button>
        </div>

        {/* Global-admin-only creation */}
        {isGlobalAdmin && (
          <div className="mb-5 rounded-lg border border-gray-700 bg-gray-850/60 p-4">
            <h2 className="text-sm font-medium text-white mb-3 flex items-center gap-2">
              <Plus size={14} className="text-emerald-400" />{t.groups.create}
            </h2>
            <div className="flex flex-wrap gap-2 items-end">
              <label className="flex flex-col gap-1 text-[11px] text-slate-400">
                {t.groups.id}
                <input value={newId} onChange={e => setNewId(e.target.value)}
                  placeholder="group-gamma" className="w-40 px-2 py-1.5 rounded bg-gray-800 border border-gray-700 text-xs text-slate-100 outline-none focus:border-blue-500" />
              </label>
              <label className="flex flex-col gap-1 text-[11px] text-slate-400">
                {t.groups.name}
                <input value={newName} onChange={e => setNewName(e.target.value)}
                  className="w-44 px-2 py-1.5 rounded bg-gray-800 border border-gray-700 text-xs text-slate-100 outline-none focus:border-blue-500" />
              </label>
              <label className="flex flex-col gap-1 text-[11px] text-slate-400 flex-1 min-w-[180px]">
                {t.groups.description}
                <input value={newDesc} onChange={e => setNewDesc(e.target.value)}
                  className="w-full px-2 py-1.5 rounded bg-gray-800 border border-gray-700 text-xs text-slate-100 outline-none focus:border-blue-500" />
              </label>
              <button disabled={busy || !newId.trim()} onClick={doCreate}
                className="px-3 py-1.5 rounded-md bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-xs font-medium">
                {t.groups.createBtn}
              </button>
            </div>
            <p className="text-[10px] text-slate-500 mt-2">{t.groups.idHint}</p>
          </div>
        )}

        {groups.length === 0 && (
          <div className="rounded-lg border border-dashed border-gray-700 p-10 text-center text-sm text-slate-400">
            {t.groups.empty}<br /><span className="text-xs text-slate-500">{t.groups.emptyHint}</span>
          </div>
        )}

        <div className="space-y-3">
          {groups.map(g => {
            const opened = expanded === g.group.id;
            const list = members[g.group.id] || [];
            const chosen = selectedUser[g.group.id] || '';
            return (
              <div key={g.group.id} className="rounded-lg border border-gray-700 bg-gray-850/50 overflow-hidden">
                <button onClick={() => toggle(g.group.id)} className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-gray-800/60">
                  <ChevronDown size={14} className={`text-slate-500 transition-transform ${opened ? 'rotate-180' : ''}`} />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium text-white truncate">{g.group.name}</div>
                    <div className="text-[11px] text-slate-500 truncate">{g.group.id}{g.group.description ? ` · ${g.group.description}` : ''}</div>
                  </div>
                  <span className="text-[11px] text-slate-400">{g.memberCount} {t.groups.people}</span>
                  <span className={`px-2 py-0.5 rounded text-[10px] ${g.groupAdmin ? 'bg-amber-500/20 text-amber-300' : isMemberOf(g) ? 'bg-blue-500/20 text-blue-300' : 'bg-gray-700 text-slate-300'}`}>
                    {g.groupAdmin ? t.groups.roleAdmin : isMemberOf(g) ? t.groups.roleMember : t.groups.roleOutsider}
                  </span>
                </button>

                {opened && (
                  <div className="border-t border-gray-700 px-4 py-3">
                    {canManage(g) && (
                      <div className="flex gap-2 mb-3">
                        <select value={chosen} onChange={e => setSelectedUser(prev => ({ ...prev, [g.group.id]: e.target.value }))}
                          className="flex-1 px-2 py-1.5 rounded bg-gray-800 border border-gray-700 text-xs outline-none focus:border-blue-500">
                          <option value="">{t.groups.selectUser}</option>
                          {candidates.map(c => <option key={c.username} value={c.username}>{c.displayName} ({c.username})</option>)}
                        </select>
                        <button disabled={busy || !chosen} onClick={() => run(() => addGroupMember(g.group.id, chosen), t.groups.memberAdded)}
                          className="px-3 py-1.5 rounded bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-xs">
                          {t.groups.add}
                        </button>
                      </div>
                    )}

                    <div className="space-y-1">
                      {list.length === 0 && <div className="text-xs text-slate-500">{t.groups.noMembers}</div>}
                      {list.map(m => (
                        <div key={m.username} className="flex items-center gap-2 px-2 py-1.5 rounded bg-gray-800/60 text-xs">
                          <span className="flex-1 text-slate-200">{m.displayName} <span className="text-slate-500">({m.username})</span></span>
                          {m.groupAdmin && <span className="px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 text-[10px]">{t.groups.roleAdmin}</span>}
                          {isGlobalAdmin && (
                            <button disabled={busy} title={m.groupAdmin ? t.groups.revokeAdmin : t.groups.makeAdmin}
                              onClick={() => run(() => setGroupAdmin(g.group.id, m.username, !m.groupAdmin), t.groups.adminUpdated)}
                              className="p-1 rounded hover:bg-gray-700 text-slate-400 hover:text-amber-300">
                              <Shield size={13} />
                            </button>
                          )}
                          {canManage(g) && !(m.groupAdmin && !isGlobalAdmin) && (
                            <button disabled={busy} title={t.groups.remove}
                              onClick={() => run(() => removeGroupMember(g.group.id, m.username), t.groups.memberRemoved)}
                              className="p-1 rounded hover:bg-gray-700 text-slate-400 hover:text-red-400">
                              <UserMinus size={13} />
                            </button>
                          )}
                        </div>
                      ))}
                    </div>

                    <div className="flex gap-2 mt-3 pt-3 border-t border-gray-700/70">
                      {isMemberOf(g) && (
                        <button disabled={busy} onClick={() => run(() => leaveGroup(g.group.id), t.groups.left)}
                          className="flex items-center gap-1 px-2.5 py-1 rounded text-[11px] bg-gray-700 hover:bg-gray-600">
                          <LogOut size={12} />{t.groups.leave}
                        </button>
                      )}
                      {isGlobalAdmin && (
                        <button disabled={busy} onClick={() => {
                          if (!window.confirm(t.groups.deleteConfirm)) return;
                          run(() => deleteGroup(g.group.id), t.groups.deleted);
                        }} className="flex items-center gap-1 px-2.5 py-1 rounded text-[11px] bg-red-900/50 hover:bg-red-800/60 text-red-300">
                          <Trash2 size={12} />{t.groups.delete}
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
