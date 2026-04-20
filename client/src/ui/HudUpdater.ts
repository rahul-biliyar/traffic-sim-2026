import { TickDelta, GameStateSnapshot } from "../types";

const WEATHER_ICONS: Record<string, string> = {
  CLEAR: "☀️",
  RAIN: "🌧️",
  HEAVY_RAIN: "⛈️",
  FOG: "🌫️",
  SNOW: "❄️",
  ICE: "🧊",
  BLIZZARD: "🌨️",
  THUNDERSTORM: "⚡",
};

/**
 * Updates the HTML HUD elements with game state data.
 */
export class HudUpdater {
  updateFromSnapshot(snapshot: GameStateSnapshot): void {
    this.setTextContent("hud-rating", snapshot.player.ratingGrade);
    this.setRatingClass(snapshot.player.ratingGrade);
    this.setTextContent("hud-score", snapshot.player.ratingScore.toFixed(2));
    this.setTextContent("hud-rp", snapshot.player.roadPoints.toString());
    this.setTextContent("hud-bt", snapshot.player.blueprintTokens.toString());
    this.setTextContent("hud-season", snapshot.currentSeason);
    this.setTextContent(
      "hud-weather",
      WEATHER_ICONS[snapshot.currentWeather] ?? "☀️",
    );
  }

  updateFromTick(delta: TickDelta): void {
    if (delta.ratingUpdate) {
      this.setTextContent("hud-rating", delta.ratingUpdate.grade);
      this.setRatingClass(delta.ratingUpdate.grade);
      this.setTextContent("hud-score", delta.ratingUpdate.score.toFixed(2));
    }
    if (delta.vehicleUpdates) {
      this.setTextContent(
        "hud-vehicles",
        delta.vehicleUpdates.length.toString(),
      );
    }
    if (delta.weatherUpdate) {
      this.setTextContent(
        "hud-weather",
        WEATHER_ICONS[delta.weatherUpdate.weather] ?? "☀️",
      );
      this.setTextContent("hud-season", delta.weatherUpdate.season);
    }

    // Update event panel
    this.updateEventPanel(delta);
  }

  private updateEventPanel(delta: TickDelta): void {
    const panel = document.getElementById("event-panel");
    if (!panel) return;

    if (!delta.eventUpdates || delta.eventUpdates.length === 0) {
      panel.innerHTML = "";
      return;
    }

    panel.innerHTML = delta.eventUpdates
      .map(
        (e) => `
      <div class="event-card ${e.phase === "ACTIVE" ? "active" : ""}">
        <div class="event-type">${e.type.replace(/_/g, " ")}</div>
        <div class="event-timer">${e.phase} — ${Math.ceil(e.timeRemaining)}s</div>
      </div>
    `,
      )
      .join("");
  }

  private setTextContent(id: string, text: string): void {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
  }

  private setRatingClass(grade: string): void {
    const el = document.getElementById("hud-rating");
    if (!el) return;
    el.className = "hud-value rating-" + grade;
  }
}
