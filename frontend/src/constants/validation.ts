/** Reglas de validación compartidas con el backend (registro y cambio de contraseña). */

export const PASSWORD_MIN = 8;
/** BCrypt solo usa los primeros 72 bytes. */
export const PASSWORD_MAX = 72;
export const MIN_AGE = 12;
export const ADULT_AGE = 18;

/** Mensaje de error de la contraseña, o null si cumple: 8–72 caracteres, al menos una letra y un número. */
export function passwordError(password: string): string | null {
  if (password.length < PASSWORD_MIN) return `Debe tener al menos ${PASSWORD_MIN} caracteres`;
  if (password.length > PASSWORD_MAX) return `Puede tener como máximo ${PASSWORD_MAX} caracteres`;
  if (!/\p{L}/u.test(password)) return "Incluye al menos una letra";
  if (!/\d/.test(password)) return "Incluye al menos un número";
  return null;
}

export const PASSWORD_HINT = `Mínimo ${PASSWORD_MIN} caracteres, con letras y números`;

export function currentYear(): number {
  return new Date().getFullYear();
}

/** Edad aproximada a partir del año de nacimiento (igual que el backend: año actual − año de nacimiento). */
export function ageFromBirthYear(birthYear: number): number {
  return currentYear() - birthYear;
}
