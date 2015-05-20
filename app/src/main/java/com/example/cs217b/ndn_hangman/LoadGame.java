package com.example.cs217b.ndn_hangman;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import net.named_data.jndn.*;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.util.Blob;

import java.io.IOException;

public class LoadGame extends ActionBarActivity {
    private Face loadFace, discFace;

    private class FindPlayer implements OnData, OnTimeout {
        public boolean done = false;
        public void onTimeout(Interest interest) {
            Log.i("NDN", "Interest timeout");
            done = true;
        }


        public void onData(Interest interest, Data data) {
            Log.i("NDN", "Got data packet with name " + data.getName().toUri() +
                    " and content " + data.getContent().toString());
            done = true;
        }
    }

    private class DiscPlayer implements OnInterestCallback, OnRegisterFailed {
        public boolean done = false;
        public void onRegisterFailed(Name prefix) {
            Log.i("NDN", "Register failed for " + prefix.toUri());
            done = true;
        }

        public void onInterest(Name prefix, Interest interest, Face face, long id, InterestFilter filter) {
            Log.i("NDN", "Received interest " + interest.getName().toUri());
            Data data = new Data();
            data.setName(new Name(interest.getName()));
            Blob blob = new Blob("Hello".getBytes());
            data.setContent(blob);
            try {
                face.putData(data);
            } catch (IOException e) {
                Log.i("NDN", "putData exception");
            }
            done = true;
        }
    }

    /**
     * Setup an in-memory KeyChain with a default identity.
     *
     * @return
     * @throws net.named_data.jndn.security.SecurityException
     */
    public static KeyChain buildTestKeyChain() throws net.named_data.jndn.security.SecurityException {
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        KeyChain keyChain = new KeyChain(identityManager);
        try {
            keyChain.getDefaultCertificateName();
        } catch (net.named_data.jndn.security.SecurityException e) {
            keyChain.createIdentity(new Name("/test/identity"));
            keyChain.getIdentityManager().setDefaultIdentity(new Name("/test/identity"));
        }
        return keyChain;
    }

    private class DiscoveredNet extends Thread {
        boolean interestComplete = false;

        @Override
        public void run() {
            try {
                //discFace = new Face("spurs.cs.ucla.edu");
                discFace = new Face("localhost");
                KeyChain keychain = buildTestKeyChain();
                discFace.setCommandSigningInfo(keychain, keychain.getDefaultCertificateName());

                Interest interest = new Interest(new Name("/ndn/org/caida/ping/" +
                        Math.floor(Math.random() * 100000)));
                interest.setInterestLifetimeMilliseconds(1000);
                discFace.expressInterest(interest, new OnData() {
                    public void onData(Interest interest, Data data) {
                        Log.i("NDN", "Data " + data.getName().toUri() +
                                " received for interest " + interest.getName().toUri());
                        interestComplete = true;
                    }
                }, new OnTimeout() {
                    public void onTimeout(Interest interest) {
                        Log.i("NDN", "Timeout");
                        interestComplete = true;
                    }
                });

                while (interestComplete == false) {
                    discFace.processEvents();
                    Thread.sleep(5);
                }

                DiscPlayer discovery = new DiscPlayer();
                Name prefix = new Name("/ndn/edu/ucla/hangman");
                discFace.registerPrefix(prefix, discovery, discovery);
                discFace.setInterestFilter(prefix, discovery);
                while(discovery.done == false) {
                    discFace.processEvents();
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("NDN", "Producer exception");
            }
        }
    }
    private class FindPlayerNet extends Thread {
        public FindPlayerNet() {}

        @Override
        public void run() {
            try {
                //loadFace = new Face("spurs.cs.ucla.edu");
                loadFace = new Face("localhost");
                FindPlayer findPlayer = new FindPlayer();
                Interest findPlayerInterest = new Interest(new Name("/ndn/edu/ucla/hangman"));
                findPlayerInterest.setInterestLifetimeMilliseconds(1000);
                loadFace.expressInterest(findPlayerInterest,findPlayer,findPlayer);

                while (findPlayer.done == false) {
                    loadFace.processEvents();
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("NDN", "exception: " + e.getMessage());
            }
        }
    };

    private class PingNet extends Thread {
        @Override
        public void run() {
            try {
                loadFace = new Face("spurs.cs.ucla.edu");
                FindPlayer findPlayer = new FindPlayer();
                Interest findPlayerInterest = new Interest(new Name("/ndn/org/caida/ping/" +
                        Math.floor(Math.random() * 100000)));
                findPlayerInterest.setInterestLifetimeMilliseconds(1000);
                loadFace.expressInterest(findPlayerInterest,findPlayer,findPlayer);

                while (findPlayer.done == false) {
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

        Button btn = (Button) findViewById(R.id.button_interest);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FindPlayerNet playerNet = new FindPlayerNet();
                playerNet.start();
                Log.i("NDN", "Thread started");
            }
        });

        Button btn2 = (Button) findViewById(R.id.button_data);
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DiscoveredNet discNet = new DiscoveredNet();
                discNet.start();
                Log.i("NDN", "Thread started");
            }
        });

        Button btn3 = (Button) findViewById(R.id.button_ping);
        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PingNet pingNet = new PingNet();
                pingNet.start();
                Log.i("NDN", "Thread started");
            }
        });
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
