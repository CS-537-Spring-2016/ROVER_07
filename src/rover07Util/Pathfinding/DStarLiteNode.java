package rover07Util.Pathfinding;

class DStarLiteNode implements Comparable<DStarLiteNode> {
    public DStarLiteNode parent;
    public int g;
    public int rhs;
    public int k1;
    public int k2;

    private MapCell cell;

    public DStarLiteNode(MapCell cell) {
        this.cell = cell;
        g = rhs = Integer.MAX_VALUE;
    }

    public DStarLiteNode(DStarLiteNode that) {
        parent = that.parent;
        g = that.g;
        rhs = that.rhs;
        k1 = that.k1;
        k2 = that.k2;

        cell = that.cell;
    }

    public MapCell getCell() {
        return cell;
    }

    @Override
    public int compareTo(DStarLiteNode that) {
        return (k1 == that.k1)
                ? k2 - that.k2
                : k1 - that.k1;
    }
}
