import { GameApp } from "./game/GameApp";

const app = new GameApp();
app.init().catch((err) => {
  console.error("Failed to initialize game:", err);
  const loading = document.getElementById("loading");
  if (loading) loading.textContent = "Failed to load. Check console.";
});
