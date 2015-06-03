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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

//TODO: Add comments and clean up
//TODO: Make sure leave works
//TODO: Add more game info to screen (round, guess, etc.)
//TODO: Fix AIGame endgame
//TODO: Add roster checks and security
//TODO: Add dictionary

public class JoinGameActivity extends ActionBarActivity {
    private Context context;
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

                    joinTask = new JoinTask(name, room);
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
        if (joinTask != null)
            joinTask.cancel(true);
    }

    public class JoinTask extends AsyncTask<Void, String, Void> {
        private final String hubPrefix = "/ndn/edu/ucla/hangman/app";
        private final String host = "spurs.cs.ucla.edu";
        private final Face face =  new Face(host);
        private GameSync gs;
        private String name;
        private String room;
        private Thread faceThread;
        private boolean start = false;
        private boolean switched = false;
        private boolean end = false;
        private final int playersPerGame = 2;

        private final String allLettersSpaces =
                "a b c d e f g h i j k l m n o p q r s t u v w x y z ";
        private final int guesserBonus = 200;
        private final int drawerBonus = 400;
        private int firstDrawerIndex;
        private int currentDrawerIndex;
        private int currentGuesserIndex;
        private StringBuilder hangmanBuilder;
        private int numberGuessedWrong;
        private UserInputType waitForInput;
        private String remainingString;

        public JoinTask(String name, String room) {
            this.name = name;
            this.room = room;
        }

        @Override
        protected Void doInBackground(Void... ignored) {
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
                    face.shutdown();
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
                    publishProgress("Initializing game...");
                    break;
                }
            }

            // Begin game logic
            firstDrawerIndex = 0;
            currentDrawerIndex = firstDrawerIndex;
            currentGuesserIndex = firstDrawerIndex;

            Collections.sort(gs.roster_, new Comparator<Player>() {
                @Override
                public int compare(Player lhs, Player rhs) {
                    return lhs.sid.compareTo(rhs.sid);
                }
            });

            gs.roundNum_ = 0;
            do {
                gs.roundNum_++;
                gs.guessNum_ = 0;

                // Begin new round
                pause(1000);
                String chosenWord;
                numberGuessedWrong = 0;
                hangmanBuilder = new StringBuilder("");
                remainingString = "";

                Player drawer = gs.roster_.get(currentDrawerIndex);
                publishProgress(drawer.name + "'s turn to choose a word.");
                drawer.setTurn(true);
                if (drawer.isLocal) {
                    waitForInput = UserInputType.WORD;
                    publishProgress();
                    while (drawer.isThinking() && !isCancelled()) {
                        pause(250);
                    }
                    chosenWord = drawer.inputWord();
                    char[] tmpArray = new char[chosenWord.length()];
                    Arrays.fill(tmpArray, '_');
                    String underscoredWord = new String(tmpArray);
                    hangmanBuilder = new StringBuilder(underscoredWord);
                    try {
                        gs.sendEvalMessage(gs.roundNum_ + "-" + gs.guessNum_ + "-" +
                                hangmanBuilder.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    while (drawer.isThinking() && !isCancelled()) {
                        pause(250);
                    }
                    chosenWord = drawer.inputWord();
                    hangmanBuilder = new StringBuilder(chosenWord);
                }
                remainingString = allLettersSpaces;
                publishProgress(drawer.name + " chose a " + hangmanBuilder.length() +
                        "-letter word.");

                while (numberGuessedWrong < numberOfChances && !isCancelled()) {
                    int count;
                    String guessString;
                    char guess;

                    currentGuesserIndex = (currentGuesserIndex + 1) % gs.roster_.size();
                    if (currentGuesserIndex == currentDrawerIndex)
                        continue;

                    gs.guessNum_++;
                    Log.i("join", "roundNum=" + gs.roundNum_ + " guessNum=" + gs.guessNum_);

                    Player guesser = gs.roster_.get(currentGuesserIndex);
                    pause(500);
                    publishProgress(guesser.name + "'s turn to guess.");
                    Log.i("join", "guesser=(" + guesser.name + ", " + currentGuesserIndex + ")");
                    guesser.setTurn(true);
                    if (guesser.isLocal) {
                        waitForInput = UserInputType.LETTER;
                        publishProgress();
                        while (guesser.isThinking() && !isCancelled()) {
                            pause(250);
                        }
                        guess = guesser.inputLetter(null);
                        guessString = (new Character(guess)).toString();
                        try {
                            gs.sendGuessMessage(gs.roundNum_ + "-" + gs.guessNum_ +
                                    "-" + guessString);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        while (guesser.isThinking() && !isCancelled()) {
                            pause(250);
                        }
                        guess = guesser.inputLetter(null);
                        guessString = (new Character(guess)).toString();
                    }
                    remainingString = remainingString.replace(guessString + " ", "");
                    Log.i("join", "guess=" + guessString);

                    Log.i("join", "drawer=(" + drawer.name + ", " + currentDrawerIndex + ")");
                    drawer.setTurn(true);
                    if (drawer.isLocal) {
                        String prevWord = hangmanBuilder.toString();
                        int fromIndex = 0;
                        int replaceIndex = chosenWord.indexOf(guess, fromIndex);
                        while (replaceIndex != -1) {
                            hangmanBuilder.setCharAt(replaceIndex, guess);
                            fromIndex = replaceIndex + 1;
                            replaceIndex = chosenWord.indexOf(guess, fromIndex);
                        }
                        String currWord = hangmanBuilder.toString();
                        count = currWord.replace("_", "").length() -
                                prevWord.replace("_", "").length();
                        try {
                            gs.sendEvalMessage(gs.roundNum_ + "-" + gs.guessNum_ + "-" +
                                    hangmanBuilder.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        while (drawer.isThinking() && !isCancelled()) {
                            pause(250);
                        }
                        String currWord = drawer.inputWord();
                        String prevWord = hangmanBuilder.toString();
                        count = currWord.replace("_", "").length() -
                                prevWord.replace("_", "").length();
                        hangmanBuilder = new StringBuilder(currWord);
                    }

                    int points = count * 100;
                    guesser.score += points;

                    if (count == 0) {
                        numberGuessedWrong++;
                        publishProgress(guesser.name + " incorrectly guessed the letter '" +
                                guessString + "'.");

                        if (numberGuessedWrong >= numberOfChances) {
                            drawer.score += drawerBonus;
                            Log.i("game", "Drawer (+" + drawerBonus + "): " + drawer.score);
                            publishProgress(drawer.name + " scored " + drawerBonus + " points " +
                                    "for completing Hangman!");
                            break;
                        }
                    } else {
                        publishProgress(guesser.name + " correctly guessed the letter '" +
                                guessString + "' and scored " + points + " points.");
                    }

                    if (hangmanBuilder.toString().indexOf('_') == -1) {
                        guesser.score += guesserBonus;
                        publishProgress(guesser.name + " completed the word and scored " +
                                guesserBonus + " points!");
                        break;
                    }
                }

                pause(1000);

                currentDrawerIndex = (currentDrawerIndex + 1) % gs.roster_.size();

            } while (currentDrawerIndex != firstDrawerIndex && !isCancelled());

            pause(1000);

            end = true;
            remainingString = "";
            numberGuessedWrong = numberOfChances;
            hangmanBuilder = new StringBuilder("");

            ArrayList<Player> winners = new ArrayList<>();
            int winnerScore = 0;
            for (int i = 0; i < gs.roster_.size(); i++) {
                Player player = gs.roster_.get(i);
                if (player.score > winnerScore) {
                    winnerScore = player.score;
                }
            }

            for (int i = 0; i < gs.roster_.size(); i++) {
                Player player = gs.roster_.get(i);
                if (player.score == winnerScore)
                    winners.add(player);
            }

            StringBuilder sb = new StringBuilder("");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0)
                    sb.append(", ");

                Player player = winners.get(i);
                sb.append(player.name);
            }

            if (winners.size() > 1)
                sb.append(" tie!");
            else
                sb.append(" wins!");

            publishProgress(sb.toString());

            pause(10000);

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

            return null;
        }

        @Override
        protected void onProgressUpdate(String... message) {
            if (!start) {
                StringBuilder sb = new StringBuilder("");
                for (int i = 0; i < gs.roster_.size(); i++) {
                    String playerName = gs.roster_.get(i).name;
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
            for (int i = 0; i < gs.roster_.size(); i++) {
                Player player = gs.roster_.get(i);
                sb.append(player.name + ": " + player.score + "\n");
            }

            tv_score.setText(sb.toString());

            if (message.length != 0)
                tv_status.append(message[0] + "\n");

            tv_letters.setText(hangmanBuilder.toString());

            img_man.setImageResource(hangmanImages[numberGuessedWrong]);

            tv_remain.setText(remainingString);

            if (waitForInput == UserInputType.WORD) {
                Log.i("join", "Guess button enabled");
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
                                    gs.roster_.get(currentDrawerIndex).think(str);
                                    btn_guess.setEnabled(false);
                                    waitForInput = UserInputType.NONE;
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
                                    gs.roster_.get(currentGuesserIndex).think(str.charAt(0));
                                    btn_guess.setEnabled(false);
                                    waitForInput = UserInputType.NONE;
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
    }
}