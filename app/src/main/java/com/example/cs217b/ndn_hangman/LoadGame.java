package com.example.cs217b.ndn_hangman;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import net.named_data.jndn.*;

import java.util.ArrayList;

/*
Send interest to find players
Create a player list
When 2 players are found, then start new game
 */

public class LoadGame extends ActionBarActivity {
    private ArrayList<String>  playerList;
    private Face loadFace, discFace;
    private Name findPlayerInterest;

    private class FindPlayer implements OnData, OnTimeout {
        public void onData(Interest interest, Data data) {

        }

        public boolean timeOut = false;
        public void onTimeout(Interest interest) {
            timeOut = true;
        }
    };

    private class DiscPlayer implements OnInterestCallback, OnRegisterFailed {
        public void onInterest(Name prefix, Interest interest, Face face, long id, InterestFilter filter) {

        }

        public boolean regFail = false;
        public void onRegisterFailed(Name prefix) {
            regFail = true;
        }
    }

    private class DiscoveredNet extends Thread {
//        discFace = new Face("localhost");

        public DiscoveredNet() {};

        @Override
        public void run() {
            try {
                discFace = new Face("localhost");
                discFace.setCommandSigningInfo(MainActivity.keychain, MainActivity.keychain.getDefaultCertificateName());
                DiscPlayer discovery = new DiscPlayer();
                discFace.registerPrefix(findPlayerInterest, discovery, discovery);

                while(!discovery.regFail) {
                    discFace.processEvents();

                    Thread.sleep(5);
                }
            } catch (Exception e) {

            }
        }
    };
    private class FindPlayerNet extends Thread {
        public FindPlayerNet() {}

        @Override
        public void run() {
            try {
//                loadFace = new Face("spurs.cs.ucla.edu");
                loadFace = new Face("localhost");
                findPlayerInterest = new Name("/ndn-hangman/find/player/");
                FindPlayer findPlayer = new FindPlayer();
                loadFace.expressInterest(findPlayerInterest,findPlayer,findPlayer);

                while (findPlayer.timeOut = false) {

                    loadFace.processEvents();

                    Thread.sleep(5);
                }
            } catch (Exception e) {
                Log.i("NDN", "exception: " + e.getMessage());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_game);

        FindPlayerNet playerNet = new FindPlayerNet();
        playerNet.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_load_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
