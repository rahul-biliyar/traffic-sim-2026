# Traffic Sim 2026 — Comprehensive Game Documentation

## 1. Game Overview

### 1.1 Concept & Theme

**Traffic Sim 2026** is a browser-based traffic infrastructure simulation sandbox game. The player assumes the role of a **Mayor** who inherits a small town and must build, manage, and expand the city's traffic infrastructure. Starting with a limited budget and a basic road network, the player grows the city from a modest town center into a sprawling metropolis spanning multiple districts — each with distinct characteristics, challenges, and vehicle types.

### 1.2 Core Gameplay Loop

1. **Observe** — Monitor traffic flow, congestion, vehicle wait times, and events
2. **Plan** — Decide where to place roads, signals, or demolish infrastructure
3. **Build** — Spend Road Points (RP) to place roads of various types or install traffic signals
4. **Manage** — React to dynamic weather, traffic events, and emergencies
5. **Expand** — Unlock new districts by meeting delivery and rating thresholds
6. **Upgrade** — Progress from local roads to highways, from stop signs to traffic signals

### 1.3 Win Condition & Progression

There is no strict "win" state — the game is an open-ended sandbox. Progress is measured by:

- **City Tier** (1–7, based on districts unlocked)
- **Traffic Rating** (F through S grade)
- **Road Points (RP)** accumulated
- **Vehicles Delivered** (completed routes)
- **Districts Unlocked** (up to 7)

---

## 2. Actors & Roles

### 2.1 Primary Actor: The Mayor (Player)

The player is the sole human actor. They interact with the game through:

- **Road Placement Tool** — Draw roads between two points
- **Signal Placement Tool** — Upgrade an intersection to a traffic signal
- **Demolish Tool** — Remove road segments for partial refund
- **Selection Tool** — Inspect intersections and roads

### 2.2 System Actors

| Actor                         | Role                                                                                     |
| ----------------------------- | ---------------------------------------------------------------------------------------- |
| **Vehicle Spawner**           | Autonomously spawns vehicles at tunnel entry points with randomized routes               |
| **Traffic Signal Controller** | Manages 6-phase signal cycles at signalized intersections                                |
| **Vehicle AI (IDM)**          | Each vehicle follows the Intelligent Driver Model for acceleration, braking, gap-keeping |
| **Weather System**            | Cycles seasons and weather conditions, modifying road speeds                             |
| **Event System**              | Generates random traffic events (rush hour, accidents, storms)                           |
| **Rating System**             | Continuously evaluates traffic performance                                               |
| **Currency System**           | Awards Road Points based on rating performance and deliveries                            |
| **Pathfinder**                | A\* algorithm routes vehicles through the road network                                   |

---

## 3. Use Cases

### UC-1: Start New Game

- **Actor**: Mayor
- **Precondition**: None
- **Flow**: Player opens the application → Client connects via WebSocket → Server creates a new game session with a procedurally generated city map → Server sends full game state snapshot → Client renders the 3D city
- **Postcondition**: Player sees the city with initial road network, buildings, terrain, and active traffic

### UC-2: Place Road

- **Actor**: Mayor
- **Precondition**: Player has sufficient Road Points; selected tool is "Road"
- **Flow**: Player clicks start point on map → Drags to end point → Releases mouse → Client sends `place_road` command with coordinates and road type → Server validates placement (cost, terrain feasibility) → Server deducts RP, creates intersection nodes, adds bidirectional road edges → Client receives updated state
- **Postcondition**: New road segment appears on map; RP balance reduced
- **Alternative**: Insufficient RP → Command rejected with error message

### UC-3: Place Traffic Signal

- **Actor**: Mayor
- **Precondition**: Target intersection exists; Player has ≥300 RP; selected tool is "Signal"
- **Flow**: Player clicks on an existing intersection → Client sends `place_signal` command → Server validates and upgrades intersection type to SIGNAL → Signal begins cycling (GREEN_NS → YELLOW_NS → RED_ALL_1 → GREEN_EW → YELLOW_EW → RED_ALL_2)
- **Postcondition**: Traffic signal installed and cycling; RP deducted by 300
- **Alternative**: Invalid intersection or insufficient RP → rejected

### UC-4: Demolish Road

- **Actor**: Mayor
- **Precondition**: Target road exists; selected tool is "Demolish"
- **Flow**: Player clicks on road segment → Client sends `demolish` command → Server removes the edge from road network → Player receives 20 RP partial refund
- **Postcondition**: Road segment removed; traffic re-routes

### UC-5: Observe Traffic Flow

- **Actor**: Mayor
- **Precondition**: Game is running
- **Flow**: Player watches vehicles moving in real-time → HUD displays: traffic rating (S/A/B/C/D/F), score, vehicle count, weather, season, Road Points, Blueprint Tokens → Player uses camera controls (WASD, zoom, rotate) to inspect different areas
- **Postcondition**: Player gains information to make decisions

### UC-6: Respond to Traffic Event

- **Actor**: Mayor, Event System
- **Precondition**: Event System generates a traffic event
- **Flow**: Event enters FORECAST phase (45s warning shown in event panel) → Event becomes ACTIVE (affects road conditions/speeds) → Player may place new roads or signals to mitigate → Event enters RESOLVING phase → Event COMPLETES → Player rewarded if handled well
- **Postcondition**: Event resolved; RP awarded for success, penalties for failure

### UC-7: Unlock New District

- **Actor**: Mayor, Currency System
- **Precondition**: Player meets district unlock requirements (vehicles delivered + rating grade)
- **Flow**: Player delivers enough vehicles and maintains required rating → District unlocks → New area becomes available with different terrain, building types, and road requirements → New vehicle types may become available
- **Postcondition**: New district accessible; city tier increases

### UC-8: Weather Affects Traffic

- **Actor**: Weather System
- **Precondition**: Game is running
- **Flow**: Season cycles (Spring → Summer → Fall → Winter, each 86400 game-seconds) → Weather changes every 2–7 minutes → Weather type applies speed multiplier to ALL road segments (rain=0.7×, snow=0.5×, fog=0.6×) → Vehicles automatically slow down
- **Postcondition**: Road effective speeds modified; traffic flow impacted

### UC-9: Vehicle Completes Route

- **Actor**: Vehicle AI, Currency System
- **Precondition**: Vehicle has been spawned with a valid route
- **Flow**: Vehicle follows A\*-computed route edge by edge → Uses IDM for realistic acceleration/braking → Obeys traffic signals (decelerates at red/yellow) → Reaches destination node → Vehicle despawned → Delivery counted → RP awarded (10 RP × rating multiplier)
- **Postcondition**: Vehicle removed; delivery counter incremented; RP added

---

## 4. Activity Flows

### 4.1 Game Initialization Flow

```
START
  → Application launched
  → Client creates WebSocket connection to /ws/game/{sessionId}
  → Server creates GameSession
    → Instantiate TrafficGame(seed)
      → CityMapGenerator generates 64×64 map
        → Generate height map (mountains north, ocean south, river, canyon)
        → Classify cells (WATER, FOREST, EMPTY)
        → Remove isolated water bodies (BFS flood-fill)
        → Mark beach cells (EMPTY near WATER → PARK)
        → Assign districts via Voronoi (7 geographic seeds)
        → RoadPlanner generates road network
          → Lay arterial grid lines (bridge water gaps ≤4)
          → Fill local roads per district spacing
          → Mark intersections (≥3 neighbors or H+V connections)
          → Add tunnel entries at mountain borders
          → Build road graph (nodes + edges)
          → Assign intersection control types
          → Prune dead-end nodes
        → BuildingPlacer places structures
          → Place buildings with road-access validation
          → Place trees (forest dense, farmland crops, vegetation)
        → Generate terrain elevation (flatten near roads, zone-based multipliers)
      → Initialize 7 game systems
      → Create GameLoop at 10Hz
  → Server sends full GameStateSnapshot to client
  → Client builds all renderers (Terrain, Roads, Buildings, Vehicles)
  → Game begins ticking
END
```

### 4.2 Game Tick Flow (every 100ms)

```
START
  → GameLoop.tick()
  → SystemOrchestrator.update() runs 7 systems in order:
    1. VehicleSpawnerSystem
       → Despawn vehicles with completed routes (position ≥ 0.99)
       → Award RP for each delivery
       → If vehicle count < MAX (500) and spawn timer elapsed (0.6s):
         → Pick random TUNNEL entry node
         → Pick destination: 60% interior signal/stop, 40% far tunnel exit
         → Run A* pathfinding (weight = distance / effectiveSpeed)
         → If path found: create Entity with VehicleInfo + VehicleMovement + VehicleRoute
    2. TrafficSignalSystem
       → For each SIGNAL intersection:
         → Decrement timer by dt
         → If timer ≤ 0: advance to next signal state, reset timer
    3. VehicleMovementSystem
       → Sort vehicles per edge by position
       → For each vehicle:
         → Get road speed limit × weather factor
         → Calculate gap to leader (same-edge, same-lane)
         → Apply IDM formula: accel = a × (1 - (v/v₀)⁴ - (s*/gap)²)
         → Check signal compliance (decelerate at >85% position on red/yellow)
         → Update speed and position on edge
         → If edge complete: advance to next edge in route
    4. WeatherSystem
       → Advance season timer
       → Advance weather timer; if elapsed, pick new weather
       → Apply speed modifier to all road segments
    5. EventSystem
       → Update active events (countdown timers, phase transitions)
       → Spawn new events if cooldown elapsed (60–180s between events)
    6. RatingSystem
       → Every 1s: compute weighted score from wait time, emergency response, accidents, throughput
       → Assign letter grade (S ≥ 0.95, A ≥ 0.80, B ≥ 0.65, C ≥ 0.50, D ≥ 0.35, F)
    7. CurrencySystem
       → Passive RP earning: ~2 RP/s × grade multiplier
  → GameWebSocket broadcasts TickDelta (vehicle positions, signals, rating, weather, events)
END
```

### 4.3 Road Placement Flow

```
START
  → Player selects Road tool (keyboard '2' or toolbar button)
  → Player presses mouse on map
  → Client raycasts to ground plane, records start (x1, y1)
  → Player drags and releases mouse
  → Client records end (x2, y2)
  → Client sends PlayerCommand { type: "place_road", x, y, x2, y2, data: roadType }
  → Server receives command via WebSocket
  → CommandHandler.handlePlaceRoad()
    → Validate road type (PATH/LOCAL/COLLECTOR/ARTERIAL/HIGHWAY)
    → Calculate RP cost (PATH=50, LOCAL=100, COLLECTOR=200, ARTERIAL=500, HIGHWAY=1000)
    → Check player has sufficient RP
    → [If insufficient] → Return failure result
    → Deduct RP from player
    → Create/find intersection nodes at start and end
    → Add bidirectional edges to RoadNetwork
    → Return success result
  → Client receives updated state in next tick
END
```

### 4.4 Vehicle Lifecycle Flow

```
START
  → VehicleSpawnerSystem detects capacity available
  → Pick random TUNNEL node as origin
  → Select destination (60% interior, 40% far tunnel)
  → A* pathfinding finds route (sequence of edge IDs)
  → [If no path] → Skip spawn, try next tick
  → Create Entity:
    → VehicleInfo: type (weighted random), origin, destination
    → VehicleMovement: edge=first, position=0, speed=0
    → VehicleRoute: list of edge IDs, index=0
  → LOOP (each tick):
    → IDM calculates target acceleration
    → Check signal at approaching intersection
    → Update speed += accel × dt
    → Update position += speed × dt / edgeLength
    → [If position ≥ 1.0] → Advance to next edge, reset position
    → [If route complete and position ≥ 0.99] → EXIT LOOP
  → Despawn entity
  → Award 10 RP × rating multiplier
  → Increment vehiclesDelivered
END
```

### 4.5 Event Lifecycle Flow

```
START
  → EventSystem cooldown expires
  → Select event type by tier (higher city tier = more event types)
  → Create TrafficEvent in FORECAST phase
  → HUD shows forecast card (event type, 45s countdown)
  → [45s passes]
  → Event enters ACTIVE phase
    → Apply event effects (speed reductions, road blocks, weather changes)
    → Duration: 90s–360s depending on type
  → Player may respond (place roads, signals, reroute traffic)
  → [Duration expires]
  → Event enters RESOLVING phase (effects diminishing)
  → Event enters COMPLETE phase
    → If handled well: Award 100 RP × rating multiplier
    → If failed: No reward; may penalize rating
  → Remove event from active list
END
```

---

## 5. State Diagrams

### 5.1 Vehicle States

```
States: SPAWNING → MOVING → WAITING_AT_SIGNAL → MOVING → ARRIVED → DESPAWNED

SPAWNING:
  - Entry: Entity created with route
  - Transition → MOVING: Always (immediate)

MOVING:
  - Entry: Vehicle has speed > 0, following IDM
  - Transition → WAITING_AT_SIGNAL: Approaching red/yellow signal at >85% edge position
  - Transition → ARRIVED: Route complete, position ≥ 0.99 on final edge

WAITING_AT_SIGNAL:
  - Entry: Vehicle decelerating/stopped at intersection
  - Transition → MOVING: Signal turns green for vehicle's direction

ARRIVED:
  - Entry: Vehicle reached destination
  - Transition → DESPAWNED: Immediate (next spawner tick)

DESPAWNED:
  - Entry: Entity removed, RP awarded
  - Terminal state
```

### 5.2 Traffic Signal States

```
States: GREEN_NS → YELLOW_NS → RED_ALL_1 → GREEN_EW → YELLOW_EW → RED_ALL_2

GREEN_NS:
  - Duration: 30s (configurable greenDuration)
  - North-South traffic flows
  - Transition → YELLOW_NS: Timer expires

YELLOW_NS:
  - Duration: 3s
  - North-South clearing
  - Transition → RED_ALL_1: Timer expires

RED_ALL_1:
  - Duration: 3s
  - All directions stopped (safety clearance)
  - Transition → GREEN_EW: Timer expires

GREEN_EW:
  - Duration: 30s
  - East-West traffic flows
  - Transition → YELLOW_EW: Timer expires

YELLOW_EW:
  - Duration: 3s
  - East-West clearing
  - Transition → RED_ALL_2: Timer expires

RED_ALL_2:
  - Duration: 3s
  - All directions stopped
  - Transition → GREEN_NS: Timer expires (cycle repeats)
```

### 5.3 Traffic Event States

```
States: FORECAST → ACTIVE → RESOLVING → COMPLETE

FORECAST:
  - Duration: 45s
  - Player warned via HUD event panel
  - Transition → ACTIVE: Forecast timer expires

ACTIVE:
  - Duration: 90s–360s (varies by event type)
  - Effects applied to roads/weather
  - Transition → RESOLVING: Active timer expires

RESOLVING:
  - Duration: Brief transition
  - Effects diminishing
  - Transition → COMPLETE: Immediate

COMPLETE:
  - Terminal state
  - Rewards/penalties applied
  - Event removed
```

### 5.4 Weather/Season States

```
Season Cycle: SPRING → SUMMER → FALL → WINTER → SPRING (86400s each)

Weather States (per season, random transitions every 2–7 minutes):
  CLEAR (speed factor: 1.0)
  RAIN (speed factor: 0.7)
  HEAVY_RAIN (speed factor: 0.6)
  FOG (speed factor: 0.6)
  SNOW (speed factor: 0.5) [Winter only]
  ICE (speed factor: 0.4) [Winter only]
  BLIZZARD (speed factor: 0.3) [Winter only]
  THUNDERSTORM (speed factor: 0.5)
```

### 5.5 Game Session States

```
States: UNINITIALIZED → GENERATING → READY → RUNNING → PAUSED → ENDED

UNINITIALIZED:
  - WebSocket connection opened
  - Transition → GENERATING: Session created

GENERATING:
  - Map generation pipeline running
  - Transition → READY: Generation complete, snapshot sent

READY:
  - Client has received snapshot and rendered scene
  - Transition → RUNNING: Tick scheduler started

RUNNING:
  - 10Hz game loop active
  - Broadcasting TickDelta every 100ms
  - Transition → ENDED: WebSocket closed

ENDED:
  - Tick scheduler cancelled
  - Terminal state
```

### 5.6 District Unlock States

```
States: LOCKED → UNLOCKABLE → UNLOCKED

LOCKED:
  - Requirements not met (vehiclesDelivered < threshold OR rating < threshold)
  - Transition → UNLOCKABLE: Both thresholds reached

UNLOCKABLE:
  - Player meets requirements
  - Transition → UNLOCKED: Automatic (system checks each tick)

UNLOCKED:
  - District available
  - New vehicle types and scenarios accessible
  - Terminal state (cannot re-lock)
```

---

## 6. Case Diagram (Class/Entity Relationships)

### 6.1 Core Domain Model

```
TrafficGame (central orchestrator)
  ├── EntityManager (manages all entities)
  │     └── Entity (ID + components map)
  │           ├── VehicleInfo (type, config, origin, destination)
  │           ├── VehicleMovement (edge, position, speed, lane)
  │           └── VehicleRoute (edge list, current index)
  ├── RoadNetwork (extends Graph<Intersection, RoadSegment>)
  │     ├── Node<Intersection> (id, position, data)
  │     │     └── Intersection (type, signalState, timer, tier)
  │     └── Edge<RoadSegment> (id, from, to, weight, data)
  │           └── RoadSegment (lanes, speedLimit, roadType, condition, congestion)
  ├── TileMap (2D grid of Tile objects)
  │     └── Tile (type, elevation, moisture, passable)
  ├── List<BuildingData> (id, x, z, width, depth, height, style, district, colorIndex)
  ├── List<District> (id, name, number, unlocked, tier, requirements)
  ├── PlayerProfile (RP, tokens, deliveries, rating, tier)
  ├── SystemOrchestrator
  │     └── List<GameSystem> (7 systems in execution order)
  ├── EventBus (pub/sub for game events)
  └── GameLoop (fixed timestep with accumulator)
```

### 6.2 Map Generation Classes

```
CityMapGenerator
  ├── Uses: Height map generation (FBM noise, rivers, canyons)
  ├── Uses: Cell classification (WATER, FOREST, EMPTY)
  ├── Uses: District assignment (Voronoi with 7 seeds)
  ├── Delegates to: RoadPlanner
  │     ├── Produces: RoadNetwork (graph of intersections and road segments)
  │     └── Modifies: Cell grid (ROAD, INTERSECTION cells)
  ├── Delegates to: BuildingPlacer
  │     ├── Produces: List<BuildingData> (buildings and vegetation)
  │     └── Modifies: Cell grid (BUILDING cells)
  └── Returns: GeneratedMap (TileMap, RoadNetwork, buildings, districtGrid)

Supporting Enums:
  ├── ZoneType (WATER, MOUNTAIN, CITY, SUBURB, COUNTRYSIDE, COAST, FOREST, INDUSTRIAL, DOWNTOWN)
  ├── CellType (EMPTY, ROAD, BUILDING, INTERSECTION, PARK, FARM, WATER, FOREST)
  ├── DistrictTemplate (7 districts with road/building parameters)
  ├── RoadSegment.RoadType (PATH, LOCAL, COLLECTOR, ARTERIAL, HIGHWAY)
  └── Intersection.IntersectionType (UNCONTROLLED, YIELD, STOP, SIGNAL, ROUNDABOUT, TUNNEL)
```

### 6.3 Game Systems Hierarchy

```
GameSystem (interface)
  ├── VehicleSpawnerSystem (spawn/despawn, A* routing)
  ├── TrafficSignalSystem (6-phase signal cycle management)
  ├── VehicleMovementSystem (IDM acceleration, signal compliance, lane management)
  ├── WeatherSystem (season cycle, weather changes, speed modifiers)
  ├── EventSystem (traffic event lifecycle management)
  ├── RatingSystem (weighted composite scoring every 1s)
  └── CurrencySystem (passive RP earning, delivery/event rewards)
```

### 6.4 Network Protocol Classes

```
Server → Client:
  GameStateSnapshot (full state on connect)
    ├── terrain: int[][] (zone type per cell)
    ├── elevation: double[][] (height per cell)
    ├── roads: List<RoadSegmentData> (fromId, toId, lanes, speedLimit, roadType)
    ├── intersections: List<IntersectionData> (id, x, y, type, signalState)
    ├── buildings: List<BuildingSnapshotData> (x, z, width, depth, height, style, colorIndex)
    ├── districts: List<DistrictData>
    ├── player: PlayerData
    └── weather/season/forecasts

  TickDelta (incremental per-tick)
    ├── vehicleUpdates: List<VehicleUpdate> (id, x, y, speed, angle, type, laneIndex)
    ├── removedVehicleIds: List<Long>
    ├── signalUpdates: List<SignalUpdate>
    ├── ratingUpdate: RatingUpdate
    ├── eventUpdates: List<EventUpdate>
    └── weatherUpdate: WeatherUpdate

Client → Server:
  PlayerCommand
    ├── type: "place_road" | "place_signal" | "demolish"
    ├── action, targetId
    ├── x, y, x2, y2
    └── data (road type string)
```

### 6.5 Client Rendering Pipeline

```
GameApp (orchestrator)
  ├── Three.js Scene
  │     ├── TerrainRenderer (unified vertex grid mesh with zone colors)
  │     ├── RoadRenderer (BoxGeometry strips, lane markings, bridges, tunnels, signals)
  │     ├── BuildingRenderer (InstancedMesh for buildings and vegetation types)
  │     └── VehicleRenderer (interpolated BoxGeometry per vehicle)
  ├── WebSocketClient (auto-reconnect, JSON message parsing)
  ├── StateBuffer (ring buffer of 5 TickDelta frames for interpolation)
  ├── InputHandler (keyboard/mouse, tool selection, raycasting)
  └── HudUpdater (rating, score, weather, events display)
```

---

## 7. Design Principles

### 7.1 Separation of Concerns

The architecture strictly separates engine, game logic, network protocol, and presentation:

- **Engine module** — Pure infrastructure with zero game knowledge (ECS, graph, pathfinding, events, spatial indexing, terrain primitives, game loop)
- **Game module** — Traffic simulation logic built on engine primitives (map generation, vehicle behavior, systems)
- **Shared module** — Protocol DTOs shared between server and client (snapshots, deltas, commands)
- **Server module** — Network transport only (WebSocket handling, session management, HTTP API)
- **Client module** — Pure presentation and input handling (Three.js rendering, camera, HUD)

### 7.2 Data-Driven Design

All game behavior is configured through data, not code:

- `GameConfig` centralizes all tuning constants (speeds, costs, timers, thresholds)
- `DistrictTemplate` enum defines district parameters (road spacing, building chance, default road type, speed limits)
- `VehicleConfig` record defines per-vehicle-type parameters (speed, acceleration, spawn weight, priority)
- `GameEventType` enum defines event durations, severities, and tier requirements

### 7.3 Single Responsibility Principle

Each system handles exactly one concern:

- `VehicleSpawnerSystem` — only spawning/despawning + route computation
- `VehicleMovementSystem` — only physics (IDM) + position updates
- `TrafficSignalSystem` — only signal state machine cycling
- `WeatherSystem` — only weather/season state transitions
- No system modifies another system's state directly

### 7.4 Interface Segregation

Engine interfaces are minimal:

- `GameSystem` — just `update(state, dt)` and `getName()`
- `GameState` — just `getTickNumber()` and `advanceTick()`
- `Component` — empty marker interface
- `GameEvent` — just `tick()`
- `Command` — just `type()`
- `Snapshot` / `StateDelta` — just `tickNumber()`

### 7.5 Open/Closed Principle

The system is extensible without modification:

- New vehicle types: add to `VehicleType` enum and `VehicleConfig`
- New districts: add to `DistrictTemplate` enum
- New events: add to `GameEventType` enum
- New game systems: implement `GameSystem` and register with orchestrator
- New road types: add to `RoadSegment.RoadType` enum

### 7.6 Deterministic Simulation

The game tick is deterministic for a given input sequence:

- Fixed timestep (100ms) ensures consistent physics regardless of frame rate
- Systems execute in a strict, deterministic order
- Client-side interpolation is purely visual (does not affect simulation state)

### 7.7 Authoritative Server

The server is the single source of truth:

- All game state lives on the server
- Client sends commands; server validates and applies
- Client receives state updates and renders them
- No client-side prediction or state mutation

---

## 8. Design Patterns

### 8.1 Entity-Component-System (ECS) Pattern

**Purpose**: Decouple game object identity from behavior, enabling flexible composition.

**Implementation**:

- `Entity` — Unique ID + map of Component instances (pure data containers)
- `Component` — Marker interface implemented by `VehicleInfo`, `VehicleMovement`, `VehicleRoute`
- `System` — `GameSystem` interface; systems iterate over entities with specific component sets
- `EntityManager` — Central registry with `withComponent(Class)` queries

**Usage**: Vehicles are entities composed of three components. `VehicleSpawnerSystem` queries entities with `VehicleInfo`. `VehicleMovementSystem` queries entities with `VehicleMovement` + `VehicleRoute`. Components can be added/removed dynamically.

### 8.2 Game Loop Pattern (Fixed Timestep with Accumulator)

**Purpose**: Ensure consistent simulation regardless of frame rate or processing delays.

**Implementation**:

- `GameLoop` maintains an accumulator of elapsed wall-clock time
- `tick()` drains the accumulator in fixed `fixedDt` (100ms) increments
- `getAlpha()` returns fractional remainder for client-side interpolation
- Server schedules `tick()` at 10Hz via `ScheduledExecutorService`

### 8.3 Observer Pattern (Event Bus)

**Purpose**: Decouple event producers from consumers.

**Implementation**:

- `EventBus` — Type-safe publish/subscribe with `ConcurrentHashMap` + `CopyOnWriteArrayList`
- `GameEvent` — Marker interface for all events
- `EventListener<T>` — Functional interface for handlers
- Thread-safe registration, synchronous dispatch

### 8.4 Command Pattern

**Purpose**: Encapsulate player actions as objects for validation and execution.

**Implementation**:

- `PlayerCommand` — Data object with type, coordinates, target ID
- `CommandHandler` — Interprets commands and applies to game state
- Commands sent from client via WebSocket, deserialized, and routed through `SessionManager` to `CommandHandler`
- `CommandResult` record returns success/failure + message

### 8.5 Strategy Pattern (Pathfinding Weight Function)

**Purpose**: Allow flexible pathfinding cost calculations.

**Implementation**:

- `PathFinder.findPath()` accepts a `BiFunction<Edge, Graph, Double>` weight function
- `VehicleSpawnerSystem` provides a custom weight: `edge.weight / effectiveSpeed` (factoring in congestion, road condition, weather)
- Negative weight return = impassable edge (blocked road)
- This allows the same A\* algorithm to be used for different routing strategies

### 8.6 Builder Pattern (Message Serialization)

**Purpose**: Construct complex protocol messages step by step.

**Implementation**:

- `MessageBuilder.buildSnapshot()` — Assembles full `GameStateSnapshot` from game state (terrain, roads, buildings, player, weather, etc.)
- `MessageBuilder.buildTickDelta()` — Assembles incremental `TickDelta` (vehicle positions interpolated along edges with lane offsets, signal states, rating, events)
- Separates serialization logic from game logic

### 8.7 State Machine Pattern (Traffic Signals & Events)

**Purpose**: Manage complex state transitions with clear rules.

**Implementation**:

- `Intersection.SignalState` — 6-state enum cycle: GREEN_NS → YELLOW_NS → RED_ALL_1 → GREEN_EW → YELLOW_EW → RED_ALL_2
- `TrafficEvent` — 4-phase lifecycle: FORECAST → ACTIVE → RESOLVING → COMPLETE
- `WeatherSystem` — Season cycle + weather state transitions
- Each state has a defined duration and a single successor state

### 8.8 Spatial Hashing Pattern

**Purpose**: Efficient proximity queries for game objects.

**Implementation**:

- `GridSpatialIndex<T>` — Grid-based spatial hash with configurable cell size
- Uses packed `(cx << 32 | cy)` as HashMap key
- Supports `queryRadius()` and `queryRect()` for area searches
- Used for spatial queries during simulation

### 8.9 Singleton Pattern (Session Management)

**Purpose**: Single point of access for game session management.

**Implementation**:

- `SessionManager` — `@Singleton` Micronaut bean
- `ConcurrentHashMap<String, GameSession>` for thread-safe session storage
- `createSession()`, `getSession()`, `startSession()` methods
- Each `GameSession` contains a `TrafficGame` instance, `CommandHandler`, and tick scheduler future

### 8.10 Flyweight Pattern (Instanced Rendering)

**Purpose**: Efficiently render thousands of similar objects.

**Implementation**:

- `BuildingRenderer` — Uses Three.js `InstancedMesh` to batch-render all buildings of the same style+color with a single draw call
- Trees (deciduous, pine), crops (wheat, corn), and fences each get their own instanced mesh
- Each instance only needs a transformation matrix, not a separate geometry/material
- Reduces draw calls from thousands to dozens

### 8.11 Adapter Pattern (Coordinate Mapping)

**Purpose**: Bridge different coordinate systems between server and client.

**Implementation**:

- Server uses 2D `(x, y)` coordinates on a flat grid
- Client uses Three.js 3D `(x, Y_up, z)` where server `y` maps to Three.js `z` and height is on the `Y` axis
- `MessageBuilder` handles the server→client coordinate transform when building vehicle positions
- Renderers consistently apply `serverY → threeZ` mapping

### 8.12 Graph/Adjacency List Pattern

**Purpose**: Model the road network for pathfinding and traffic simulation.

**Implementation**:

- `Graph<N, E>` — Generic directed weighted graph with adjacency lists
- `Node<D>` — Generic node wrapping domain data (`Intersection`)
- `Edge<D>` — Generic directed weighted edge wrapping domain data (`RoadSegment`)
- `RoadNetwork` extends `Graph<Intersection, RoadSegment>` with traffic-specific operations
- Bidirectional roads modeled as two directed edges

### 8.13 Template Method Pattern (Map Generation Pipeline)

**Purpose**: Define the skeleton of the map generation algorithm with customizable steps.

**Implementation**:

- `CityMapGenerator.generate()` defines a 7-step pipeline:
  1. Height map generation
  2. Cell classification
  3. Water cleanup
  4. Beach marking
  5. District assignment (Voronoi)
  6. Road planning (delegated to `RoadPlanner`)
  7. Building placement (delegated to `BuildingPlacer`)
- Each step can be modified independently without affecting the overall pipeline

---

## 9. Technical Architecture

### 9.1 Module Dependency Graph

```
engine (0 dependencies)
  ↑
game (depends on engine)
  ↑
shared (depends on game)
  ↑
server (depends on shared, engine, game) ← client (connects via WebSocket)
```

### 9.2 Technology Stack

| Layer            | Technology          | Version  |
| ---------------- | ------------------- | -------- |
| Runtime          | Java 17 LTS         | 17.0.18  |
| Build            | Gradle (Kotlin DSL) | 8.12     |
| Server Framework | Micronaut           | 4.7.6    |
| Transport        | WebSocket (Netty)   | —        |
| Client Runtime   | TypeScript          | 5.7.3    |
| 3D Engine        | Three.js            | 0.170.0  |
| Client Build     | Vite                | 6.x      |
| Noise            | FastNoiseLite       | vendored |

### 9.3 Performance Characteristics

| Metric              | Value                                          |
| ------------------- | ---------------------------------------------- |
| Server tick rate    | 10 Hz (100ms)                                  |
| Max vehicles        | 500                                            |
| Spawn interval      | 0.6s                                           |
| Map size            | 64×64 cells (1024×1024 world units)            |
| Tile size           | 16 world units                                 |
| Client target FPS   | 60 (requestAnimationFrame)                     |
| State buffer        | 5 frames (ring buffer)                         |
| WebSocket reconnect | up to 10 attempts, exponential backoff max 30s |
| Shadow map          | 4096×4096 PCFSoft                              |
| Terrain mesh        | (64 + 80 + 1)² = ~21,025 vertices              |

### 9.4 Road Network Specifications (US Standard)

| Road Type | Lane Width    | Speed Limit | Lanes | Cost (RP) | Center Marking         |
| --------- | ------------- | ----------- | ----- | --------- | ---------------------- |
| PATH      | 1.5× lane     | 50          | 1     | 50        | None                   |
| LOCAL     | 2× lane       | 30          | 1     | 100       | Dashed white           |
| COLLECTOR | 2× lane + 1   | 40          | 2     | 200       | Dashed white           |
| ARTERIAL  | 2× lane + 1.5 | 50          | 2     | 500       | Double yellow          |
| HIGHWAY   | 2× lane + 3   | 100         | 3     | 1000      | Double yellow + median |

### 9.5 Intersection Control Types (US Standards)

| Type         | Criteria                        | Visual                              |
| ------------ | ------------------------------- | ----------------------------------- |
| UNCONTROLLED | Default                         | Plain pad                           |
| YIELD        | Local meets collector, or 3-way | Inverted triangle signs             |
| STOP         | Local meets arterial            | Red octagon signs + white stop line |
| SIGNAL       | Player-placed or high-tier      | 3-light signal heads, 6-phase cycle |
| TUNNEL       | Mountain border entry           | Stone arch with keystone            |

### 9.6 Vehicle Types & Specifications

| Type       | Max Speed | Acceleration | Spawn Weight | Priority | Dimensions (W×H×D) |
| ---------- | --------- | ------------ | ------------ | -------- | ------------------ |
| CAR        | 60        | 3.5          | 0.60         | 0        | 3×2×5              |
| TRUCK      | 40        | 1.5          | 0.15         | 0        | 3.5×3×8            |
| BUS        | 45        | 2.0          | 0.10         | 0        | 3×3.2×10           |
| EMERGENCY  | 80        | 5.0          | 0.05         | 100      | 3×2.5×6            |
| TAXI       | 55        | 3.5          | 0.08         | 0        | 3×2×5              |
| MOTORCYCLE | 70        | 5.0          | 0.02         | 0        | 1.5×1.8×3          |

### 9.7 District Specifications

| #   | District         | Road Spacing | Building Chance | Default Road | Lanes | Speed | Style     | Unlock Requirement |
| --- | ---------------- | ------------ | --------------- | ------------ | ----- | ----- | --------- | ------------------ |
| 1   | Town Center      | 8            | 0.55            | COLLECTOR    | 2     | 40    | SHOP      | Initial            |
| 2   | Residential      | 8            | 0.40            | LOCAL        | 1     | 30    | HOUSE     | Tier 1             |
| 3   | Farmland         | 20           | 0.12            | PATH         | 1     | 50    | FARM      | Tier 1             |
| 4   | Commercial       | 8            | 0.60            | ARTERIAL     | 2     | 50    | OFFICE    | Tier 2             |
| 5   | Industrial       | 12           | 0.45            | COLLECTOR    | 2     | 40    | WAREHOUSE | Tier 2             |
| 6   | Highway Corridor | 20           | 0.08            | HIGHWAY      | 3     | 100   | SERVICE   | Tier 3             |
| 7   | Downtown         | 5            | 0.90            | ARTERIAL     | 3     | 40    | TOWER     | Tier 3             |

### 9.8 Weather System

| Weather      | Speed Factor | Seasons              |
| ------------ | ------------ | -------------------- |
| CLEAR        | 1.0          | All                  |
| RAIN         | 0.7          | Spring, Summer, Fall |
| HEAVY_RAIN   | 0.6          | Summer, Fall         |
| FOG          | 0.6          | All                  |
| SNOW         | 0.5          | Winter               |
| ICE          | 0.4          | Winter               |
| BLIZZARD     | 0.3          | Winter               |
| THUNDERSTORM | 0.5          | Summer               |

### 9.9 Traffic Event Types

| Event             | Tier | Duration | Description                              |
| ----------------- | ---- | -------- | ---------------------------------------- |
| RUSH_HOUR         | 1    | 240s     | Increased vehicle density, slower speeds |
| RAIN              | 1    | 150s     | Road speed reduced by weather            |
| ACCIDENT          | 1    | 90s      | Road segment blocked                     |
| HEAVY_FOG         | 2    | 150s     | Severe visibility reduction              |
| SNOW_ICE          | 2    | 240s     | Dangerous road conditions                |
| EMERGENCY_CASCADE | 2    | 120s     | Multiple emergency vehicles dispatched   |
| CONSTRUCTION_ZONE | 2    | 300s     | Road partially blocked for maintenance   |
| FLOODING          | 3    | 240s     | Roads submerged, impassable              |
| BLIZZARD          | 3    | 360s     | Extreme winter conditions                |
| STADIUM_EXODUS    | 3    | 240s     | Massive traffic spike from event venue   |

### 9.10 Rating System

| Grade | Threshold | RP Multiplier |
| ----- | --------- | ------------- |
| S     | ≥ 0.95    | 2.0×          |
| A     | ≥ 0.80    | 1.5×          |
| B     | ≥ 0.65    | 1.0×          |
| C     | ≥ 0.50    | 0.75×         |
| D     | ≥ 0.35    | 0.5×          |
| F     | < 0.35    | 0.25×         |

**Score Components** (weighted average):

- Wait Time Factor (40%) — Average vehicle wait time; 60s wait = score 0
- Emergency Response (25%) — Emergency vehicle response efficiency
- Accident Rate (15%) — Frequency and severity of traffic incidents
- Throughput (20%) — Vehicles delivered per time unit

---

## 10. Map Generation Details

### 10.1 Height Map

The map is a 64×64 cell grid. Each cell is 16×16 world units (total 1024×1024).

**Geographic Layout**:

- **North** (ny < 0.12): Tall mountains with forest-covered slopes
- **Northwest** (ny 0.15–0.40, nx < 0.35): Forested hills
- **Central**: Gentle rolling terrain (city area)
- **East** (nx > 0.62): Rolling ridges (farmland)
- **Southwest** (nx < 0.25, ny > 0.55): Canyon
- **South** (ny > 0.82): Ocean
- **River**: Carves from north to south at approximately 62% of map width

### 10.2 District Voronoi Seeds

7 district centers are placed geographically:

1. **Town Center** — Map center
2. **Residential** — Northwest quadrant
3. **Farmland** — Northeast area
4. **Commercial** — East-center
5. **Industrial** — Southwest
6. **Highway Corridor** — North (mountain border)
7. **Downtown** — South-center

Each EMPTY cell is assigned to its nearest seed by Euclidean distance.

### 10.3 Terrain Extensions

The terrain extends 40 cells beyond the map in all directions:

- **North**: Mountain wall with forest, gradually increasing elevation
- **South**: Ocean floor sloping down
- **East/West**: Mountain ranges wrapping to coastal cliffs

---

## 11. Currency & Economy

### 11.1 Income Sources

| Source           | Amount                      |
| ---------------- | --------------------------- |
| Vehicle delivery | 10 RP × rating multiplier   |
| Event completion | 100 RP × rating multiplier  |
| Passive earning  | ~2 RP/s × rating multiplier |
| Road demolition  | 20 RP (partial refund)      |

### 11.2 Expenditures

| Action                 | Cost (RP) |
| ---------------------- | --------- |
| Place PATH road        | 50        |
| Place LOCAL road       | 100       |
| Place COLLECTOR road   | 200       |
| Place ARTERIAL road    | 500       |
| Place HIGHWAY road     | 1000      |
| Install traffic signal | 300       |

---

## 12. Glossary

| Term                               | Definition                                                                                       |
| ---------------------------------- | ------------------------------------------------------------------------------------------------ |
| **RP (Road Points)**               | Primary currency for building infrastructure                                                     |
| **BT (Blueprint Tokens)**          | Secondary currency (reserved for future use)                                                     |
| **IDM (Intelligent Driver Model)** | Physics model for realistic vehicle following behavior                                           |
| **ECS (Entity-Component-System)**  | Architecture pattern separating data (components) from behavior (systems)                        |
| **FBM (Fractal Brownian Motion)**  | Noise algorithm for generating natural-looking terrain                                           |
| **Voronoi**                        | Spatial partitioning algorithm used for district assignment                                      |
| **A\***                            | Optimal graph pathfinding algorithm using heuristic                                              |
| **Tile**                           | Single cell in the map grid (16×16 world units)                                                  |
| **Tick**                           | Single simulation step (100ms / 10Hz)                                                            |
| **Snapshot**                       | Full game state sent on initial connection                                                       |
| **TickDelta**                      | Incremental state change sent every tick                                                         |
| **Signal Cycle**                   | 6-phase traffic light sequence (NS green → NS yellow → all red → EW green → EW yellow → all red) |
