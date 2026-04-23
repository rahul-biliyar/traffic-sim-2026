// Protocol types matching server DTOs

export interface TickDelta {
  tickNumber: number;
  vehicleUpdates: VehicleUpdate[];
  removedVehicleIds: number[];
  signalUpdates: SignalUpdate[];
  ratingUpdate: RatingUpdate;
  eventUpdates: EventUpdate[];
  weatherUpdate: WeatherUpdate;
  playerUpdate: PlayerUpdate;
}

export interface VehicleUpdate {
  id: number;
  x: number;
  y: number;
  speed: number;
  angle: number;
  type: string;
  laneIndex: number;
  elevation: number;
}

export interface SignalUpdate {
  intersectionId: string;
  state: string;
  timeRemaining: number;
}

export interface RatingUpdate {
  score: number;
  grade: string;
  breakdown: Record<string, number>;
}

export interface EventUpdate {
  eventId: string;
  type: string;
  phase: string;
  timeRemaining: number;
}

export interface WeatherUpdate {
  weather: string;
  season: string;
  intensity: number;
}

export interface PlayerUpdate {
  roadPoints: number;
  blueprintTokens: number;
  vehiclesDelivered: number;
}

export interface GameStateSnapshot {
  tickNumber: number;
  mapWidth: number;
  mapHeight: number;
  terrain: number[][];
  elevation: number[][];
  roads: RoadSegmentData[];
  intersections: IntersectionData[];
  vehicles: VehicleData[];
  districts: DistrictData[];
  buildings: BuildingSnapshotData[];
  player: PlayerData;
  currentSeason: string;
  currentWeather: string;
  forecasts: EventForecast[];
}

export interface RoadSegmentData {
  id: string;
  fromId: string;
  toId: string;
  lanes: number;
  speedLimit: number;
  roadType: string;
  condition: number;
  congestion: number;
}

export interface IntersectionData {
  id: string;
  x: number;
  y: number;
  type: string;
  signalState: string;
}

export interface VehicleData {
  id: number;
  x: number;
  y: number;
  speed: number;
  angle: number;
  type: string;
  laneIndex: number;
}

export interface DistrictData {
  id: string;
  name: string;
  number: number;
  unlocked: boolean;
  tier: number;
  unlockProgress: number;
  vehiclesRequired: number;
  ratingRequired: string | null;
  centerX: number;
  centerY: number;
}

export interface PlayerData {
  roadPoints: number;
  blueprintTokens: number;
  ratingGrade: string;
  ratingScore: number;
  cityTier: number;
  vehiclesDelivered: number;
}

export interface EventForecast {
  eventId: string;
  eventType: string;
  timeUntilStart: number;
  severity: string;
  description: string;
  choices: string[];
}

export interface PlayerCommand {
  type: string;
  action?: string;
  targetId?: string;
  x?: number;
  y?: number;
  x2?: number;
  y2?: number;
  data?: string;
}

export interface BuildingSnapshotData {
  id: string;
  x: number;
  z: number;
  width: number;
  depth: number;
  height: number;
  style: string;
  districtNumber: number;
  colorIndex: number;
}
