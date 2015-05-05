package com.example.cs217b.ndn_hangman;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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

        if (savedInstanceState == null)
            new GameTask(2).execute();
        /*
        try {
            NewGame game = new NewGame(4);
            GameState gs = game.init();
            if (gs == null)
                throw new NullPointerException("NewGame failed to init");

            TextView tv = (TextView) findViewById(R.id.text_status);
            tv.setText("Initializing game...\nIt is " + gs.players.get(0).name + "'s turn to choose a word.");
            updateScore(gs.players, gs.currentDrawerIndex, gs.currentGuesserIndex);

            while (gs.state != GameState.Stage.COMPLETE) {
                gs = game.progress();
                // Update UI here
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        */
    }

    public class GameTask extends AsyncTask<Void, Integer, ArrayList<Player>> {
        private ArrayList<Player> players;
        private static final int numberOfChances = 6;
        private String allAvailableLetters = "abcdefghijklmnopqrstuvwxyz";
        private int firstDrawerIndex = 0;
        private int currentDrawerIndex;
        private int currentGuesserIndex;

        public GameTask(int numberOfPlayers) {
            if (numberOfPlayers < 2 || numberOfPlayers > 4)
                throw new IllegalArgumentException("Number of players must be between 2 and 4");

            players = new ArrayList<Player>();
            for (int i = 0; i < numberOfPlayers; i++) {
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
                String availableLetters = "abcdefghijklmnopqrstuvwxyz";
                int numberGuessedWrong = 0;

                Player drawer = players.get(currentDrawerIndex);
                String word = drawer.chooseWord();
                Log.i("game", drawer.name + " chose the word '" + word + "'");

                while (numberGuessedWrong < numberOfChances) {
                    currentGuesserIndex = (currentGuesserIndex + 1) % players.size();
                    if (currentGuesserIndex == currentDrawerIndex)
                        continue;

                    Player guesser = players.get(currentGuesserIndex);
                    char guess = guesser.chooseLetter(availableLetters);
                    String guessString = (new Character(guess)).toString();

                    availableLetters = availableLetters.replace(guessString, "");
                    String updatedWord = word.replace(guessString, "");
                    int count = word.length() - updatedWord.length();
                    guesser.score += count * 100;
                    word = updatedWord;

                    Log.i("game", guesser.name + " guessed the letter " + guessString);
                    Log.i("game", guesser.name + " scored " + (count * 100) + " points");
                    Log.i("game", "Available letters: " + availableLetters);
                    Log.i("game", "Word: " + word);

                    if (count == 0)
                        numberGuessedWrong++;

                    publishProgress();
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
        protected void onProgressUpdate(Integer... progress) {
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

    }
    /*
    private void updateScore(ArrayList<Player> players, int currentDrawerIndex, int currentGuesserIndex) {
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
        TextView tv = (TextView) findViewById(R.id.text_score);
        tv.setText(sb);
    }
    */

}
