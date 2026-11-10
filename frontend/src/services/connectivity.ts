import { useSyncExternalStore } from "react";

/**
 * Estado de conexión observado a partir de las propias peticiones (sin dependencias nativas):
 * un error de red marca "sin conexión" y cualquier respuesta del servidor la vuelve a marcar.
 */
let online = true;
const listeners = new Set<() => void>();

export function setOnline(next: boolean): void {
  if (online === next) return;
  online = next;
  listeners.forEach((l) => l());
}

export function isOnline(): boolean {
  return online;
}

export function subscribeOnline(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** true mientras el servidor responde; false tras un error de red hasta la siguiente respuesta. */
export function useOnline(): boolean {
  return useSyncExternalStore(subscribeOnline, isOnline, isOnline);
}
