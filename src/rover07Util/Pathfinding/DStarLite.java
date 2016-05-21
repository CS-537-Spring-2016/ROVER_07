package rover07Util.Pathfinding;

import java.util.*;

/**
 * @author Michael Fong (G7) [@meishuu]
 */

// largely based on "D* Lite Demonstration" by Luis Henrique Oliveira Rios
//   <http://www.luisrios.eti.br/public/en_us/research/d_star_lite_demo/>

// annotated with code references to "D* Lite" by S. Koenig and M. Likhachev
//    <http://idm-lab.org/bib/abstracts/papers/aaai02b.pdf>

public class DStarLite {
    private final int MAX_STEPS = 100000;

    private Map map;
    private Set<MapCell> changedCells;

    private PriorityQueue<DStarLiteNode> openList;
    private DStarLiteNode[][] graph;

    private int k_m;
    private DStarLiteNode s_start;
    private DStarLiteNode s_goal;
    private DStarLiteNode s_last;

    private List<MapCell> path;

    public DStarLite(Map map, MapCell start, MapCell goal) {
        this.map = map;
        changedCells = new HashSet<>();

        // initialize graph
        final int w = map.getWidth();
        final int h = map.getHeight();
        graph = new DStarLiteNode[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                graph[y][x] = new DStarLiteNode(map.getCell(x, y));
            }
        }

        // set start and goal nodes
        s_start = getNode(start);
        s_goal = getNode(goal);

        // procedure Main()
        // {21'} s_last = s_start;
        s_last = s_start;

        // {22'} Initialize()
        initialize();
    }

    // procedure CalculateKey(s)
    private void calculateKey(DStarLiteNode node) {
        // {01'} return [min(g(s), rhs(s)) + h(s_start, s) + k_m ; min(g(s), rhs(s))];
        int min = Math.min(node.g, node.rhs);
        int h = heuristic(node.getCell(), s_start.getCell());
        node.k1 = (min == Integer.MAX_VALUE) ? Integer.MAX_VALUE : min + h + k_m;
        node.k2 = min;
    }

    // procedure Initialize()
    private void initialize() {
        // {02'} U = ∅;
        openList = new PriorityQueue<>();

        // {03'} k_m = 0;
        k_m = 0;

        // {04"} for all s ∈ S rhs(s) = g(s) = ∞;
        // <implied>

        // {05"} rhs(s_goal) = 0;
        s_goal.rhs = 0;

        // {06'} U.Insert(s_goal, CalculateKey(s_goal));
        calculateKey(s_goal);
        openList.add(s_goal);
    }

    // procedure UpdateVertex(u)
    private void updateNode(DStarLiteNode node) {
        // {07'} if (u ≠ s_goal) rhs(u) = min_{s' ∈ Succ(u)}(c(u, s') + g(s'));
        if (node != s_goal) {
            node.rhs = Integer.MAX_VALUE;
            node.parent = null;

            for (DStarLiteNode neighbor : getNeighbors(node)) {
                if (neighbor.getCell().isBlocked()) continue;

                int cost = cost(node);
                int aux = (neighbor.g == Integer.MAX_VALUE) ? Integer.MAX_VALUE : neighbor.g + cost;
                if (aux < node.rhs) {
                    node.rhs = aux;
                    node.parent = neighbor;
                }
            }
        }

        // {08'} if (u ∈ U) U.Remove(u);
        if (openList.contains(node)) {
            openList.remove(node);
        }

        // {09'} if (g(u) ≠ rhs(u)) U.Insert(u, calculateKey(u));
        if (node.g != node.rhs) {
            calculateKey(node);
            openList.add(node);
        }
    }

    // procedure ComputeShortestPath()
    private boolean computeShortestPath() {
        try {
            int step = 0;

            while (true) {
                step++;
                if (step >= MAX_STEPS) return false;

                if (openList.size() == 0) break;

                // {10'} while (U.TopKey() < CalculateKey(s_start) OR rhs(s_start) ≠ g(s_start))
                DStarLiteNode node = openList.peek();
                DStarLiteNode aux = new DStarLiteNode(s_start);
                calculateKey(aux);
                if (!(node.compareTo(aux) < 0 || s_start.rhs != s_start.g)) break;

                // {11'} k_old = U.TopKey();
                // {12'} u = U.Pop();
                node = openList.poll();
                aux = new DStarLiteNode(node);

                // {13'} if (k_old < CalculateKey(u))
                calculateKey(node);
                if (aux.compareTo(node) < 0) {
                    // {14'} U.Insert(u, CalculateKey(u))
                    openList.add(node);
                }

                // {15'} else if (g(u) > rhs(u))
                else if (node.g > node.rhs) {
                    // {16'} g(u) = rhs(u);
                    node.g = node.rhs;

                    // {17'} for all s ∈ Pred(u) UpdateVertex(s)
                    for (DStarLiteNode neighbor : getNeighbors(node)) {
                        if (neighbor.getCell().isBlocked()) continue;
                        updateNode(neighbor);
                    }
                }

                // {18'} else
                else {
                    // {19'} g(u) = ∞;
                    node.g = Integer.MAX_VALUE;

                    // {20'} for all s ∈ Pred(u) ∪ {u} UpdateVertex(s)
                    for (DStarLiteNode neighbor : getNeighbors(node)) {
                        if (neighbor.getCell().isBlocked()) continue;
                        updateNode(neighbor);
                    }

                    updateNode(node);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public void solve() {
        // {30'} k_m = k_m + h(s_last, s_start);
        k_m += heuristic(s_last.getCell(), s_start.getCell());

        // {32'} for all directed edges (u, v) with changed edge costs
        for (MapCell cell : changedCells) {
            DStarLiteNode node = getNode(cell);

            // {33'} Update the edge cost c(u, v);
            if (cell.isBlocked()) {
                node.g = node.rhs = Integer.MAX_VALUE;

                if (openList.contains(node)) {
                    openList.remove(node);
                }
            } else {
                // {34'} UpdateVertex(u);
                updateNode(node);
            }

            for (DStarLiteNode neighbor : getNeighbors(node)) {
                if (neighbor.getCell().isBlocked()) continue;
                updateNode(neighbor);
            }
        }
        changedCells.clear();

        // {35'} ComputeShortestPath();
        boolean success = computeShortestPath();

        // generate path
        if (success) generatePath();
    }

    private void generatePath() {
        path = null;

        // {25'} /* if (g(s_start) = ∞) then there is no known path */
        if (s_start.g == Integer.MAX_VALUE) return;

        DStarLiteNode node = s_start;
        DStarLiteNode node_parent = node.parent;

        path = new ArrayList<>();
        while (node_parent != null) {
            path.add(node_parent.getCell());
            node = node_parent;
            node_parent = node.parent;
        }
    }

    // helpers
    private int cost(DStarLiteNode node) {
        return node.getCell().getCost();
    }

    private int heuristic(MapCell s1, MapCell s2) {
        int dx = Math.abs(s2.getX() - s1.getX());
        int dy = Math.abs(s2.getY() - s1.getY());
        return dx + dy;
    }

    private List<DStarLiteNode> getNeighbors(DStarLiteNode node) {
        List<DStarLiteNode> neighbors = new ArrayList<>();
        for (MapCell cell : map.getNeighbors(node.getCell())) {
            neighbors.add(getNode(cell));
        }
        return neighbors;
    }

    private DStarLiteNode getNode(MapCell cell) {
        return graph[cell.getY()][cell.getX()];
    }

    // public methods
    public void updateStart(MapCell new_start) {
        // {31'} s_last = s_start;
        s_last = s_start;

        s_start = getNode(new_start);
    }

    public void markChangedCell(MapCell cell) {
        changedCells.add(cell);
    }

    public void markChangedCell(Set<MapCell> cells) {
        changedCells.addAll(cells);
    }

    public List<MapCell> getPath() {
        return new ArrayList<>(path);
    }
}
