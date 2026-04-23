import {
  TickDelta,
  GameStateSnapshot,
  DistrictData,
  PlayerCommand,
} from "../types";

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

type CommandSender = (cmd: PlayerCommand) => void;
type OnDistrictUnlock = (
  districtId: string,
  centerX: number,
  centerY: number,
) => void;

/**
 * Updates the HTML HUD elements with game state data.
 */
export class HudUpdater {
  private districts: DistrictData[] = [];
  private sendCommand: CommandSender | null = null;
  private onDistrictUnlock: OnDistrictUnlock | null = null;
  private previouslyUnlocked: Set<string> = new Set();

  setMayorName(name: string): void {
    this.setTextContent("hud-mayor", name || "Mayor");
  }

  setSendCommand(fn: CommandSender): void {
    this.sendCommand = fn;
  }

  setOnDistrictUnlock(fn: OnDistrictUnlock): void {
    this.onDistrictUnlock = fn;
  }

  updateFromSnapshot(snapshot: GameStateSnapshot): void {
    this.setTextContent("hud-rating", snapshot.player.ratingGrade);
    this.setRatingClass(snapshot.player.ratingGrade);
    this.setTextContent(
      "hud-budget",
      this.formatCurrency(snapshot.player.roadPoints),
    );
    this.setTextContent("hud-season", snapshot.currentSeason);
    this.setTextContent(
      "hud-weather",
      WEATHER_ICONS[snapshot.currentWeather] ?? "☀️",
    );

    // Store districts and build panel
    this.districts = snapshot.districts ?? [];
    // Track initially unlocked districts
    for (const d of this.districts) {
      if (d.unlocked) this.previouslyUnlocked.add(d.id);
    }
    this.buildDistrictPanel();
    this.setupDistrictToggle();
  }

  updateFromTick(delta: TickDelta): void {
    if (delta.ratingUpdate) {
      this.setTextContent("hud-rating", delta.ratingUpdate.grade);
      this.setRatingClass(delta.ratingUpdate.grade);
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
    if (delta.playerUpdate) {
      this.setTextContent(
        "hud-budget",
        this.formatCurrency(delta.playerUpdate.roadPoints),
      );
    }

    // Update event panel
    this.updateEventPanel(delta);
  }

  private buildDistrictPanel(): void {
    const list = document.getElementById("district-list");
    if (!list) return;

    list.innerHTML = this.districts
      .map((d) => {
        const statusClass = d.unlocked ? "unlocked" : "locked";
        const req = d.unlocked ? "✅ Unlocked" : this.formatRequirements(d);
        const clickAttr = d.unlocked ? "" : `data-district-id="${d.id}"`;
        return `
          <div class="district-card ${statusClass}" ${clickAttr}>
            <div class="district-name">${d.name}</div>
            <div class="district-req">${req}</div>
          </div>`;
      })
      .join("");

    // Attach click handlers for locked districts
    list.querySelectorAll(".district-card.locked").forEach((card) => {
      card.addEventListener("click", () => {
        const id = (card as HTMLElement).dataset.districtId;
        if (id && this.sendCommand) {
          this.sendCommand({ type: "unlock_district", data: id });
        }
      });
    });
  }

  private formatRequirements(d: DistrictData): string {
    const parts: string[] = [];
    if (d.vehiclesRequired > 0) {
      parts.push(`🚗 ${d.vehiclesRequired} vehicles`);
    }
    if (d.ratingRequired) {
      parts.push(`⭐ Rating ${d.ratingRequired}`);
    }
    return parts.length ? `🔒 ${parts.join(" · ")}` : "🔒 Locked";
  }

  private setupDistrictToggle(): void {
    const toggle = document.getElementById("district-toggle");
    const panel = document.getElementById("district-panel");
    if (!toggle || !panel) return;
    // Remove previous listeners by cloning
    const newToggle = toggle.cloneNode(true) as HTMLElement;
    toggle.parentNode?.replaceChild(newToggle, toggle);
    newToggle.addEventListener("click", () => {
      panel.classList.toggle("visible");
    });
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

  showUnlockNotification(name: string): void {
    const el = document.getElementById("unlock-notification");
    if (!el) return;
    el.innerHTML = `<h2>District Unlocked</h2><p>${name}</p>`;
    el.style.display = "block";
    setTimeout(() => {
      el.style.display = "none";
    }, 3000);
  }

  checkDistrictUnlocks(districts: DistrictData[]): void {
    for (const d of districts) {
      if (d.unlocked && !this.previouslyUnlocked.has(d.id)) {
        this.previouslyUnlocked.add(d.id);
        this.showUnlockNotification(d.name);
        // Trigger camera move to new district
        if (this.onDistrictUnlock) {
          this.onDistrictUnlock(d.id, d.centerX, d.centerY);
        }
      }
    }
    this.districts = districts;
    this.buildDistrictPanel();
  }

  private formatCurrency(cents: number): string {
    const dollars = cents / 100;
    if (dollars >= 1000000) return `$${(dollars / 1000000).toFixed(1)}M`;
    if (dollars >= 1000) return `$${(dollars / 1000).toFixed(0)}K`;
    return `$${dollars.toFixed(0)}`;
  }
}
