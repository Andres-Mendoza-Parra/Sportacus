import React from "react";
import { View } from "react-native";

import { BlockLog, BlockView, SessionUi } from "../../services/types";
import { Card } from "../ui/Card";
import BlockHeader from "./BlockHeader";
import DistanceBlock from "./blocks/DistanceBlock";
import GenericBlock from "./blocks/GenericBlock";
import HoldBlock from "./blocks/HoldBlock";
import IntervalBlock from "./blocks/IntervalBlock";
import RepsBlock from "./blocks/RepsBlock";
import { BlockRendererProps } from "./blocks/shared";
import TimeBlock from "./blocks/TimeBlock";
import PainSlider from "./checks/PainSlider";

interface Props {
  block: BlockView;
  entries: BlockLog[];
  onChange: (entries: BlockLog[]) => void;
  ui: SessionUi;
  speak: (text: string) => void;
  pain: number | undefined;
  onPain: (pain: number) => void;
  onSwap: () => void;
  swapping: boolean;
}

function Renderer(props: BlockRendererProps) {
  switch (props.block.prescription.type) {
    case "reps":
      return <RepsBlock {...props} />;
    case "hold":
      return <HoldBlock {...props} />;
    case "time":
      return <TimeBlock {...props} />;
    case "interval":
      return <IntervalBlock {...props} />;
    case "distance":
      return <DistanceBlock {...props} />;
    default:
      return <GenericBlock {...props} />;
  }
}

/** Un bloque de la sesión: cabecera + renderizador según `prescription.type` + dolor del bloque si el mundo lo pide. */
export default function BlockPlayer({ block, entries, onChange, ui, speak, pain, onPain, onSwap, swapping }: Props) {
  return (
    <View style={{ gap: 14 }}>
      <Card>
        <BlockHeader block={block} onSwap={onSwap} swapping={swapping} />
      </Card>
      {/* key: al cambiar de bloque (o de ejercicio tras un cambio) los temporizadores empiezan de cero. */}
      <Renderer key={`${block.id}:${block.movementKey ?? ""}`} block={block} entries={entries} onChange={onChange} ui={ui} speak={speak} />
      {ui.logFields.includes("pain") ? (
        <PainSlider compact value={pain} onChange={onPain} label="¿Dolor durante este bloque?" max={block.prescription.painMax} />
      ) : null}
    </View>
  );
}
