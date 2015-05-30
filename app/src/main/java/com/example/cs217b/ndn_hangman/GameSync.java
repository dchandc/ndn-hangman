package com.example.cs217b.ndn_hangman;

import android.util.Log;

import net.named_data.jndn.*;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.sync.*;
import net.named_data.jndn.security.*;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.util.*;
import com.example.cs217b.ndn_hangman.MessageBuffer.Messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import java.util.Random;

public class GameSync implements ChronoSync2013.OnInitialized,
        ChronoSync2013.OnReceivedSyncState, OnData, OnInterestCallback {
    public boolean changedState;
    public boolean guessReceived;
    public boolean isHost = false;
    public String lastGuesser;
    public String gameState;
    public String lastGuess;
    public String playerName_;
    public String userName_;
    public String gameName_;
    public ArrayList<String> roster_ = new ArrayList<>();
    public Face face_;
    public KeyChain keyChain_;
    public Name certificateName_;
    public Name gamePrefix_;
    public boolean done = false;
    private OnTimeout heartbeat_;
    public ArrayList<CachedMessage> messageCache_ = new ArrayList<>();
    public ChronoSync2013 sync_;
    private final double syncLifetime_ = 5000.0; // milliseconds
    private boolean isRecoverySyncState_ = true;

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
            Log.i("gamesync", "[GameSync] ChronoSync object created");
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
            Log.i("gamesync", "[onInitialized] heartbeat timeout set");
        } catch (IOException ex) {
            Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        if (roster_.indexOf(userName_) < 0) {
            roster_.add(userName_);
            try {
                sendJoinMessage();
            } catch (Exception e) {
                Log.i("gamesync", "[onInitialized] exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    // Process the incoming Chat data.
    // (Do not call this. It is only public to implement the interface.)
    public final void
    onData(Interest interest, Data data) {
        Messages content;
        try {
            content = Messages.parseFrom(data.getContent().getImmutableArray());
        } catch (Exception ex) {
            Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        Log.i("gamesync", "[onData] " + content.getName() + " (" + content.getType() + ")");
        if (getNowMilliseconds() - content.getTimestamp() * 1000.0 < 120000.0) {
            String name = content.getName();
            String prefix = data.getName().getPrefix(-2).toUri();
            long sessionNo = Long.parseLong(data.getName().get(-2).toEscapedString());
            long sequenceNo = Long.parseLong(data.getName().get(-1).toEscapedString());
            String nameAndSession = name + sessionNo;

            int l = 0;
            //update roster
            while (l < roster_.size()) {
                String entry = roster_.get(l);
                String tempName = entry.substring(0, entry.length() - 10);
                long tempSessionNo = Long.parseLong(entry.substring(entry.length() - 10));
                if (!name.equals(tempName) && !content.getType().equals(Messages.MessageType.LEAVE))
                    ++l;
                else {
                    if (name.equals(tempName) && sessionNo > tempSessionNo)
                        roster_.set(l, nameAndSession);
                    break;
                }
            }

            if (l == roster_.size()) {
                roster_.add(nameAndSession);
                Log.i("Joined", nameAndSession);
            }

            // Set the alive timeout using the Interest timeout mechanism.
            // TODO: Are we sure using a "/local/timeout" interest is the best future call approach?
            Interest timeout = new Interest(new Name("/local/timeout"));
            timeout.setInterestLifetimeMilliseconds(120000);
            try {
                face_.expressInterest
                        (timeout, DummyOnData.onData_,
                                this.new Alive(sequenceNo, name, sessionNo, prefix));
            } catch (IOException ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }

            // isRecoverySyncState_ was set by sendInterest.
            // TODO: If isRecoverySyncState_ changed, this assumes that we won't get
            //   data from an interest sent before it changed.
            if (content.getType().equals(Messages.MessageType.EVAL) && !content.getName().equals(playerName_) &&
                    !isHost) {
                gameState = content.getWord();
                lastGuesser = content.getName();
                Log.i("Received Eval", gameState);
                changedState = true; //or call static method in game activity to signal new game state
            }
            else if (content.getType().equals(Messages.MessageType.GUESS) && !content.getName().equals(playerName_) &&
                    isHost) {
                lastGuess = content.getWord();
                lastGuesser = content.getName();
                Log.i("Guesser", lastGuesser);
                Log.i("Received Guess", lastGuess);
                guessReceived = true; //or call static method in game activity to signal guess received
            }
            else if (content.getType().equals(Messages.MessageType.LEAVE)) {
                // leave message
                int n = roster_.indexOf(nameAndSession);
                if (n >= 0 && !name.equals(playerName_)) {
                    roster_.remove(n);
                    Log.i("Leave", name);
                }
            }
        }
    }

    @Override
    // Send back a Chat Data Packet which contains the user's message.
    // (Do not call this. It is only public to implement the interface.)
    public final void
    onInterest
            (Name prefix, Interest interest, Face face, long interestFilterId,
             InterestFilter filter)
    {
        Log.i("gamesync", "[onInterest] interest=" + interest.getName().toUri());
        Messages.Builder builder = Messages.newBuilder();
        long sequenceNo = Long.parseLong(interest.getName().get(gamePrefix_.size() + 1).toEscapedString());
        boolean gotContent = false;
        for (int i = messageCache_.size() - 1; i >= 0; --i) {
            CachedMessage message = messageCache_.get(i);
            if (message.getSequenceNo() == sequenceNo) {
                if (message.getMessageType().equals(Messages.MessageType.GUESS) || message.getMessageType().equals(Messages.MessageType.EVAL)) {
                    builder.setName(playerName_);
                    builder.setType(message.getMessageType());
                    builder.setWord(message.getMessage());
                    builder.setTimestamp((int) Math.round(message.getTime() / 1000.0));
                } else {
                    builder.setName(playerName_);
                    builder.setType(message.getMessageType());
                    builder.setTimestamp((int) Math.round(message.getTime() / 1000.0));
                }
                gotContent = true;
                Log.i("gamesync", "[onInterest] " + message.getMessage() + " (" + message.getMessageType() + ")");
                break;
            }
        }

        if (gotContent) {
            Messages content = builder.build();
            byte[] array = content.toByteArray();
            Data data = new Data(interest.getName());
            data.setContent(new Blob(array));
            try {
                keyChain_.sign(data, certificateName_);
            } catch (Exception ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            try {
                face.putData(data);
                Log.i("gamesync", "[onInterest] content name=" + content.getName() + " word=" +
                        content.getWord() + " (" + content.getType() + ")");
            } catch (IOException ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public final void
    onReceivedSyncState(List syncStates, boolean isRecovery) {
        // This is used by onData to decide whether to display the chat messages.
        isRecoverySyncState_ = isRecovery;

        Log.i("gamesync", "[onReceivedSyncState] isRecovery=" + ((isRecovery) ? "true" : "false"));
        ArrayList<String> sendList = new ArrayList<>();
        ArrayList<Long> sessionNoList = new ArrayList<>();
        ArrayList<Long> sequenceNoList = new ArrayList<>();
        for (int j = 0; j < syncStates.size(); ++j) {
            ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState) syncStates.get(j);
            Name nameComponents = new Name(syncState.getDataPrefix());
            String tempName = nameComponents.get(-1).toEscapedString();
            long sessionNo = syncState.getSessionNo();
            if (!tempName.equals(playerName_)) {
                int index = -1;
                for (int k = 0; k < sendList.size(); ++k) {
                    if (sendList.get(k).equals(syncState.getDataPrefix())) {
                        index = k;
                        break;
                    }
                }
                if (index != -1) {
                    sessionNoList.set(index, sessionNo);
                    sequenceNoList.set(index, syncState.getSequenceNo());
                } else {
                    sendList.add(syncState.getDataPrefix());
                    sessionNoList.add(sessionNo);
                    sequenceNoList.add(syncState.getSequenceNo());
                }
            }
        }

        for (int i = 0; i < sendList.size(); ++i) {
            String uri = sendList.get(i) + "/" + sessionNoList.get(i) +
                    "/" + sequenceNoList.get(i);
            Interest interest = new Interest(new Name(uri));
            interest.setInterestLifetimeMilliseconds(syncLifetime_);
            try {
                face_.expressInterest(interest, this, ChatTimeout.onTimeout_);
            } catch (IOException ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        }
    }

    private static class ChatTimeout implements OnTimeout {
        public final void
        onTimeout(Interest interest) {
            Log.i("gamesync", "[ChatTimeout] timed out");
        }

        public final static OnTimeout onTimeout_ = new ChatTimeout();
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
            Log.i("gamesync", "[Heartbeat] timed out");
            if (messageCache_.size() == 0)
                messageCacheAppend(Messages.MessageType.JOIN, "xxx");

            try {
                sync_.publishNextSequenceNo();
            } catch (Exception ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            messageCacheAppend(Messages.MessageType.HELLO, "xxx");

            // Call again.
            // TODO: Are we sure using a "/local/timeout" interest is the best future call approach?
            if (!done) {
                Interest timeout = new Interest(new Name("/local/timeout"));
                timeout.setInterestLifetimeMilliseconds(60000);
                try {
                    face_.expressInterest(timeout, DummyOnData.onData_, heartbeat_);
                } catch (Exception ex) {
                    Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                }
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
            Log.i("gamesync", "[Alive] tempSequenceNo_=" + tempSequenceNo_ +
                    " name=" + name_ + " sessionNo_=" + sessionNo_ + " prefix_=" + prefix_);
            Log.i("gamesync", "[Alive] sequenceNo=" + sequenceNo + " nameAndSession=" + nameAndSession +
                    " n=" + n);
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
            Log.i("gamesync", "[RegisterFailed] prefix=" + prefix.toUri());
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
    }

    // Send a join message
    public final void
    sendJoinMessage() throws IOException, SecurityException
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.JOIN, "xxx");
        Log.i("gamesync", "[sendJoinMessage]");
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
            Log.i("gamesync", "[sendGuessMessage] guess=" + guess);
        }
    }

    public final void
    sendEvalMessage(String gameState) throws  Exception
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.EVAL, gameState);
        Log.i("gamesync", "[sendEvalMessage] gameState=" + gameState);
    }

    public final void
    sendLeaveMessage() throws IOException, SecurityException
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.LEAVE, "xxx");
        Log.i("gamesync", "[sendLeaveMessage]");
    }

    private void
    messageCacheAppend(MessageBuffer.Messages.MessageType messageType, String message)
    {
        final int maxMessageCacheLength_ = 100;
        Log.i("gamesync", "[messageCacheAppend] " + message + " (" + messageType + ")");
        messageCache_.add(new CachedMessage(sync_.getSequenceNo(), messageType, message, getNowMilliseconds()));
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
