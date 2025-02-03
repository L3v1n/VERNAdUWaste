package com.vern.vernaduwaste;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseHelper {

    private static final String TAG = "FirebaseHelper";
    private final DatabaseReference databaseReference;

    public FirebaseHelper() {
        databaseReference = FirebaseDatabase.getInstance().getReference("wasteBins");
        Log.d(TAG, "Firebase database reference initialized.");
    }

    public interface WifiPositionListener {
        void onPositionsReceived(WifiPosition board1, WifiPosition board2);
    }

    public void listenForWifiBoardPositions(final WifiPositionListener listener) {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                WifiPosition board1 = snapshot.child("VERNWasteBoard1").getValue(WifiPosition.class);
                WifiPosition board2 = snapshot.child("VERNWasteBoard2").getValue(WifiPosition.class);

                if (board1 != null && board2 != null) {
                    Log.d(TAG, "Fetched positions: Board1(" + board1.x + ", " + board1.y + "), Board2(" + board2.x + ", " + board2.y + ")");
                    listener.onPositionsReceived(board1, board2);
                } else {
                    Log.w(TAG, "One or both Wi-Fi board positions are null");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database retrieval cancelled: " + error.getMessage());
            }
        });
    }

    public static class WifiPosition {
        public String ip;
        public String mac;
        public int floor;
        public int rssi;
        public int x;
        public int y;

        public WifiPosition() {}
    }
}
