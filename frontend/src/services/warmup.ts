/** Calentamiento y enfriamiento guiados, adaptados a los grupos musculares del día. */
export interface TimedMove {
  title: string;
  seconds: number;
  tip: string;
}

const UPPER = ["Pecho", "Espalda", "Hombros", "Bíceps", "Tríceps", "Antebrazos"];
const LEGS = ["Glúteos", "Cuádriceps", "Isquiotibiales", "Pantorrillas", "Aductores"];

function has(groups: string[], set: string[]): boolean {
  return groups.some((g) => set.includes(g));
}

export function buildWarmup(groups: string[]): TimedMove[] {
  const moves: TimedMove[] = [
    { title: "Marcha en el lugar", seconds: 60, tip: "Eleva las rodillas poco a poco y sube el ritmo." },
    { title: "Círculos de cadera", seconds: 30, tip: "Amplios y lentos, 15 s por lado." },
  ];
  if (has(groups, UPPER) || groups.length === 0) {
    moves.push({ title: "Círculos de brazos", seconds: 30, tip: "Hacia adelante y hacia atrás, aumentando el rango." });
  }
  if (has(groups, LEGS) || groups.includes("Cuerpo completo") || groups.length === 0) {
    moves.push({ title: "Sentadillas sin carga", seconds: 40, tip: "Despacio, rodillas alineadas con los pies." });
  }
  if (groups.includes("Core") || groups.includes("Espalda")) {
    moves.push({ title: "Gato-camello", seconds: 30, tip: "En cuatro apoyos, alterna arquear y redondear la espalda." });
  }
  moves.push({ title: "Rotación de tronco", seconds: 30, tip: "Gira suave a cada lado sin forzar." });
  return moves;
}

export function buildCooldown(groups: string[]): TimedMove[] {
  const moves: TimedMove[] = [
    { title: "Respiración profunda", seconds: 40, tip: "Inhala por la nariz 4 s, exhala por la boca 6 s." },
  ];
  if (groups.includes("Pecho") || groups.includes("Hombros")) {
    moves.push({ title: "Estiramiento de pecho en la pared", seconds: 30, tip: "Brazo apoyado, gira el cuerpo hacia el lado contrario." });
  }
  if (groups.includes("Espalda")) {
    moves.push({ title: "Postura del niño", seconds: 40, tip: "Sienta la cadera sobre los talones y estira los brazos al frente." });
  }
  if (groups.includes("Cuádriceps")) {
    moves.push({ title: "Estiramiento de cuádriceps", seconds: 30, tip: "De pie, talón hacia el glúteo; 15 s por pierna." });
  }
  if (groups.includes("Isquiotibiales") || groups.includes("Pantorrillas")) {
    moves.push({ title: "Estiramiento de isquiotibiales", seconds: 30, tip: "Una pierna estirada al frente, inclínate con la espalda recta." });
  }
  if (groups.includes("Glúteos")) {
    moves.push({ title: "Figura 4 de glúteo", seconds: 40, tip: "Tobillo sobre la rodilla contraria, 20 s por lado." });
  }
  if (groups.includes("Core")) {
    moves.push({ title: "Cobra suave", seconds: 30, tip: "Boca abajo, apoya antebrazos y abre el pecho sin dolor lumbar." });
  }
  if (groups.includes("Bíceps") || groups.includes("Tríceps") || groups.includes("Antebrazos")) {
    moves.push({ title: "Estiramiento de brazos", seconds: 30, tip: "Brazo estirado al frente, tira suavemente de los dedos hacia ti." });
  }
  if (moves.length === 1) {
    moves.push({ title: "Estiramiento global", seconds: 40, tip: "Eleva los brazos y alárgate hacia arriba, luego baja despacio." });
  }
  return moves;
}
