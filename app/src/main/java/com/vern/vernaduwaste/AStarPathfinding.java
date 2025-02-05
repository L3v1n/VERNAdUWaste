package com.vern.vernaduwaste;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AStarPathfinding {

    private static final String TAG = "AStarPathfinding";
    public static final int GRID_SIZE = 32;
    // Directions: N, NE, E, SE, S, SW, W, NW
    public static final int[][] DIRECTIONS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    public boolean isInBounds(int[][] grid, int x, int y) {
        return x >= 0 && y >= 0 && y < grid.length && x < grid[0].length;
    }

    // Walkable if cell is 0 (walkable) or 2 (stairs)
    public boolean isWalkable(int[][] grid, int x, int y) {
        int cell = grid[y][x];
        return cell == 0 || cell == 2;
    }

    public double manhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    // Standard A* algorithm for a single floor.
    public AStarResult aStar(int[][] grid, int startX, int startY, int goalX, int goalY) {
        if (!isInBounds(grid, startX, startY) || !isInBounds(grid, goalX, goalY))
            return new AStarResult(null, 0);
        if (!isWalkable(grid, startX, startY) || !isWalkable(grid, goalX, goalY))
            return new AStarResult(null, 0);

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<String, Node> allNodes = new HashMap<>();
        int exploredCount = 0;
        double initialHeuristic = manhattanDistance(startX, startY, goalX, goalY);
        Node startNode = new Node(startX, startY, 0, 0, initialHeuristic);
        openSet.add(startNode);
        allNodes.put(getKey(startX, startY), startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            exploredCount++;
            if (current.x == goalX && current.y == goalY) {
                return new AStarResult(reconstructPath(current), exploredCount);
            }
            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];
                if (isInBounds(grid, nx, ny) && isWalkable(grid, nx, ny)) {
                    double tentativeG = current.gCost + 1; // uniform cost
                    String key = getKey(nx, ny);
                    Node neighbor = allNodes.get(key);
                    if (neighbor == null) {
                        double h = manhattanDistance(nx, ny, goalX, goalY);
                        neighbor = new Node(nx, ny, current.floor, tentativeG, h);
                        neighbor.parent = current;
                        openSet.add(neighbor);
                        allNodes.put(key, neighbor);
                    } else if (tentativeG < neighbor.gCost) {
                        neighbor.gCost = tentativeG;
                        neighbor.fCost = tentativeG + neighbor.hCost;
                        neighbor.parent = current;
                        openSet.remove(neighbor);
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return new AStarResult(null, exploredCount);
    }

    // Generates a unique key for (x,y).
    private String getKey(int x, int y) {
        return x + "," + y;
    }

    // Reconstructs path from goal to start.
    public List<Node> reconstructPath(Node node) {
        List<Node> path = new ArrayList<>();
        while (node != null) {
            path.add(node);
            node = node.parent;
        }
        Collections.reverse(path);
        Log.d(TAG, "Path reconstructed with " + path.size() + " nodes.");
        return path;
    }

    // Extracts stair positions from the grid. In our maps, cell value 2 indicates stairs.
    public List<int[]> getStairsFromGrid(int[][] grid) {
        List<int[]> stairs = new ArrayList<>();
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[0].length; x++) {
                if (grid[y][x] == 2) {
                    stairs.add(new int[]{x, y});
                }
            }
        }
        return stairs;
    }

    // Finds the nearest stair in the provided list from (x, y)
    public int[] findNearestStair(int floor, int[][] grid, int x, int y, List<int[]> stairsList) {
        if (stairsList == null || stairsList.isEmpty()) return null;
        double minDistance = Double.MAX_VALUE;
        int[] nearestStair = null;
        for (int[] stair : stairsList) {
            double d = manhattanDistance(x, y, stair[0], stair[1]);
            if (d < minDistance) {
                minDistance = d;
                nearestStair = stair;
            }
        }
        return nearestStair;
    }

    // Multi-floor pathfinding: if on the same floor, standard A*; otherwise, compute two segments.
    public PathResult findPathAcrossFloorsSimple(
            Map<Integer, int[][]> floorGrids,
            int startFloor, int startX, int startY,
            int goalFloor, int goalX, int goalY) {
        if (startFloor == goalFloor) {
            AStarResult result = aStar(floorGrids.get(startFloor), startX, startY, goalX, goalY);
            return new PathResult(result.path, result.exploredCount, null, null);
        }
        // For start floor, extract stairs from the grid.
        List<int[]> startStairs = getStairsFromGrid(floorGrids.get(startFloor));
        int[] startStair = findNearestStair(startFloor, floorGrids.get(startFloor), startX, startY, startStairs);
        if (startStair == null) {
            Log.d(TAG, "No stairs found on start floor " + startFloor);
            return new PathResult(null, 0, null, null);
        }
        // For goal floor, extract stairs and verify that the same stair exists.
        List<int[]> goalStairs = getStairsFromGrid(floorGrids.get(goalFloor));
        boolean stairExists = false;
        for (int[] stair : goalStairs) {
            if (stair[0] == startStair[0] && stair[1] == startStair[1]) {
                stairExists = true;
                break;
            }
        }
        if (!stairExists) {
            Log.d(TAG, "Stair at (" + startStair[0] + "," + startStair[1] + ") not found on goal floor " + goalFloor);
            return new PathResult(null, 0, null, null);
        }
        // Compute path on the start floor from start point to stair.
        AStarResult pathToStair = aStar(floorGrids.get(startFloor), startX, startY, startStair[0], startStair[1]);
        if (pathToStair.path == null) {
            Log.d(TAG, "No path to stair on start floor.");
            return new PathResult(null, 0, null, null);
        }
        // Compute path on the goal floor from stair to goal.
        AStarResult pathFromStair = aStar(floorGrids.get(goalFloor), startStair[0], startStair[1], goalX, goalY);
        if (pathFromStair.path == null) {
            Log.d(TAG, "No path from stair to goal on goal floor.");
            return new PathResult(null, 0, null, null);
        }
        // Combine paths, avoiding duplicate stair coordinate.
        List<Node> totalPath = new ArrayList<>(pathToStair.path);
        if (!totalPath.isEmpty())
            totalPath.remove(totalPath.size() - 1);
        totalPath.addAll(pathFromStair.path);
        int totalExplored = pathToStair.exploredCount + pathFromStair.exploredCount;
        Log.d(TAG, "Multi-floor path found with total nodes: " + totalPath.size());
        Node stairNode = new Node(startStair[0], startStair[1], startFloor, 0, 0); // dummy stair node
        return new PathResult(totalPath, totalExplored, stairNode, stairNode);
    }

    // Node class
    public static class Node implements Comparable<Node> {
        int x, y, floor;
        double gCost, hCost, fCost;
        Node parent;

        public Node(int x, int y, int floor, double gCost, double hCost) {
            this.x = x;
            this.y = y;
            this.floor = floor;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost, other.fCost);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Node other) {
                return this.x == other.x && this.y == other.y && this.floor == other.floor;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (x + "," + y + "," + floor).hashCode();
        }
    }

    // A* search result for a single floor.
    public static class AStarResult {
        public List<Node> path;
        public int exploredCount;

        public AStarResult(List<Node> path, int exploredCount) {
            this.path = path;
            this.exploredCount = exploredCount;
        }
    }

    // Multi-floor path result.
    public static class PathResult {
        public List<Node> path;
        public int exploredCount;
        public Node startStair;
        public Node endStair;

        public PathResult(List<Node> path, int exploredCount, Node startStair, Node endStair) {
            this.path = path;
            this.exploredCount = exploredCount;
            this.startStair = startStair;
            this.endStair = endStair;
        }
    }
}
