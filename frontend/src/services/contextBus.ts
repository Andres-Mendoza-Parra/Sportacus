/**
 * Aviso de "los datos del usuario cambiaron" (cuestionarios, rutina). El chat lo usa para saber
 * cuándo el contexto que le dio al asistente quedó desactualizado.
 */
let version = 0;
const listeners = new Set<() => void>();

export function invalidateUserContext(): void {
  version += 1;
  listeners.forEach((listener) => listener());
}

export function getContextVersion(): number {
  return version;
}

export function subscribeContext(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
