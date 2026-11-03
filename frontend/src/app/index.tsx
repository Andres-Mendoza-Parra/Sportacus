import { Redirect } from "expo-router";

import { ScreenSkeleton } from "../components/ui/Skeleton";
import { useAuth } from "../context/AuthContext";

/**
 * Punto de entrada de la app. Mientras se restaura la sesión muestra un
 * esqueleto; luego redirige a /home si hay sesión activa, o a /login si no.
 */
export default function Index() {
  const { token, isLoading } = useAuth();

  if (isLoading) return <ScreenSkeleton />;

  return <Redirect href={token ? "/home" : "/login"} />;
}
