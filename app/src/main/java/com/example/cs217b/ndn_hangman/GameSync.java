package com.example.cs217b.ndn_hangman;

import android.os.Message;

import net.named_data.jndn.*;
import net.named_data.jndn.sync.*;
import net.named_data.jndn.security.*;
import net.named_data.jndn.OnRegisterFailed;
import com.example.cs217b.ndn_hangman.MessageBuffer.Messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import java.util.Random;

public class GameSync implements ChronoSync2013.OnInitialized,
        ChronoSync2013.OnReceivedSyncState, OnData, OnInterestCallback {
    public String playerName_;
    public String userName_;
    public String gameName_;
    public ArrayList<String> roster_ = new ArrayList<String>();
    public Face face_;
    public KeyChain keyChain_;
    public Name certificateName_;
    public Name gamePrefix_;
    private OnTimeout heartbeat_;
    public ArrayList messageCache_;
    public ChronoSync2013 sync_;
    private final double syncLifetime_ = 5000.0; // milliseconds
    private final int maxMessageCacheLength_ = 100;

    public GameSync(String playerName, String gameName, Name hubPrefix, Face face, KeyChain keyChain, Name certificateName) {
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
                            new Name("/ndn/edu/ucla/hangman/broadcast").append(gameName_), session,
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

    // Send a chat message.
    public final void
    sendGuessMessage(String guess) throws Exception
    {
        if (messageCache_.size() == 0)
            messageCacheAppend(Messages.MessageType.JOIN, "xxx");

        // Ignore an empty message.
        // forming Sync Data Packet.
        if (!guess.equals("")) {
            sync_.publishNextSequenceNo();
            messageCacheAppend(Messages.MessageType.GUESS, guess);
            System.out.println(playerName_ + ": " + guess);
        }
    }

    @Override
    public void onData(Interest interest, Data data) {

    }

    // initial: push the JOIN message in to the messageCache_, update roster and
    // start the heartbeat.
    // (Do not call this. It is only public to implement the interface.)
    @Override
    public final void
    onInitialized()
    {
        // Set the heartbeat timeout using the Interest timeout mechanism. The
        // heartbeat() function will call itself again after a timeout.
        Interest timeout = new Interest(new Name("/local/timeout"));
        timeout.setInterestLifetimeMilliseconds(60000);
        try {
            face_.expressInterest(timeout, DummyOnData.onData_, heartbeat_);
        } catch (IOException ex) {
            Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        if (roster_.indexOf(userName_) < 0) {
            roster_.add(userName_);
            System.out.println("Member: " + playerName_);
            System.out.println(playerName_ + ": Join");
            messageCacheAppend(Messages.MessageType.JOIN, "xxx");
        }
    }

    @Override
    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

    }

    @Override
    public void onReceivedSyncState(List syncStates, boolean isRecovery) {

    }

    /**
     * This repeatedly calls itself after a timeout to send a heartbeat message
     * (chat message type HELLO).
     * This method has an "interest" argument because we use it as the onTimeout
     * for Face.expressInterest.
     */
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

    /**
     * This is called after a timeout to check if the user with prefix has a newer
     * sequence number than the given temp_seq. If not, assume the user is idle and
     * remove from the roster and print a leave message.
     * This is used as the onTimeout for Face.expressInterest.
     */
    private class Alive implements OnTimeout {
        public Alive(long tempSequenceNo, String name, long sessionNo, String prefix)
        {
            tempSequenceNo_ = tempSequenceNo;
            name_ = name;
            sessionNo_ = sessionNo;
            prefix_ = prefix;
        }

        public final void
        onTimeout(Interest interest)
        {
            long sequenceNo = sync_.getProducerSequenceNo(prefix_, sessionNo_);
            String nameAndSession = name_ + sessionNo_;
            int n = roster_.indexOf(nameAndSession);
            if (sequenceNo != -1 && n >= 0) {
                if (tempSequenceNo_ == sequenceNo) {
                    roster_.remove(n);
                    System.out.println(name_ + ": Leave");
                }
            }
        }

        private final long tempSequenceNo_;
        private final String name_;
        private final long sessionNo_;
        private final String prefix_;
    }

    private static class RegisterFailed implements OnRegisterFailed {
        public final void
        onRegisterFailed(Name prefix)
        {
            System.out.println("Register failed for prefix " + prefix.toUri());
        }

        public final static OnRegisterFailed onRegisterFailed_ = new RegisterFailed();
    }

    // This is a do-nothing onData for using expressInterest for timeouts.
    // This should never be called.
    private static class DummyOnData implements OnData {
        public final void
        onData(Interest interest, Data data) {}

        public final static OnData onData_ = new DummyOnData();
    }

    private static class CachedMessage {
        public CachedMessage
                (long sequenceNo, Messages.MessageType messageType, String message, double time)
        {
            sequenceNo_ = sequenceNo;
            messageType_ = messageType;
            message_ = message;
            time_ = time;
        }

        public final long
        getSequenceNo() { return sequenceNo_; }

        public final Messages.MessageType
        getMessageType() { return messageType_; }

        public final String
        getMessage() { return message_; }

        public final double
        getTime() { return time_; }

        private final long sequenceNo_;
        private final Messages.MessageType messageType_;
        private final String message_;
        private final double time_;
    };

    private void
    messageCacheAppend(MessageBuffer.Messages.MessageType messageType, String message)
    {
        //messageCache_.add(new CachedMessage(sync_.getSequenceNo(), messageType, message, getNowMilliseconds()));
        while (messageCache_.size() > maxMessageCacheLength_)
            messageCache_.remove(0);
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
}
