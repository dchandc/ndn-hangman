package com.example.cs217b.ndn_hangman;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import net.named_data.jndn.*;
import net.named_data.jndn.sync.*;
import net.named_data.jndn.security.*;
import net.named_data.jndn.OnRegisterFailed;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import java.util.Random;

class SyncHandler implements ChronoSync2013.OnInitialized,
        ChronoSync2013.OnReceivedSyncState, OnData, OnInterestCallback {
    public String playerName_;
    public String userName_;
    public String gameName_;
    public Face face_;
    public KeyChain keyChain_;
    public Name certificateName_;
    public Name gamePrefix_;
    private OnTimeout heartbeat_;
    public ArrayList messageCache_;
    public ChronoSync2013 sync_;
    private final double syncLifetime_ = 5000.0; // milliseconds
    private final int maxMessageCacheLength_ = 100;

    public SyncHandler(String playerName, String gameName, Name hubPrefix, Face face, KeyChain keyChain, Name certificateName) {
        playerName_ = playerName;
        gameName_ = gameName;
        face_ = face;
        keyChain_ = keyChain;
        certificateName_ = certificateName;
        heartbeat_ = this.new Heartbeat();

        // This should only be called once, so get the random string here.
        gamePrefix_ = new Name(hubPrefix).append(gameName_).append(getRandomString());
        int session = (int)Math.round(getNowMilliseconds() / 1000.0);
        userName_ = playerName_ + session;
        try {
            sync_ = new ChronoSync2013
                    (this, this, gamePrefix_,
                            new Name("/ndn/broadcast/hangman").append(gameName_), session,
                            face, keyChain, certificateName, syncLifetime_, RegisterFailed.onRegisterFailed_);
        } catch (Exception ex) {
            Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        try {
            face.registerPrefix(gamePrefix_, this, RegisterFailed.onRegisterFailed_);
        } catch (Exception ex) {
            Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void onData(Interest interest, Data data) {

    }

    @Override
    public void onInitialized() {

    }

    @Override
    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

    }

    @Override
    public void onReceivedSyncState(List syncStates, boolean isRecovery) {

    }

    private static class RegisterFailed implements OnRegisterFailed {
        public final void
        onRegisterFailed(Name prefix)
        {
            System.out.println("Register failed for prefix " + prefix.toUri());
        }

        public final static OnRegisterFailed onRegisterFailed_ = new RegisterFailed();
    }

    private static String
    getRandomString()
    {
        String seed = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM0123456789";
        String result = "";
        Random random = new Random();
        for (int i = 0; i < 10; ++i) {
            // Using % means the distribution isn't uniform, but that's OK.
            int position = random.nextInt(256) % seed.length();
            result += seed.charAt(position);
        }

        return result;
    }

    public static double
    getNowMilliseconds() { return (double)System.currentTimeMillis(); }

    private void
    messageCacheAppend(MessageBuffer.Messages.MessageType messageType, String message)
    {
        //messageCache_.add(new CachedMessage(sync_.getSequenceNo(), messageType, message, getNowMilliseconds()));
        while (messageCache_.size() > maxMessageCacheLength_)
            messageCache_.remove(0);
    }

    private class Heartbeat implements OnTimeout {
        public final void
        onTimeout(Interest interest) {
            if (messageCache_.size() == 0)
                //messageCacheAppend(ChatMessage.ChatMessageType.JOIN, "xxx");

            try {
                sync_.publishNextSequenceNo();
            } catch (Exception ex) {
                Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            //messageCacheAppend(ChatMessage.ChatMessageType.HELLO, "xxx");

            // Call again.
            // TODO: Are we sure using a "/local/timeout" interest is the best future call approach?
            Interest timeout = new Interest(new Name("/local/timeout"));
            timeout.setInterestLifetimeMilliseconds(60000);
            try {
                //face_.expressInterest(timeout, DummyOnData.onData_, heartbeat_);
            } catch (Exception ex) {
                Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}

public class GameSync extends IntentService {
    public ChronoSync2013 sync;
    public ReceivedSyncState rec;
    public ArrayList messages;

    public GameSync(String name) {
        super(name);
    }

    public class ReceivedSyncState implements ChronoSync2013.OnReceivedSyncState {
        @Override
        public void onReceivedSyncState(List syncStates, boolean isRecovery) {

        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}

