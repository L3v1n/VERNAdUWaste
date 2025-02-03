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

    // Directions: N, NE, E, SE, S, SW, W, NW
    private static final int[][] DIRECTIONS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    /**
     * Finds the shortest path from start to goal across multiple floors.
     *
     * @param floorGrids  Map of floor number to its grid representation.
     * @param startFloor  Starting floor number.
     * @param startX      Starting X coordinate.
     * @param startY      Starting Y coordinate.
     * @param goalFloor   Goal floor number.
     * @param goalX       Goal X coordinate.
     * @param goalY       Goal Y coordinate.
     * @param stairsMap   Map of floor number to list of stair coordinates.
     * @return List of Nodes representing the path, or null if no path found.
     */
    public List<Node> findPathAcrossFloors(
            Map<Integer, int[][]> floorGrids,
            int startFloor, int startX, int startY,
            int goalFloor, int goalX, int goalY,
            Map<Integer, List<int[]>> stairsMap) {

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<String, Node> allNodes = new HashMap<>();

        double initialHeuristic = getHeuristic(startX, startY, startFloor, goalX, goalY, goalFloor);
        Node startNode = new Node(startX, startY, startFloor, 0, initialHeuristic);
        openSet.add(startNode);
        allNodes.put(getKey(startX, startY, startFloor), startNode);

        Log.d(TAG, "Starting multi-floor pathfinding from (" + startX + ", " + startY + ", Floor " + startFloor + ") to (" + goalX + ", " + goalY + ", Floor " + goalFloor + ")");

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current == null) continue;

            if (current.x == goalX && current.y == goalY && current.floor == goalFloor) {
                Log.d(TAG, "Path found across floors.");
                return reconstructPath(current);
            }

            // Explore neighbors on the same floor
            for (int[] direction : DIRECTIONS) {
                int neighborX = current.x + direction[0];
                int neighborY = current.y + direction[1];
                int neighborFloor = current.floor;

                if (isInBounds(floorGrids.get(neighborFloor), neighborX, neighborY) && isWalkable(floorGrids.get(neighborFloor), neighborX, neighborY)) {
                    double tentativeGCost = current.gCost + getMovementCost(floorGrids.get(neighborFloor)[neighborY][neighborX]);
                    String neighborKey = getKey(neighborX, neighborY, neighborFloor);
                    Node neighborNode = allNodes.get(neighborKey);

                    if (neighborNode == null) {
                        double hCost = getHeuristic(neighborX, neighborY, neighborFloor, goalX, goalY, goalFloor);
                        neighborNode = new Node(neighborX, neighborY, neighborFloor, tentativeGCost, hCost);
                        neighborNode.parent = current;
                        openSet.add(neighborNode);
                        allNodes.put(neighborKey, neighborNode);
                        Log.d(TAG, "Adding node (" + neighborX + ", " + neighborY + ", Floor " + neighborFloor + ") with fCost: " + neighborNode.fCost);
                    } else if (tentativeGCost < neighborNode.gCost) {
                        neighborNode.gCost = tentativeGCost;
                        neighborNode.fCost = tentativeGCost + neighborNode.hCost;
                        neighborNode.parent = current;
                        openSet.remove(neighborNode);
                        openSet.add(neighborNode);
                        Log.d(TAG, "Updating node (" + neighborX + ", " + neighborY + ", Floor " + neighborFloor + ") with new fCost: " + neighborNode.fCost);
                    }
                }
            }

            // If current node is a stair, consider moving to connected floors
            if (stairsMap.containsKey(current.floor)) {
                for (int[] stair : stairsMap.get(current.floor)) {
                    if (current.x == stair[0] && current.y == stair[1]) {
                        // Assume stairs connect to the next floor (e.g., floor+1 or floor-1)
                        List<Integer> connectedFloors = getConnectedFloors(current.floor, stairsMap);
                        for (int connectedFloor : connectedFloors) {
                            String stairKey = getKey(current.x, current.y, connectedFloor);
                            Node stairNode = allNodes.get(stairKey);
                            double tentativeGCost = current.gCost + 1.5; // Slightly higher cost for stair transition

                            if (stairNode == null) {
                                double hCost = getHeuristic(current.x, current.y, connectedFloor, goalX, goalY, goalFloor);
                                stairNode = new Node(current.x, current.y, connectedFloor, tentativeGCost, hCost);
                                stairNode.parent = current;
                                openSet.add(stairNode);
                                allNodes.put(stairKey, stairNode);
                                Log.d(TAG, "Adding stair node (" + current.x + ", " + current.y + ", Floor " + connectedFloor + ") with fCost: " + stairNode.fCost);
                            } else if (tentativeGCost < stairNode.gCost) {
                                stairNode.gCost = tentativeGCost;
                                stairNode.fCost = tentativeGCost + stairNode.hCost;
                                stairNode.parent = current;
                                openSet.remove(stairNode);
                                openSet.add(stairNode);
                                Log.d(TAG, "Updating stair node (" + current.x + ", " + current.y + ", Floor " + connectedFloor + ") with new fCost: " + stairNode.fCost);
                            }
                        }
                        break; // Only handle one stair per node
                    }
                }
            }
        }

        Log.w(TAG, "No path found across floors.");
        return null; // No path found
    }

    private String getKey(int x, int y, int floor) {
        return x + "," + y + "," + floor;
    }

    private double getHeuristic(int x1, int y1, int floor1, int x2, int y2, int floor2) {
        // Manhattan distance including floor difference
        return Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(floor1 - floor2) * 10; // Assign higher cost for floor differences
    }

    private boolean isInBounds(int[][] grid, int x, int y) {
        return y >= 0 && x >= 0 && y < grid.length && x < grid[0].length;
    }

    private boolean isWalkable(int[][] grid, int x, int y) {
        return grid[y][x] == 0 || grid[y][x] == 2; // 0: walkable, 2: stairs
    }

    private double getMovementCost(int cellType) {
        switch (cellType) {
            case 0:
                return 1.0; // Normal walkable
            case 2:
                return 1.5; // Stair path
            default:
                return 1.0;
        }
    }

    private List<Node> reconstructPath(Node node) {
        List<Node> path = new ArrayList<>();
        while (node != null) {
            path.add(node);
            node = node.parent;
        }
        Collections.reverse(path);
        Log.d(TAG, "Path reconstructed with " + path.size() + " nodes.");
        return path;
    }

    /**
     * Determines connected floors for a given floor based on stairsMap.
     * For simplicity, assume stairs connect adjacent floors only.
     *
     * @param floor     Current floor number.
     * @param stairsMap Map of floor to stair positions.
     * @return List of connected floor numbers.
     */
    private List<Integer> getConnectedFloors(int floor, Map<Integer, List<int[]>> stairsMap) {
        List<Integer> connectedFloors = new ArrayList<>();
        if (stairsMap.containsKey(floor - 1)) {
            connectedFloors.add(floor - 1);
        }
        if (stairsMap.containsKey(floor + 1)) {
            connectedFloors.add(floor + 1);
        }
        return connectedFloors;
    }
}
