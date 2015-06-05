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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * This class represents a game between a local human player and multiple AI players.
 */
public class AIGameActivity extends ActionBarActivity {
    private final int numberOfPlayers = 4;
    private final int numberOfChances = 6;
    private Context context;
    private enum UserInputType {NONE, WORD, LETTER}
    private GameTask gameTask;
    // activity_newgame layout
    private TextView tv_score;
    private TextView tv_status;
    private TextView tv_letters;
    private TextView tv_remain;
    private ImageView img_man;
    private Button btn_guess;
    private int[] hangmanImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newgame);

        context = this;

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

        if (savedInstanceState == null) {
            gameTask = new GameTask(numberOfPlayers);
            gameTask.execute();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameTask != null)
            gameTask.cancel(true);
    }

    /**
     * This class allows game logic to run in the background while updating the UI.
     */
    public class GameTask extends AsyncTask<Void, String, Void> {
        private final String allAvailableLetters = "abcdefghijklmnopqrstuvwxyz";
        private final String allLettersSpaces =
                "a b c d e f g h i j k l m n o p q r s t u v w x y z ";
        private final int guesserBonus = 200;
        private final int drawerBonus = 400;
        private final Object lock = new Object();
        private ArrayList<Player> players;
        private int firstDrawerIndex;
        private int currentDrawerIndex;
        private int currentGuesserIndex;
        private int numberGuessedWrong;
        private UserInputType waitForInput;
        private String remainingString;
        private StringBuilder hangmanBuilder;

        public GameTask(int numberOfPlayers) {
            if (numberOfPlayers < 2 || numberOfPlayers > 4)
                throw new IllegalArgumentException("Number of players must be between 2 and 4");

            // Populate list with one local human player and at least one AI player.
            players = new ArrayList<>();
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
        protected Void doInBackground(Void... ignored) {
            int roundNum = 0;
            int guessNum = 0;
            do {
                roundNum++;
                guessNum = 0;

                // Begin new round.
                pause(1000);
                String availableLetters = allAvailableLetters;
                numberGuessedWrong = 0;
                hangmanBuilder = new StringBuilder("");
                remainingString = "";

                // Prompt the drawer to choose a word.
                Player drawer = players.get(currentDrawerIndex);
                Log.i("ai", "drawer=" + drawer.name + " [" + currentDrawerIndex + "]");
                publishProgress(drawer.name + "'s turn to choose a word.");
                drawer.setTurn(true);
                while(drawer.isThinking()) {
                    pause(100);
                }
                String chosenWord = drawer.inputWord();
                String remainingWord = chosenWord;
                char[] tmpArray = new char[chosenWord.length()];
                Arrays.fill(tmpArray, '_');
                hangmanBuilder = new StringBuilder(new String(tmpArray));
                remainingString = allLettersSpaces;
                Log.i("ai", "word=" + chosenWord + " (" + chosenWord.length() + ")");
                publishProgress(drawer.name + " chose a " + chosenWord.length() + "-letter word.");

                // Loop through remaining players for guesses until word is guessed or chances
                // are used up.
                while (numberGuessedWrong < numberOfChances && !isCancelled()) {
                    currentGuesserIndex = (currentGuesserIndex + 1) % players.size();
                    if (currentGuesserIndex == currentDrawerIndex)
                        continue;

                    guessNum++;
                    Log.i("ai", "roundNum=" + roundNum + " guessNum=" + guessNum);

                    // Prompt the guesser to choose a letter.
                    Player guesser = players.get(currentGuesserIndex);
                    pause(500);
                    Log.i("ai", "guesser=" + guesser.name + " [" + currentGuesserIndex + "]");
                    publishProgress(guesser.name + "'s turn to guess.");
                    guesser.setTurn(true);
                    while(guesser.isThinking()) {
                        pause(100);
                    }
                    char guess = guesser.inputLetter(availableLetters);
                    String guessString = Character.valueOf(guess).toString();
                    availableLetters = availableLetters.replace(guessString, "");
                    remainingString = remainingString.replace(guessString + " ", "");
                    String updatedWord = remainingWord.replace(guessString, "");
                    int count = remainingWord.length() - updatedWord.length();
                    int points = count * 100;
                    guesser.score += points;
                    remainingWord = updatedWord;
                    Log.i("ai", "guess=" + guessString + " count=" + count + " remainder=" +
                            remainingWord);

                    // Update the underscored word by filling in the new letter.
                    int fromIndex = 0;
                    int replaceIndex = chosenWord.indexOf(guess, fromIndex);
                    while (replaceIndex != -1) {
                        hangmanBuilder.setCharAt(replaceIndex, guess);
                        fromIndex = replaceIndex + 1;
                        replaceIndex = chosenWord.indexOf(guess, fromIndex);
                    }

                    // Update the game status and check for the end to this round.
                    if (count == 0) {
                        numberGuessedWrong++;
                        publishProgress(guesser.name + " incorrectly guessed the letter '" +
                                guessString + "'.");

                        if (numberGuessedWrong >= numberOfChances) {
                            drawer.score += drawerBonus;
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

            } while (currentDrawerIndex != firstDrawerIndex && !isCancelled());

            pause(1000);

            // Reset screen assets.
            remainingString = "";
            numberGuessedWrong = numberOfChances;
            hangmanBuilder = new StringBuilder("");

            // Determine the winner(s) by score, allowing for ties.
            if (!isCancelled()) {
                int winnerScore = 0;
                ArrayList<Player> winners = new ArrayList<>();
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
            }

            Log.i("ai", "end background thread");
            return null;
        }

        @Override
        protected void onProgressUpdate(String... message) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                String str = player.name + ": " + player.score + "\n";
                sb.append(str);
            }

            tv_score.setText(sb.toString());

            if (message.length != 0)
                tv_status.append(message[0] + "\n");

            tv_letters.setText(hangmanBuilder.toString());

            img_man.setImageResource(hangmanImages[numberGuessedWrong]);

            tv_remain.setText(remainingString);

            // Enable the input button and use the Object lock to signal back to the background
            // thread when input is received.
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

        /**
         * This class represents the local human player. This class is used in place of NetPlayer
         * because an Object lock is more appropriate for notification in local-only games.
         */
        private class LocalPlayer extends Player {
            String inputWord;
            char inputLetter;

            public LocalPlayer(String name) {
                this.name = name;
                this.score = 0;
            }

            @Override
            public String inputWord() {
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
            public char inputLetter(String letters) {
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

            @Override
            public void setTurn(boolean turnState) {}

            @Override
            public boolean isThinking() {
                return false;
            }

            @Override
            public void think(String word) {}

            @Override
            public void think(char letter) {}
        }
    }
}
