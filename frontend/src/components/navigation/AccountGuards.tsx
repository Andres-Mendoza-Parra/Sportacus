import { router } from "expo-router";
import React, { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "../../context/AuthContext";
import { pendingLegal } from "../../services/legalService";
import { loadRawPref } from "../../services/prefs";
import { fetchAccount } from "../../services/profileService";
import { AccountView } from "../../services/types";
import LegalAcceptModal from "../legal/LegalAcceptModal";
import { useTour } from "../tour/TourContext";
import { TOUR_DONE_PREF } from "../tour/TourOverlay";

/**
 * Comprobaciones al entrar a las pestañas, una vez por sesión:
 * 1. Si la Política o los Términos cambiaron de versión, pide aceptarlos (modal bloqueante).
 * 2. Si es el primer ingreso (`tutorialCompletedAt` null), lanza el recorrido inicial.
 */
export default function AccountGuards() {
  const { token, logout } = useAuth();
  const tour = useTour();
  const [account, setAccount] = useState<AccountView | null>(null);
  const checkedFor = useRef<string | null>(null);
  const tourOfferedFor = useRef<string | null>(null);
  const tokenRef = useRef(token);
  useEffect(() => {
    tokenRef.current = token;
  });

  const load = useCallback(async () => {
    try {
      setAccount(await fetchAccount());
    } catch {
      // sin conexión: se vuelve a comprobar en el próximo ingreso
    }
  }, []);

  useEffect(() => {
    if (!token || checkedFor.current === token) return;
    checkedFor.current = token;
    load();
  }, [token, load]);

  const pending = pendingLegal(account);
  const startTour = tour.start;

  useEffect(() => {
    if (!token || !account || pending.length > 0 || tourOfferedFor.current === token) return;
    tourOfferedFor.current = token;
    if (account.tutorialCompletedAt) return;
    const owner = token;
    (async () => {
      const done = await loadRawPref<boolean>(TOUR_DONE_PREF, false);
      if (done) return;
      // Deja que el inicio se pinte antes de resaltar zonas.
      setTimeout(() => {
        if (tokenRef.current === owner) startTour();
      }, 900);
    })();
  }, [token, account, pending.length, startTour]);

  if (!token || pending.length === 0) return null;
  return (
    <LegalAcceptModal
      pending={pending}
      onAccepted={() => {
        load();
      }}
      onSignOut={async () => {
        await logout();
        router.replace("/login");
      }}
    />
  );
}
