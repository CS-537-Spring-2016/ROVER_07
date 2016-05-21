package rover07Util.Pathfinding;

public interface MapCell {
    int getX();
    int getY();
    int getCost();
    boolean isBlocked();
}
