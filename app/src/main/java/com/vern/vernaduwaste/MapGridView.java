package com.vern.vernaduwaste;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MapGridView extends View {
    private static final String TAG = "MapGridView";
    private int[][] mapGrid;
    private Paint walkablePaint, inaccessiblePaint, stairsPaint, roomsPaint;
    private Paint markerPaint, deviceMarkerPaint, selectedMarkerPaint;
    private Paint pathActivePaint;
    private final int cellSize = 32;
    private float scaleFactor = 1.0f;
    private float minScaleFactor = 1.0f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private boolean isInitialSetup = true;

    private final List<int[]> markers = new ArrayList<>();
    private int[] selectedMarker = null;
    private int[] deviceMarker = null;
    private float deviceOrientation = 0f;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private MarkerClickListener markerClickListener;
    private List<int[]> activePath;

    private int deviceFloor = -1;
    private int currentFloor = -1;

    public interface MarkerClickListener {
        void onMarkerClick(int x, int y);
    }

    public void setMarkerClickListener(MarkerClickListener listener) {
        this.markerClickListener = listener;
    }

    public MapGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    private void initPaints() {
        walkablePaint = createPaint("#a9b8c8");
        inaccessiblePaint = createPaint("#c8c8c8");
        stairsPaint = createPaint("#e8b931");
        roomsPaint = createPaint("#e7e8ec");

        markerPaint = new Paint();
        markerPaint.setColor(Color.parseColor("#4CAF50"));
        markerPaint.setStyle(Paint.Style.FILL);

        selectedMarkerPaint = new Paint();
        selectedMarkerPaint.setColor(Color.parseColor("#388E3C"));
        selectedMarkerPaint.setStyle(Paint.Style.FILL);

        deviceMarkerPaint = new Paint();
        deviceMarkerPaint.setColor(Color.parseColor("#329eda"));
        deviceMarkerPaint.setStyle(Paint.Style.FILL);

        pathActivePaint = new Paint();
        pathActivePaint.setColor(Color.parseColor("#FF0000"));
        pathActivePaint.setStyle(Paint.Style.STROKE);
        pathActivePaint.setStrokeWidth(5);
        pathActivePaint.setStrokeCap(Paint.Cap.ROUND);
        pathActivePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private Paint createPaint(String color) {
        Paint paint = new Paint();
        paint.setColor(Color.parseColor(color));
        return paint;
    }

    public void setMapGrid(int[][] mapGrid, int deviceFloor) {
        this.mapGrid = mapGrid;
        this.deviceFloor = deviceFloor;
        isInitialSetup = true;
        invalidate();
        Log.d(TAG, "Map grid set with deviceFloor: " + deviceFloor);
    }

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
        invalidate();
        Log.d(TAG, "Current floor set to: " + currentFloor);
    }

    public void clearMarkers() {
        markers.clear();
        invalidate();
        Log.d(TAG, "Markers cleared from the map.");
    }

    public void addMarkerPosition(int x, int y) {
        if (mapGrid != null && x >= 0 && y >= 0 && x < mapGrid[0].length && y < mapGrid.length) {
            markers.add(new int[]{x, y});
            invalidate();
            Log.d(TAG, "Marker added at (" + x + ", " + y + ")");
        } else {
            Log.w(TAG, "Attempted to add marker out of bounds: (" + x + ", " + y + ")");
        }
    }

    public void addDeviceMarkerPosition(int x, int y) {
        if (mapGrid != null && x >= 0 && y >= 0 && x < mapGrid[0].length && y < mapGrid.length) {
            if (currentFloor == deviceFloor) {
                deviceMarker = new int[]{x, y};
                invalidate();
                Log.d(TAG, "Device marker added at (" + x + ", " + y + ") on floor " + currentFloor);
            } else {
                Log.d(TAG, "Device marker not added as currentFloor (" + currentFloor + ") != deviceFloor (" + deviceFloor + ")");
            }
        } else {
            Log.w(TAG, "Attempted to add device marker out of bounds: (" + x + ", " + y + ")");
        }
    }

    public void removeDeviceMarker() {
        if (deviceMarker != null) {
            deviceMarker = null;
            invalidate();
            Log.d(TAG, "Device marker removed from the map.");
        }
    }

    public void deselectMarker() {
        if (selectedMarker != null) {
            selectedMarker = null;
            invalidate();
            Log.d(TAG, "Marker deselected.");
        }
    }

    public void setDeviceOrientation(float orientation) {
        this.deviceOrientation = orientation;
        invalidate();
        Log.d(TAG, "Device orientation set to " + orientation + " degrees.");
    }

    public void setPath(List<int[]> path) {
        this.activePath = path;
        invalidate();
        Log.d(TAG, "Active navigation path set.");
    }

    public void clearPaths() {
        activePath = null;
        invalidate();
        Log.d(TAG, "Paths cleared.");
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mapGrid == null) return;

        if (isInitialSetup) {
            setupInitialScaleAndPosition();
            isInitialSetup = false;
        }

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scaleFactor, scaleFactor);

        for (int y = 0; y < mapGrid.length; y++) {
            for (int x = 0; x < mapGrid[y].length; x++) {
                Paint paint = getPaintForCell(mapGrid[y][x]);
                float left = x * cellSize;
                float top = (mapGrid.length - y - 1) * cellSize;
                canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
            }
        }

        if (activePath != null && activePath.size() > 1) {
            Path drawPath = new Path();
            boolean first = true;
            for (int[] point : activePath) {
                float x = point[0] * cellSize + cellSize / 2f;
                float y = (mapGrid.length - point[1] - 1) * cellSize + cellSize / 2f;
                if (first) {
                    drawPath.moveTo(x, y);
                    first = false;
                } else {
                    drawPath.lineTo(x, y);
                }
            }
            canvas.drawPath(drawPath, pathActivePaint);
            Log.d(TAG, "Active navigation path drawn.");
        }

        for (int[] marker : markers) {
            boolean isSelected = selectedMarker != null && marker[0] == selectedMarker[0] && marker[1] == selectedMarker[1];
            drawMarker(canvas, marker[0], marker[1], isSelected);
        }

        if (deviceMarker != null && currentFloor == deviceFloor) {
            drawDeviceMarker(canvas, deviceMarker[0], deviceMarker[1]);
        }

        canvas.restore();
    }

    private Paint getPaintForCell(int cellType) {
        switch (cellType) {
            case 0:
                return walkablePaint;
            case 1:
                return inaccessiblePaint;
            case 2:
                return stairsPaint;
            case 3:
                return roomsPaint;
            default:
                return inaccessiblePaint;
        }
    }

    private void drawMarker(Canvas canvas, int x, int y, boolean isSelected) {
        if (x >= 0 && x < mapGrid[0].length && y >= 0 && y < mapGrid.length) {
            float centerX = x * cellSize + cellSize / 2f;
            float centerY = (mapGrid.length - y - 1) * cellSize + cellSize / 2f;
            float radius;
            Paint paint;
            if (mapGrid[y][x] == 2) {
                radius = cellSize / 3f;
                paint = new Paint();
                paint.setColor(Color.parseColor("#FF5722"));
                paint.setStyle(Paint.Style.FILL);
            } else {
                radius = isSelected ? cellSize / 2f : cellSize / 4f;
                paint = isSelected ? selectedMarkerPaint : markerPaint;
            }
            canvas.drawCircle(centerX, centerY, radius, paint);
            Log.d(TAG, "Marker drawn at (" + x + ", " + y + "), Selected: " + isSelected + ", CellType: " + mapGrid[y][x]);
        }
    }

    private void drawDeviceMarker(Canvas canvas, int x, int y) {
        if (x >= 0 && x < mapGrid[0].length && y >= 0 && y < mapGrid.length) {
            float centerX = x * cellSize + cellSize / 2f;
            float centerY = (mapGrid.length - y - 1) * cellSize + cellSize / 2f;
            canvas.save();
            canvas.translate(centerX, centerY);
            canvas.rotate(-deviceOrientation);
            Path arrowPath = new Path();
            arrowPath.moveTo(0, -cellSize / 2f);
            arrowPath.lineTo(cellSize / 2f, cellSize / 2f);
            arrowPath.lineTo(-cellSize / 2f, cellSize / 2f);
            arrowPath.close();
            canvas.drawPath(arrowPath, deviceMarkerPaint);
            canvas.restore();
            Log.d(TAG, "Device marker drawn at (" + x + ", " + y + ") with orientation " + deviceOrientation + " degrees.");
        }
    }

    private void setupInitialScaleAndPosition() {
        if (mapGrid == null || mapGrid.length == 0) return;
        int gridWidth = mapGrid[0].length * cellSize;
        int gridHeight = mapGrid.length * cellSize;
        float scaleX = (float) getWidth() / gridWidth;
        float scaleY = (float) getHeight() / gridHeight;
        scaleFactor = Math.max(scaleX, scaleY);
        minScaleFactor = scaleFactor;
        offsetX = (getWidth() - gridWidth * scaleFactor) / 2;
        offsetY = (getHeight() - gridHeight * scaleFactor) / 2;
        Log.d(TAG, "Initial scale set. ScaleFactor: " + scaleFactor + ", OffsetX: " + offsetX + ", OffsetY: " + offsetY);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return scaleHandled || gestureHandled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float previousScaleFactor = scaleFactor;
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(minScaleFactor, Math.min(scaleFactor, 3.0f));
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            float scaleChange = scaleFactor / previousScaleFactor;
            offsetX = (offsetX - focusX) * scaleChange + focusX;
            offsetY = (offsetY - focusY) * scaleChange + focusY;
            invalidate();
            Log.d(TAG, "Scaling: New scaleFactor: " + scaleFactor);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            float x = (event.getX() - offsetX) / scaleFactor;
            float y = (event.getY() - offsetY) / scaleFactor;
            int cellX = (int) (x / cellSize);
            int cellY = mapGrid.length - 1 - (int) (y / cellSize);
            for (int[] marker : markers) {
                if (marker[0] == cellX && marker[1] == cellY) {
                    selectedMarker = marker;
                    invalidate();
                    if (markerClickListener != null) {
                        markerClickListener.onMarkerClick(cellX, cellY);
                    }
                    Log.d(TAG, "Marker at (" + cellX + ", " + cellY + ") clicked.");
                    return true;
                }
            }
            if (selectedMarker != null) {
                selectedMarker = null;
                invalidate();
                if (markerClickListener != null) {
                    markerClickListener.onMarkerClick(-1, -1);
                }
                Log.d(TAG, "No marker clicked. Deselected current marker.");
            }
            return false;
        }

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, @Nullable MotionEvent e2, float distanceX, float distanceY) {
            offsetX = clamp(offsetX - distanceX, getMinOffsetX(), getMaxOffsetX());
            offsetY = clamp(offsetY - distanceY, getMinOffsetY(), getMaxOffsetY());
            invalidate();
            Log.d(TAG, "Scrolling: New OffsetX: " + offsetX + ", OffsetY: " + offsetY);
            return true;
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float getMinOffsetX() {
        float scaledWidth = mapGrid[0].length * cellSize * scaleFactor;
        if (scaledWidth <= getWidth()) {
            return (getWidth() - scaledWidth) / 2;
        }
        return getWidth() - scaledWidth;
    }

    private float getMaxOffsetX() {
        float scaledWidth = mapGrid[0].length * cellSize * scaleFactor;
        if (scaledWidth <= getWidth()) {
            return (getWidth() - scaledWidth) / 2;
        }
        return 0;
    }

    private float getMinOffsetY() {
        float scaledHeight = mapGrid.length * cellSize * scaleFactor;
        if (scaledHeight <= getHeight()) {
            return (getHeight() - scaledHeight) / 2;
        }
        return getHeight() - scaledHeight;
    }

    private float getMaxOffsetY() {
        float scaledHeight = mapGrid.length * cellSize * scaleFactor;
        if (scaledHeight <= getHeight()) {
            return (getHeight() - scaledHeight) / 2;
        }
        return 0;
    }
}
