interface CryptoLike {
  getRandomValues?: <T extends Uint8Array>(array: T) => T;
  randomUUID?: () => string;
}

/** UUID v4 (RFC 4122) sin dependencias: usa crypto del entorno si existe y, si no, Math.random. */
export function uuidV4(): string {
  const c = (globalThis as { crypto?: CryptoLike }).crypto;
  if (c?.randomUUID) {
    try {
      return c.randomUUID();
    } catch {
      // algunos entornos lo exponen pero exigen contexto seguro: seguimos con la versión manual
    }
  }
  const bytes = new Uint8Array(16);
  if (c?.getRandomValues) {
    c.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // versión 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variante RFC 4122
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
