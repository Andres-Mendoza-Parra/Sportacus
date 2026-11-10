import React from "react";
import { View } from "react-native";

import { Widget } from "../../services/types";
import { TourTarget } from "../tour/TourContext";
import { BarsWidget, GaugeWidget, LineWidget, RadarWidget, WeekBarsWidget } from "./ChartWidgets";
import GoalWidget from "./GoalWidget";
import { AnyTypedWidget, narrowWidget } from "./guards";
import InsightsWidget from "./InsightsWidget";
import NextSessionWidget from "./NextSessionWidget";
import NoticeWidget from "./NoticeWidget";
import PhaseTrackWidget from "./PhaseTrackWidget";
import { CheckinWidget, ProfileRingWidget, QuestionWidget, ReadinessWidget, WorldSuggestionsWidget } from "./Profile360Widgets";
import SkillTreeWidget from "./SkillTreeWidget";
import StatRowWidget from "./StatRowWidget";
import TrafficLightWidget from "./TrafficLightWidget";

/** Registro de componentes por `type` de widget (docs/16 §6). */
function renderWidget(w: AnyTypedWidget, delay: number): React.ReactNode {
  switch (w.type) {
    case "next_session":
      return <NextSessionWidget widget={w} delay={delay} />;
    case "stat_row":
      return <StatRowWidget widget={w} delay={delay} />;
    case "line":
      return <LineWidget widget={w} delay={delay} />;
    case "week_bars":
      return <WeekBarsWidget widget={w} delay={delay} />;
    case "bars":
      return <BarsWidget widget={w} delay={delay} />;
    case "radar":
      return <RadarWidget widget={w} delay={delay} />;
    case "gauge":
      return <GaugeWidget widget={w} delay={delay} />;
    case "skill_tree":
      return <SkillTreeWidget widget={w} delay={delay} />;
    case "traffic_light":
      return <TrafficLightWidget widget={w} delay={delay} />;
    case "goal":
      return <GoalWidget widget={w} delay={delay} />;
    case "insights":
      return <InsightsWidget widget={w} delay={delay} />;
    case "phase_track":
      return <PhaseTrackWidget widget={w} delay={delay} />;
    case "notice":
      return <NoticeWidget widget={w} delay={delay} />;
    case "profile_ring":
      return <ProfileRingWidget widget={w} delay={delay} />;
    case "question":
      return <QuestionWidget widget={w} delay={delay} />;
    case "checkin":
      return <CheckinWidget widget={w} delay={delay} />;
    case "world_suggestions":
      return <WorldSuggestionsWidget widget={w} delay={delay} />;
    case "readiness":
      return <ReadinessWidget widget={w} delay={delay} />;
    default:
      return null;
  }
}

/** Widgets que el recorrido inicial puede resaltar. */
const TOUR_WIDGETS = new Set<string>(["profile_ring", "question", "checkin", "readiness"]);

/**
 * Pinta los widgets del servidor en orden. Los tipos desconocidos o con `data` malformada se
 * omiten: así el backend puede añadir widgets nuevos sin romper versiones anteriores de la app.
 */
export default function WidgetList({ widgets, gap = 14 }: { widgets: Widget[]; gap?: number }) {
  const typed = widgets.map(narrowWidget).filter((w): w is AnyTypedWidget => w !== null);
  return (
    <View style={{ gap }}>
      {typed.map((w, i) =>
        TOUR_WIDGETS.has(w.type) ? (
          <TourTarget key={`${w.type}:${w.key}:${i}`} id={`widget-${w.type}`}>
            {renderWidget(w, Math.min(i, 6) * 70)}
          </TourTarget>
        ) : (
          <React.Fragment key={`${w.type}:${w.key}:${i}`}>{renderWidget(w, Math.min(i, 6) * 70)}</React.Fragment>
        ),
      )}
    </View>
  );
}
