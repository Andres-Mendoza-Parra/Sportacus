import { Redirect } from "expo-router";

import { ScreenSkeleton } from "../components/ui/Skeleton";
import { useAuth } from "../context/AuthContext";

/**
 * Punto de entrada. Sin sesión → /login. Con sesión → directo a Mi Base (o al mundo activo):
 * no hay pasos obligatorios; el perfil se completa a tu ritmo desde la app.
 */
export default function Index() {
  const { token, isLoading } = useAuth();
  if (isLoading) return <ScreenSkeleton />;
  return <Redirect href={token ? "/home" : "/login"} />;
}
