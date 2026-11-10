import { api } from "./api";
import { cachedRequest } from "./cache";
import { AccountView, LegalDocument, LegalDocumentKey, LegalVersion } from "./types";

/** Documentos legales vigentes (público, sin token). Se guarda copia para leerlos sin conexión. */
export function fetchLegalDocuments(): Promise<LegalDocument[]> {
  return cachedRequest("legal:documents", async () => (await api.get<LegalDocument[]>("/legal/documents")).data);
}

/** Registra la aceptación de un documento en su versión vigente. */
export async function acceptLegal(document: LegalDocumentKey | string, version: LegalVersion): Promise<void> {
  await api.post("/legal/accept", { document, version });
}

export interface PendingLegal {
  document: LegalDocumentKey;
  version: LegalVersion;
}

const same = (a: LegalVersion | null | undefined, b: LegalVersion) => a != null && String(a) === String(b);

/** Documentos que la cuenta debe (re)aceptar porque cambió su versión o nunca los aceptó. */
export function pendingLegal(account: AccountView | null | undefined): PendingLegal[] {
  const legal = account?.legal;
  if (!legal) return [];
  const outdated =
    legal.upToDate === false || !same(legal.acceptedPolicyVersion, legal.policyVersion) || !same(legal.acceptedTermsVersion, legal.termsVersion);
  if (legal.upToDate === true || !outdated) return [];
  // Se aceptan siempre los dos documentos vigentes (el servidor registra cada uno en consent_log).
  return [
    { document: "politica_datos", version: legal.policyVersion },
    { document: "terminos", version: legal.termsVersion },
  ];
}

/** Ruta de la app para cada documento. */
export const LEGAL_ROUTES = { politica_datos: "/legal/politica", terminos: "/legal/terminos" } as const;
