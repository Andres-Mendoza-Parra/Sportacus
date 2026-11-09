import { Href, Redirect } from "expo-router";
import { useEffect, useState } from "react";

import { ScreenSkeleton } from "../components/ui/Skeleton";
import { ErrorState } from "../components/ui/States";
import { useAuth } from "../context/AuthContext";
import { nextOnboardingStep } from "../navigation/onboarding";
import { extractErrorMessage } from "../services/api";
import { fetchAccount } from "../services/profileService";
import { useTheme } from "../theme";

/**
 * Punto de entrada. Sin sesión → /login. Con sesión consulta GET /profile y su estado de
 * recibimiento decide el siguiente paso (perfil → salud → elegir mundo) o entra a las pestañas.
 */
export default function Index() {
  const { token, isLoading, user, markOnboarded } = useAuth();
  const { colors } = useTheme();
  const [target, setTarget] = useState<Href | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (isLoading || !token) return;
    let cancelled = false;
    (async () => {
      try {
        const account = await fetchAccount();
        if (cancelled) return;
        const next = nextOnboardingStep(account);
        if (next === "/home") await markOnboarded();
        setTarget(next);
      } catch (e) {
        if (cancelled) return;
        // Sin conexión pero con el recibimiento ya hecho: se entra con la copia local.
        if (user?.onboardingCompleted) setTarget("/home");
        else setError(extractErrorMessage(e));
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading, token, attempt]);

  if (isLoading) return <ScreenSkeleton />;
  if (!token) return <Redirect href="/login" />;
  if (error) {
    return (
      <ErrorState
        message={error}
        onRetry={() => {
          setError(null);
          setAttempt((a) => a + 1);
        }}
        style={{ flex: 1, justifyContent: "center", backgroundColor: colors.bg }}
      />
    );
  }
  if (!target) return <ScreenSkeleton />;
  return <Redirect href={target} />;
}
