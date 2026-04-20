package com.trafficgame.shared.protocol;

/**
 * Command sent from client to server representing a player action.
 * Server validates before applying.
 */
public final class PlayerCommand implements com.trafficgame.engine.protocol.Command {

    private String type;
    private String action;
    private String targetId;
    private double x;
    private double y;
    private double x2;
    private double y2;
    private String data;

    public PlayerCommand() {}

    public PlayerCommand(String type, String action) {
        this.type = type;
        this.action = action;
    }

    @Override
    public String type() { return type; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getX2() { return x2; }
    public void setX2(double x2) { this.x2 = x2; }

    public double getY2() { return y2; }
    public void setY2(double y2) { this.y2 = y2; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
