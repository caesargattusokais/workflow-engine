import { useEffect, useRef } from 'react';

/**
 * Per-instance WebSocket: snapshot/update messages for one instance.
 * Auto-reconnects every 2s until the instance reaches a terminal state
 * or the component unmounts / instanceId changes.
 *
 * `onMessage` is kept in a ref so changing identity across renders does
 * not cause reconnects (the effect only depends on instanceId).
 */
export function useInstanceSocket(instanceId: string | null, onMessage: (msg: any) => void) {
  const handlerRef = useRef(onMessage);
  handlerRef.current = onMessage;

  useEffect(() => {
    if (!instanceId) return;
    const id = instanceId; // capture for the lifetime of this effect
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${location.host}/ws/instance/${id}`;
    let ws: WebSocket | null = null;
    let closed = false;

    const connect = () => {
      if (closed) return;
      ws = new WebSocket(wsUrl);
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type !== 'snapshot' && msg.type !== 'update') return;
          // Guard: only process messages for the instance this socket was created for
          if (msg.instance && msg.instance.id !== id) return;
          handlerRef.current(msg);
          // Close WS when instance reaches terminal state — no more updates expected
          if (msg.instance?.status === 'COMPLETED' || msg.instance?.status === 'TERMINATED') {
            closed = true; ws?.close();
          }
        } catch {}
      };
      ws.onclose = () => {
        // Auto-reconnect after 2s unless explicitly closed (instance changed or terminal)
        if (!closed) setTimeout(connect, 2000);
      };
      ws.onerror = () => { ws?.close(); };
    };
    connect();

    return () => { closed = true; ws?.close(); };
  }, [instanceId]);
}

/**
 * Global monitor WebSocket: 'changed' events for any instance on the server.
 * Auto-reconnects every 2s so the list keeps updating after backend restarts.
 */
export function useMonitorSocket(onChanged: (msg: any) => void) {
  const handlerRef = useRef(onChanged);
  handlerRef.current = onChanged;

  useEffect(() => {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${location.host}/ws/monitor`;
    let ws: WebSocket | null = null;
    let closed = false;
    let retryTimer: number | undefined;

    const connect = () => {
      if (closed) return;
      ws = new WebSocket(wsUrl);
      ws.onopen = () => console.log('[WS monitor] connected');
      ws.onclose = (e) => {
        console.log('[WS monitor] closed', e.code, e.reason);
        if (!closed) retryTimer = window.setTimeout(connect, 2000);
      };
      ws.onerror = (e) => console.error('[WS monitor] error', e);
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'changed') handlerRef.current(msg);
        } catch {}
      };
    };
    connect();

    return () => {
      closed = true;
      if (retryTimer) window.clearTimeout(retryTimer);
      ws?.close();
    };
  }, []);
}
