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

/**
 * This class is used to synchronize the data between players. Code based on TestChronoChat.java at
 * https://github.com/named-data/jndn/blob/master/examples/src/net/named_data/jndn/tests/
 */
public class GameSync implements ChronoSync2013.OnInitialized,
        ChronoSync2013.OnReceivedSyncState, OnData, OnInterestCallback {
    private final double syncLifetime_ = 5000.0; // milliseconds
    public String playerName_;
    public long sessionNo_;
    public String gameName_;
    public ArrayList<Player> roster_ = new ArrayList<>();
    public Face face_;
    public KeyChain keyChain_;
    public Name certificateName_;
    public Name gamePrefix_;
    public String sid_;
    public boolean done = false;
    private OnTimeout heartbeat_;
    public ArrayList<CachedMessage> messageCache_ = new ArrayList<>();
    public ChronoSync2013 sync_;
    public int roundNum_ = 0;
    public int guessNum_ = 0;

    public GameSync(String playerName, String gameName, Name hubPrefix, Face face,
                    KeyChain keyChain, Name certificateName) {
        playerName_ = playerName;
        gameName_ = gameName;
        face_ = face;
        keyChain_ = keyChain;
        certificateName_ = certificateName;
        heartbeat_ = this.new Heartbeat();
        sid_ = getRandomString();
        gamePrefix_ = new Name(hubPrefix).append(gameName_).append(sid_);
        sessionNo_ = Math.round(getNowMilliseconds() / 1000.0);
        try {
            sync_ = new ChronoSync2013(this, this, gamePrefix_,
                            new Name("/ndn/edu/ucla/hangman/broadcast").append(gameName_),
                            sessionNo_, face_, keyChain_, certificateName_, syncLifetime_,
                            RegisterFailed.onRegisterFailed_);
            Log.i("sync", "[GameSync] ChronoSync object created");
        } catch (Exception ex) {
            Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        try {
            face.registerPrefix(gamePrefix_, this, RegisterFailed.onRegisterFailed_);
        } catch (Exception ex) {
            Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Push the JOIN message in to the messageCache_, update roster and
    // start the heartbeat.
    @Override
    public final void onInitialized()
    {
        // Set the heartbeat timeout using the Interest timeout mechanism. The
        // heartbeat() function will call itself again after a timeout.
        Interest timeout = new Interest(new Name("/local/timeout"));
        timeout.setInterestLifetimeMilliseconds(60000);
        try {
            face_.expressInterest(timeout, DummyOnData.onData_, heartbeat_);
            Log.i("sync", "[onInitialized] heartbeat timeout set");
        } catch (IOException ex) {
            Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        roster_.add(new OnlinePlayer(playerName_, sessionNo_, sid_, true));
        try {
            join();
        } catch (Exception e) {
            Log.i("sync", "[onInitialized] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Process the incoming Chat data.
    // (Do not call this. It is only public to implement the interface.)
    @Override
    public final void onData(Interest interest, Data data)
    {
        Messages content;
        try {
            content = Messages.parseFrom(data.getContent().getImmutableArray());
        } catch (Exception ex) {
            Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        String name = content.getName();
        String prefix = data.getName().getPrefix(-2).toUri();
        String sid = data.getName().get(-3).toEscapedString();
        long sessionNo = Long.parseLong(data.getName().get(-2).toEscapedString());
        long sequenceNo = Long.parseLong(data.getName().get(-1).toEscapedString());
        String word = content.getWord();
        Messages.MessageType type = content.getType();

        Log.i("sync", "[onData] name=" + name + " prefix=" + prefix + " sid=" + sid +
                " sessionNo=" + sessionNo + " sequenceNo=" + sequenceNo + " word=" + word +
                " (" + type + ")");

        // Update roster.
        int i;
        Player player = null;
        for (i = 0; i < roster_.size(); i++) {
            player = roster_.get(i);
            String tempName = player.name;
            String tempSid = player.sid;
            long tempSessionNo = player.sessionNo;
            if (name.equals(tempName) && sid.equals(tempSid)) {
                if (sessionNo > tempSessionNo) {
                    player.sessionNo = sessionNo;
                }
                break;
            }
        }

        if (i == roster_.size() && type.equals(Messages.MessageType.JOIN)) {
            player = new OnlinePlayer(name, sessionNo, sid, false);
            roster_.add(player);
            Log.i("sync", "[onData] added player=(" + name + ", " + sessionNo + ", " +
                    sid + ")");
        }

        Log.i("sync", "[onData] roundNum=" + roundNum_ + " guessNum=" + guessNum_);
        if (type.equals(Messages.MessageType.EVAL) && !sid.equals(sid_)) {
            String[] parts = word.split("-");
            int roundNum = Integer.valueOf(parts[0]);
            int guessNum = Integer.valueOf(parts[1]);
            String eval = parts[2];
            if (roundNum == roundNum_ && guessNum == guessNum_ && player.isThinking()) {
                player.think(eval);
                Log.i("sync", "[onData] drawer think");
            }
        } else if (type.equals(Messages.MessageType.GUESS) && !sid.equals(sid_)) {
            String[] parts = word.split("-");
            int roundNum = Integer.valueOf(parts[0]);
            int guessNum = Integer.valueOf(parts[1]);
            char guess = parts[2].charAt(0);
            if (roundNum == roundNum_ && guessNum == guessNum_ && player.isThinking()) {
                player.think(guess);
                Log.i("sync", "[onData] guesser think");
            }
        } else if (type.equals(Messages.MessageType.LEAVE)) {
            for (i = 0; i < roster_.size(); i++) {
                player = roster_.get(i);
                if (player.name.equals(name) && player.sessionNo == sessionNo &&
                        player.sid.equals(sid)) {
                    roster_.remove(i);
                    Log.i("sync", "[onData] removed player=(" + player.name + ", " +
                            player.sessionNo + ", " + player.sid + ")");
                    break;
                }
            }
        }

        // Set the alive timeout using the Interest timeout mechanism.
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
    }

    // Send back a Chat Data Packet which contains the user's message.
    // (Do not call this. It is only public to implement the interface.)
    @Override
    public final void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
             InterestFilter filter)
    {
        Log.i("sync", "[onInterest] interest=" + interest.getName().toUri());
        Messages.Builder builder = Messages.newBuilder();
        long sequenceNo = Long.parseLong(interest.getName().get(gamePrefix_.size() + 1).toEscapedString());
        boolean gotContent = false;
        for (int i = messageCache_.size() - 1; i >= 0; --i) {
            CachedMessage message = messageCache_.get(i);
            Messages.MessageType type = message.getMessageType();
            if (message.getSequenceNo() == sequenceNo) {
                if (type.equals(Messages.MessageType.GUESS)||
                        type.equals(Messages.MessageType.EVAL)) {
                    builder.setName(playerName_);
                    builder.setType(message.getMessageType());
                    builder.setWord(message.getMessage());
                    builder.setTimestamp((int) Math.round(message.getTime() / 1000.0));
                } else if (type.equals(Messages.MessageType.HELLO)){
                    boolean gotPlay = false;
                    for (i = i - 1; i >= 0; --i) {
                        CachedMessage tempMessage = messageCache_.get(i);
                        Messages.MessageType tempType = tempMessage.getMessageType();
                        if (tempType.equals(Messages.MessageType.GUESS)||
                                tempType.equals(Messages.MessageType.EVAL)) {
                            builder.setName(playerName_);
                            builder.setType(tempMessage.getMessageType());
                            builder.setWord(tempMessage.getMessage());
                            builder.setTimestamp((int) Math.round(tempMessage.getTime() / 1000.0));
                            gotPlay = true;
                            break;
                        }
                    }

                    if (!gotPlay) {
                        builder.setName(playerName_);
                        builder.setType(message.getMessageType());
                        builder.setTimestamp((int) Math.round(message.getTime() / 1000.0));
                    }
                } else {
                    builder.setName(playerName_);
                    builder.setType(message.getMessageType());
                    builder.setTimestamp((int) Math.round(message.getTime() / 1000.0));
                }
                gotContent = true;
                Log.i("sync", "[onInterest] " + message.getMessage() + " (" + message.getMessageType() + ")");
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
                Log.i("sync", "[onInterest] content name=" + content.getName() + " word=" +
                        content.getWord() + " (" + content.getType() + ")");
            } catch (IOException ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public final void
    onReceivedSyncState(List syncStates, boolean isRecovery) {
        Log.i("sync", "[onReceivedSyncState] isRecovery=" + ((isRecovery) ? "true" : "false"));
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
            Log.i("sync", "[onReceivedSyncState] express interest uri=" + uri);
            try {
                face_.expressInterest(interest, this, SyncTimeout.onTimeout_);
            } catch (IOException ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        }
    }

    private static class SyncTimeout implements OnTimeout {
        public final void
        onTimeout(Interest interest) {
            Log.i("sync", "[SyncTimeout] timed out interest=" + interest.toUri());
        }

        public final static OnTimeout onTimeout_ = new SyncTimeout();
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
            Log.i("sync", "[Heartbeat] timed out");
            if (messageCache_.size() == 0)
                messageCacheAppend(Messages.MessageType.JOIN, "xxx");

            try {
                sync_.publishNextSequenceNo();
            } catch (Exception ex) {
                Logger.getLogger(GameSync.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            messageCacheAppend(Messages.MessageType.HELLO, "xxx");

            // Call again if necessary.
            if (!done) {
                Interest timeout = new Interest(new Name("/local/timeout"));
                timeout.setInterestLifetimeMilliseconds(15000);
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
            Log.i("sync", "[Alive] tempSequenceNo_=" + tempSequenceNo_ +
                    " name=" + name_ + " sessionNo_=" + sessionNo_ + " prefix_=" + prefix_);
            if (sequenceNo == -1 || tempSequenceNo_ != sequenceNo)
                return;

            for (int i = 0; i < roster_.size(); i++) {
                Player player = roster_.get(i);
                if (player.sessionNo == sessionNo_ && player.name.equals(name_)) {
                    roster_.remove(i);
                    Log.i("sync", "[Alive] removed player=(" + player.name + ", " +
                            player.sessionNo + ", " + player.sid + ")");
                    break;
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
            Log.i("sync", "[RegisterFailed] prefix=" + prefix.toUri());
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

    // Publish a join message.
    public final void
    join() throws IOException, SecurityException
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.JOIN, "xxx");
        Log.i("sync", "[join]");
    }

    // Publish a guess message.
    public final void
    guess(String guess) throws IOException, SecurityException
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.GUESS, guess);
        Log.i("sync", "[guess] " + guess);
    }

    // Publish an eval message.
    public final void
    eval(String eval) throws  IOException, SecurityException
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.EVAL, eval);
        Log.i("sync", "[eval] " + eval);
    }

    // Publish a leave message.
    public final void
    leave() throws IOException, SecurityException
    {
        sync_.publishNextSequenceNo();
        messageCacheAppend(Messages.MessageType.LEAVE, "xxx");
        Log.i("sync", "[leave]");
    }

    //
    private void
    messageCacheAppend(MessageBuffer.Messages.MessageType messageType, String message)
    {
        final int maxMessageCacheLength = 100;
        messageCache_.add(new CachedMessage(sync_.getSequenceNo(), messageType, message,
                getNowMilliseconds()));
        Log.i("sync", "[messageCacheAppend] " + message + " (" + messageType + ")" + "[" +
                messageCache_.size() + "]");
        while (messageCache_.size() > maxMessageCacheLength)
            messageCache_.remove(0);
    }

    // Get random String for user identification.
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
