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

    // Checks if (x, y) is within the bounds of the grid.
    public boolean isInBounds(int[][] grid, int x, int y) {
        return x >= 0 && y >= 0 && y < grid.length && x < grid[0].length;
    }

    // Checks if the cell at (x, y) is walkable. Walkable cells are 0 and stairs (2).
    public boolean isWalkable(int[][] grid, int x, int y) {
        int cell = grid[y][x];
        return cell == 0 || cell == 2;
    }

    // Computes the Manhattan distance between two points.
    public double manhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    // Standard A* algorithm for a single floor.
    public AStarResult aStar(int[][] grid, int startX, int startY, int goalX, int goalY) {
        if (!isInBounds(grid, startX, startY) || !isInBounds(grid, goalX, goalY)) {
            return new AStarResult(null, 0);
        }
        if (!isWalkable(grid, startX, startY) || !isWalkable(grid, goalX, goalY)) {
            return new AStarResult(null, 0);
        }
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
                    double tentativeG = current.gCost + 1; // Uniform cost
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

    // Helper to generate a unique key for a cell (ignoring floor for same-floor searches).
    private String getKey(int x, int y) {
        return x + "," + y;
    }

    // Reconstructs the path from the goal node back to the start.
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

    // Finds the nearest stair on a given floor from point (x, y) using a provided list of stairs.
    public int[] findNearestStair(int floor, int[][] grid, int x, int y, List<int[]> stairsList) {
        if (stairsList == null || stairsList.isEmpty()) return null;
        double minDistance = Double.MAX_VALUE;
        int[] nearest = null;
        for (int[] stair : stairsList) {
            double d = manhattanDistance(x, y, stair[0], stair[1]);
            if (d < minDistance) {
                minDistance = d;
                nearest = stair;
            }
        }
        return nearest;
    }

    /**
     * Multi-floor pathfinding (simple version) that mirrors the Python algorithm:
     * If on the same floor, performs a standard A* search.
     * If on different floors, finds the nearest stair on the start floor and checks that the same stair exists on the goal floor.
     * Then it computes two A* searches (start-to-stair and stair-to-goal) and combines the results.
     *
     * @param floorGrids Map of floor number to its grid.
     * @param stairsMap  Map of floor number to a list of stair coordinates.
     * @param startFloor Starting floor number.
     * @param startX     Starting x coordinate.
     * @param startY     Starting y coordinate.
     * @param goalFloor  Goal floor number.
     * @param goalX      Goal x coordinate.
     * @param goalY      Goal y coordinate.
     * @return A PathResult containing the combined path, total explored node count, and the stair node used, or null if no path is found.
     */
    public PathResult findPathAcrossFloorsSimple(
            Map<Integer, int[][]> floorGrids,
            Map<Integer, List<int[]>> stairsMap,
            int startFloor, int startX, int startY,
            int goalFloor, int goalX, int goalY) {

        if (startFloor == goalFloor) {
            AStarResult result = aStar(floorGrids.get(startFloor), startX, startY, goalX, goalY);
            return new PathResult(result.path, result.exploredCount, null, null);
        }

        // Find the nearest stair on the start floor.
        List<int[]> startStairs = stairsMap.get(startFloor);
        int[] startStair = findNearestStair(startFloor, floorGrids.get(startFloor), startX, startY, startStairs);
        if (startStair == null) {
            Log.d(TAG, "No stair found on start floor " + startFloor);
            return new PathResult(null, 0, null, null);
        }

        // Verify that this stair exists on the goal floor.
        List<int[]> goalStairs = stairsMap.get(goalFloor);
        boolean stairExists = false;
        for (int[] stair : goalStairs) {
            if (stair[0] == startStair[0] && stair[1] == startStair[1]) {
                stairExists = true;
                break;
            }
        }
        if (!stairExists) {
            Log.d(TAG, "Stair at (" + startStair[0] + "," + startStair[1] + ") does not exist on goal floor " + goalFloor);
            return new PathResult(null, 0, null, null);
        }

        // Compute the path on the start floor from the start point to the stair.
        AStarResult pathToStair = aStar(floorGrids.get(startFloor), startX, startY, startStair[0], startStair[1]);
        if (pathToStair.path == null) {
            Log.d(TAG, "No path to stair on start floor.");
            return new PathResult(null, 0, null, null);
        }

        // Compute the path on the goal floor from the stair to the goal.
        AStarResult pathFromStair = aStar(floorGrids.get(goalFloor), startStair[0], startStair[1], goalX, goalY);
        if (pathFromStair.path == null) {
            Log.d(TAG, "No path from stair to goal on goal floor.");
            return new PathResult(null, 0, null, null);
        }

        // Combine the two paths (avoiding a duplicate stair node).
        List<Node> totalPath = new ArrayList<>(pathToStair.path);
        if (!totalPath.isEmpty()) {
            totalPath.remove(totalPath.size() - 1);
        }
        totalPath.addAll(pathFromStair.path);
        int totalExplored = pathToStair.exploredCount + pathFromStair.exploredCount;
        Log.d(TAG, "Multi-floor path found with total nodes: " + totalPath.size());
        Node stairNode = new Node(startStair[0], startStair[1], startFloor, 0, 0); // Dummy stair node
        return new PathResult(totalPath, totalExplored, stairNode, stairNode);
    }

    // Node class representing a point on the grid (including its floor)
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

    // Class to hold the result of a same-floor A* search.
    public static class AStarResult {
        public List<Node> path;
        public int exploredCount;

        public AStarResult(List<Node> path, int exploredCount) {
            this.path = path;
            this.exploredCount = exploredCount;
        }
    }

    // Class to hold the multi-floor path result (mirroring the Python function).
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
