import React from "react";

import { DashboardFocus } from "../../services/dashboardService";
import RadarChart from "../RadarChart";
import BarsChart from "./BarsChart";
import DonutChart from "./DonutChart";
import GaugeChart from "./GaugeChart";

/** Elige el gráfico según focus.type (radar | donut | bars | gauge). */
export default function FocusChart({ focus }: { focus: DashboardFocus }) {
  switch (focus.type) {
    case "donut":
      return <DonutChart items={focus.items} />;
    case "bars":
      return <BarsChart items={focus.items} />;
    case "gauge":
      return <GaugeChart items={focus.items} />;
    case "radar":
    default:
      return <RadarChart points={focus.items.map((i) => ({ label: i.label, value: i.value }))} />;
  }
}
