package com.example.cs217b.ndn_hangman;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
    TextView tv_score, tv_status, tv_letters;
    ImageView img_man;
    Button btn_guess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newgame);

        tv_score = (TextView) findViewById(R.id.text_score);
        tv_status = (TextView) findViewById(R.id.text_status);
        tv_letters = (TextView) findViewById(R.id.text_letters);
        img_man = (ImageView) findViewById(R.id.image_man);
        btn_guess = (Button) findViewById(R.id.button_guess);

        tv_status.setMovementMethod(new ScrollingMovementMethod());

        if (savedInstanceState == null)
            new GameTask(2).execute();
    }

    public class GameTask extends AsyncTask<Void, String, ArrayList<Player>> {
        private ArrayList<Player> players;
        private final int numberOfChances = 5;
        private final String allAvailableLetters = "abcdefghijklmnopqrstuvwxyz";
        private final int guesserBonus = 100;
        private final int drawerBonus = 100;
        private int firstDrawerIndex;
        private int currentDrawerIndex;
        private int currentGuesserIndex;
        private StringBuilder hangmanBuilder;

        public GameTask(int numberOfPlayers) {
            if (numberOfPlayers < 2 || numberOfPlayers > 4)
                throw new IllegalArgumentException("Number of players must be between 2 and 4");

            players = new ArrayList<Player>();
            for (int i = 0; i < numberOfPlayers; i++) {
                players.add(new AIPlayer("AIPlayer" + i));
            }

            hangmanBuilder = new StringBuilder("");

            Random rand = new Random();
            firstDrawerIndex = rand.nextInt(players.size());
            currentDrawerIndex = firstDrawerIndex;
            currentGuesserIndex = firstDrawerIndex;
        }

        @Override
        protected ArrayList<Player> doInBackground(Void... ignored) {
            do {
                // Begin new round
                String availableLetters = allAvailableLetters;
                int numberGuessedWrong = 0;

                Player drawer = players.get(currentDrawerIndex);
                pause(1000);
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
                if (i == currentDrawerIndex)
                    sb.append("? ");
                else if (i == currentGuesserIndex)
                    sb.append("> ");
                else
                    sb.append("   ");

                Player player = players.get(i);
                sb.append(player.name + ": " + player.score + "\n");
            }

            tv_score.setText(sb.toString());

            if (message.length != 0)
                tv_status.append(message[0] + "\n");

            tv_letters.setText(hangmanBuilder.toString());
        }

        @Override
        protected void onPostExecute(ArrayList<Player> winners) {
            Log.i("game", "GameTask complete");
            StringBuilder sb = new StringBuilder("");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0)
                    sb.append(", ");

                Player player = winners.get(i);
                sb.append(player.name);
            }

            if (winners.size() > 1)
                sb.append(" win!");
            else
                sb.append(" wins!");

            Toast.makeText(getApplicationContext(), sb.toString(), Toast.LENGTH_LONG).show();
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
