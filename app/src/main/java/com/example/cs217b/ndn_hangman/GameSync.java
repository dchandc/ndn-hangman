package com.example.cs217b.ndn_hangman;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import net.named_data.jndn.*;
import net.named_data.jndn.sync.*;

import java.util.List;

public class GameSync extends Service {
    public ChronoSync2013 sync;
    public ReceivedSyncState rec;

    public class ReceivedSyncState implements ChronoSync2013.OnReceivedSyncState {
        @Override
        public void onReceivedSyncState(List syncStates, boolean isRecovery) {

        }
    }

    public GameSync() {
        rec = new ReceivedSyncState();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
