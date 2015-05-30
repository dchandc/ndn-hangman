package com.example.cs217b.ndn_hangman;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

public class JoinGameActivity extends ActionBarActivity {
    private Context context;
    private Object lock;
    private JoinTask joinTask;

    // activity_joingame layout
    private EditText etv_name;
    private EditText etv_room;
    private TextView tv_roster;
    private Button btn_startjoin;

    // activity_newgame layout
    private TextView tv_score, tv_status, tv_letters, tv_remain;
    private ImageView img_man;
    private Button btn_guess;
    private int[] hangmanImages;
    private final int numberOfChances = 6;
    private enum UserInputType {NONE, WORD, LETTER};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joingame);

        etv_name = (EditText) findViewById(R.id.etext_name);
        etv_room = (EditText) findViewById(R.id.etext_room);
        tv_roster = (TextView) findViewById(R.id.text_roster);

        context = this;

        btn_startjoin = (Button) findViewById(R.id.button_startjoin);
        btn_startjoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etv_name.getText().toString();
                String room = etv_room.getText().toString();
                if (!name.isEmpty() && !room.isEmpty()) {
                    InputMethodManager inputManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(etv_name.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    inputManager.hideSoftInputFromWindow(etv_room.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

                    etv_name.setEnabled(false);
                    etv_room.setEnabled(false);
                    btn_startjoin.setEnabled(false);

                    lock = new Object();
                    joinTask = new JoinTask(name, room, lock);
                    joinTask.execute();
                } else {
                    Toast.makeText(context, "Invalid input", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        joinTask.cancel(true);
    }

    public class JoinTask extends AsyncTask<Void, String, String> {
        private final String hubPrefix = "/ndn/edu/ucla/hangman/app";
        private final String host = "localhost";
        private final Face face =  new Face(host);
        private GameSync gs;
        private Object lock;
        private String name;
        private String room;
        private Thread faceThread;
        private boolean start = false;
        private boolean switched = false;
        private final int playersPerGame = 2;

        private ArrayList<Player> players = new ArrayList<>();
        private final String allAvailableLetters = "abcdefghijklmnopqrstuvwxyz";
        private final String allLettersSpaces =
                "a b c d e f g h i j k l m n o p q r s t u v w x y z ";
        private final int guesserBonus = 100;
        private final int drawerBonus = 100;
        private int firstDrawerIndex;
        private int currentDrawerIndex;
        private int currentGuesserIndex;
        private StringBuilder hangmanBuilder;
        private int numberGuessedWrong;
        private UserInputType waitForInput;
        private String remainingString;

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
                gs.onInitialized();
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
                if (gs.roster_.size() < playersPerGame) {
                    publishProgress();
                    pause(1000);
                } else {
                    publishProgress();
                    pause(2000);
                    start = true;
                    publishProgress();
                    break;
                }
            }

            // Begin game logic
            try {
                Thread.sleep(100000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.i("join", "Cleaning up");
            try {
                gs.sendLeaveMessage();
                Thread.sleep(10000);
                gs.sync_.shutdown();
                gs.done = true;
                faceThread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return "FAIL";
        }

        @Override
        protected void onProgressUpdate(String... message) {
            if (!start) {
                ArrayList<String> roster = gs.roster_;
                StringBuilder sb = new StringBuilder("");
                for (int i = 0; i < roster.size(); i++) {
                    String playerName = roster.get(i);
                    sb.append(playerName);
                    sb.append("\n");
                }
                tv_roster.setText(sb.toString());
                return;
            }
            if (!switched) {
                setContentView(R.layout.activity_newgame);

                tv_score = (TextView) findViewById(R.id.text_score);
                tv_status = (TextView) findViewById(R.id.text_status);
                tv_letters = (TextView) findViewById(R.id.text_letters);
                tv_remain = (TextView) findViewById(R.id.text_remain);
                img_man = (ImageView) findViewById(R.id.image_man);
                btn_guess = (Button) findViewById(R.id.button_guess);

                hangmanImages = new int[numberOfChances + 1];
                hangmanImages[0] = R.drawable.hangman_0;
                hangmanImages[1] = R.drawable.hangman_1;
                hangmanImages[2] = R.drawable.hangman_2;
                hangmanImages[3] = R.drawable.hangman_3;
                hangmanImages[4] = R.drawable.hangman_4;
                hangmanImages[5] = R.drawable.hangman_5;
                hangmanImages[6] = R.drawable.hangman_6;

                tv_status.setMovementMethod(new ScrollingMovementMethod());

                switched = true;
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                sb.append(player.name + ": " + player.score + "\n");
            }

            tv_score.setText(sb.toString());

            if (message.length != 0)
                tv_status.append(message[0] + "\n");

            tv_letters.setText(hangmanBuilder.toString());

            img_man.setImageResource(hangmanImages[numberGuessedWrong]);

            tv_remain.setText(remainingString);

            if (waitForInput == UserInputType.WORD) {
                btn_guess.setEnabled(true);
                btn_guess.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Hangman Input");
                        builder.setMessage("Choose a 4- to 12-letter long word");
                        final EditText eText = new EditText(context);
                        eText.setInputType(InputType.TYPE_CLASS_TEXT);
                        eText.setTextColor(Color.rgb(0, 0, 0));
                        builder.setView(eText);
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean valid = true;
                                String str = eText.getText().toString().toLowerCase();
                                if (str.length() < 4 || str.length() > 12) {
                                    valid = false;
                                } else {
                                    for (int i = 0; i < str.length(); i++) {
                                        char c = str.charAt(i);
                                        if (c < 'a' || c > 'z') {
                                            valid = false;
                                            break;
                                        }
                                    }
                                }

                                if (valid) {
                                    ((LocalPlayer) players.get(currentDrawerIndex)).inputWord = str;
                                    btn_guess.setEnabled(false);
                                    waitForInput = UserInputType.NONE;
                                    synchronized (lock) {
                                        lock.notify();
                                    }
                                } else {
                                    Toast.makeText(context, "Invalid input",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });

                        builder.show();
                    }
                });
            } else if (waitForInput == UserInputType.LETTER) {
                btn_guess.setEnabled(true);
                btn_guess.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Hangman Input");
                        builder.setMessage("Choose a letter (a-z)");
                        final EditText eText = new EditText(context);
                        eText.setInputType(InputType.TYPE_CLASS_TEXT);
                        eText.setTextColor(Color.rgb(0, 0, 0));
                        builder.setView(eText);
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean valid = true;
                                String str = eText.getText().toString().toLowerCase();
                                if (str.length() != 1) {
                                    valid = false;
                                } else {
                                    char c = str.charAt(0);
                                    if (c < 'a' || c > 'z') {
                                        valid = false;
                                    }
                                }

                                if (valid) {
                                    ((LocalPlayer) players.get(currentGuesserIndex)).inputLetter =
                                            str.charAt(0);
                                    btn_guess.setEnabled(false);
                                    waitForInput = UserInputType.NONE;
                                    synchronized (lock) {
                                        lock.notify();
                                    }
                                } else {
                                    Toast.makeText(context, "Invalid input",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });

                        builder.show();
                    }
                });
            }
        }

        private void pause(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private class LocalPlayer extends Player {
            String inputWord;
            char inputLetter;

            public LocalPlayer(String name) {
                this.name = name;
            }

            @Override
            String chooseWord() {
                waitForInput = UserInputType.WORD;
                publishProgress();
                try {
                    synchronized (lock) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return inputWord;
            }

            @Override
            char chooseLetter(String letters) {
                waitForInput = UserInputType.LETTER;
                publishProgress();
                try {
                    synchronized (lock) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return inputLetter;
            }
        }
    }
}
