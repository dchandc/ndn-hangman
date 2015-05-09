package com.example.cs217b.ndn_hangman;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

/**
 * Created by Dennis on 5/2/2015.
 */
public class NewGameActivity extends ActionBarActivity {
    private TextView tv_score, tv_status, tv_letters;
    private ImageView img_man;
    private Button btn_guess;
    private int[] hangmanImages;
    private final int numberOfChances = 6;
    private GameTask gameTask;
    private Object lock;
    private enum UserInputType {NONE, WORD, LETTER};
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newgame);

        context = this;

        tv_score = (TextView) findViewById(R.id.text_score);
        tv_status = (TextView) findViewById(R.id.text_status);
        tv_letters = (TextView) findViewById(R.id.text_letters);
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

        if (savedInstanceState == null) {
            lock = new Object();
            gameTask = new GameTask(4, lock);
            gameTask.execute();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gameTask.cancel(true);
    }

    public class GameTask extends AsyncTask<Void, String, ArrayList<Player>> {
        private ArrayList<Player> players;
        private final String allAvailableLetters = "abcdefghijklmnopqrstuvwxyz";
        private final int guesserBonus = 100;
        private final int drawerBonus = 100;
        private int firstDrawerIndex;
        private int currentDrawerIndex;
        private int currentGuesserIndex;
        private StringBuilder hangmanBuilder;
        private int numberGuessedWrong;
        private Object lock;
        private UserInputType waitForInput;

        public GameTask(int numberOfPlayers, Object lock) {
            if (numberOfPlayers < 2 || numberOfPlayers > 4)
                throw new IllegalArgumentException("Number of players must be between 2 and 4");

            this.lock = lock;

            players = new ArrayList<Player>();
            players.add(new LocalPlayer("LocalPlayer"));
            for (int i = 0; i < numberOfPlayers - 1; i++) {
                players.add(new AIPlayer("AIPlayer" + i));
            }

            Random rand = new Random();
            firstDrawerIndex = rand.nextInt(players.size());
            currentDrawerIndex = firstDrawerIndex;
            currentGuesserIndex = firstDrawerIndex;
        }

        @Override
        protected ArrayList<Player> doInBackground(Void... ignored) {
            do {
                // Begin new round
                pause(1000);
                String availableLetters = allAvailableLetters;
                numberGuessedWrong = 0;
                hangmanBuilder = new StringBuilder("");

                Player drawer = players.get(currentDrawerIndex);
                publishProgress(drawer.name + "'s turn to choose a word.");

                String chosenWord = drawer.chooseWord();
                String remainingWord = chosenWord;
                char[] tmpArray = new char[chosenWord.length()];
                Arrays.fill(tmpArray, '_');
                hangmanBuilder = new StringBuilder(new String(tmpArray));
                Log.i("game", drawer.name + " chose the word '" + chosenWord + "'");
                publishProgress(drawer.name + " chose a " + chosenWord.length() + "-letter word.");

                while (numberGuessedWrong < numberOfChances) {
                    currentGuesserIndex = (currentGuesserIndex + 1) % players.size();
                    if (currentGuesserIndex == currentDrawerIndex)
                        continue;

                    Player guesser = players.get(currentGuesserIndex);
                    pause(500);
                    publishProgress(guesser.name + "'s turn to guess.");

                    char guess = guesser.chooseLetter(availableLetters);
                    String guessString = (new Character(guess)).toString();

                    availableLetters = availableLetters.replace(guessString, "");
                    String updatedWord = remainingWord.replace(guessString, "");
                    int count = remainingWord.length() - updatedWord.length();
                    int points = count * 100;
                    guesser.score += points;
                    remainingWord = updatedWord;

                    int fromIndex = 0;
                    int replaceIndex = chosenWord.indexOf(guess, fromIndex);
                    while (replaceIndex != -1) {
                        hangmanBuilder.setCharAt(replaceIndex, guess);
                        fromIndex = replaceIndex + 1;
                        replaceIndex = chosenWord.indexOf(guess, fromIndex);
                    }

                    Log.i("game", guesser.name + " guessed the letter " + guessString);
                    Log.i("game", guesser.name + " scored " + (count * 100) + " points");
                    Log.i("game", "Available letters: " + availableLetters);
                    Log.i("game", "Word: " + remainingWord);

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

                    if (remainingWord.length() == 0) {
                        guesser.score += guesserBonus;
                        publishProgress(guesser.name + " completed the word and scored " +
                                guesserBonus + " points!");
                        break;
                    }
                }

                pause(1000);

                currentDrawerIndex = (currentDrawerIndex + 1) % players.size();

            } while (currentDrawerIndex != firstDrawerIndex);

            int winnerScore = 0;
            ArrayList<Player> winners = new ArrayList<Player>();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                if (player.score > winnerScore) {
                    winnerScore = player.score;
                }
            }

            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                if (player.score == winnerScore)
                    winners.add(player);
            }

            return winners;
        }

        @Override
        protected void onProgressUpdate(String... message) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                sb.append(player.name + ": " + player.score);

                if (i == currentDrawerIndex)
                    sb.append(" (?)\n");
                else if (i == currentGuesserIndex)
                    sb.append(" (!)\n");
                else
                    sb.append("\n");
            }

            tv_score.setText(sb.toString());

            if (message.length != 0)
                tv_status.append(message[0] + "\n");

            tv_letters.setText(hangmanBuilder.toString());

            img_man.setImageResource(hangmanImages[numberGuessedWrong]);

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

        @Override
        protected void onPostExecute(ArrayList<Player> winners) {
            Log.i("game", "GameTask complete");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                sb.append(player.name + ": " + player.score + "\n");
            }

            tv_score.setText(sb.toString());

            tv_letters.setText("");

            img_man.setImageResource(hangmanImages[numberOfChances]);

            sb = new StringBuilder("");
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

            tv_status.append(sb.toString());
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
