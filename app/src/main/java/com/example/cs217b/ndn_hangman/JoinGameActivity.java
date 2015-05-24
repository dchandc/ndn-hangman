package com.example.cs217b.ndn_hangman;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.KeyType;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.NoVerifyPolicyManager;
import net.named_data.jndn.util.Blob;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Dennis on 5/24/2015.
 */
public class JoinGameActivity extends ActionBarActivity {
    private Object lock;
    private JoinTask joinTask;
    private EditText etv_name;
    private EditText etv_room;
    private Button btn_startjoin;
    private TextView tv_roster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joingame);

        etv_name = (EditText) findViewById(R.id.etext_name);
        etv_room = (EditText) findViewById(R.id.etext_room);
        btn_startjoin = (Button) findViewById(R.id.button_startjoin);
        tv_roster = (TextView) findViewById(R.id.text_roster);

        btn_startjoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etv_name.getText().toString();
                String room = etv_room.getText().toString();
                if (!name.isEmpty() && !room.isEmpty()) {
                    lock = new Object();
                    joinTask = new JoinTask(name, room, lock);
                    joinTask.execute();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        joinTask.faceThread.interrupt();
        joinTask.cancel(true);
    }

    public class JoinTask extends AsyncTask<Void, ArrayList<String>, String> {
        private final String hubPrefix = "/ndn/edu/ucla/hangman/app";
        private final String host = "localhost";
        private final Face face =  new Face(host);
        private Object lock;
        private GameSync gs;
        private String name;
        private String room;
        private Thread faceThread;

        public JoinTask(String name, String room, Object lock) {
            this.lock = lock;
            this.name = name;
            this.room = room;
        }

        @Override
        protected String doInBackground(Void... ignored) {
            try {
                MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
                MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
                KeyChain keyChain = new KeyChain
                        (new IdentityManager(identityStorage, privateKeyStorage),
                                new NoVerifyPolicyManager());
                keyChain.setFace(face);
                Name keyName = new Name("/testname/DSK-123");
                Name certificateName = keyName.getSubName(0, keyName.size() - 1).append
                        ("KEY").append(keyName.get(-1)).append("ID-CERT").append("0");
                identityStorage.addKey(keyName, KeyType.RSA, new Blob(Keys.DEFAULT_RSA_PUBLIC_KEY_DER, false));
                privateKeyStorage.setKeyPairForKeyName
                        (keyName, KeyType.RSA, Keys.DEFAULT_RSA_PUBLIC_KEY_DER, Keys.DEFAULT_RSA_PRIVATE_KEY_DER);
                face.setCommandSigningInfo(keyChain, certificateName);

                gs = new GameSync(name, room, new Name(hubPrefix), face, keyChain, certificateName);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            faceThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.interrupted()) {
                        try {
                            face.processEvents();
                            Thread.sleep(10);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            faceThread.start();

            while (!isCancelled()) {
                try {
                    gs.sendGuessMessage("TEST");
                    Thread.sleep(1000);
                    Log.i("join", "Check roster");
                    publishProgress(gs.roster_);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return "FAIL";
        }

        @Override
        protected void onProgressUpdate(ArrayList<String>... update) {
            ArrayList<String> roster = update[0];
            StringBuilder sb = new StringBuilder("");
            for (int i = 0; i < roster.size(); i++) {
                String playerName = roster.get(i);
                sb.append(playerName + "\n");
            }
            tv_roster.setText(sb.toString());
        }
    }
}
