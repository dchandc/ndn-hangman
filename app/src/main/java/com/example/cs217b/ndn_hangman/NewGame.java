package com.example.cs217b.ndn_hangman;

import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Dennis on 5/2/2015.
 */
public class NewGame {
    private ArrayList<Player> players;
    private static final int numberOfChances = 6;
    private int numberOfPlayers;
    private String allAvailableLetters = "abcdefghijklmnopqrstuvwxyz";

    public NewGame(int numberOfPlayers) throws IllegalArgumentException {
        if (numberOfPlayers < 2 || numberOfPlayers > 4)
            throw new IllegalArgumentException("Number of players must be between 2 and 4");

        this.numberOfPlayers = numberOfPlayers;
    }

    public GameState init() {
        players = new ArrayList<Player>();
        /*
        for (int i = 0; i < numberOfPlayers; i++) {
            players.add(new AIPlayer("AIPlayer" + i));
        }
        */
        players.add(new AIPlayer("Bill"));
        players.add(new AIPlayer("Bob"));
        players.add(new AIPlayer("Jack"));
        players.add(new AIPlayer("Jill"));

        Random rand = new Random();
        int firstDrawerIndex = rand.nextInt(players.size());
        int currentDrawerIndex = firstDrawerIndex;

        GameState gs = new GameState(players, 0, currentDrawerIndex, 0, allAvailableLetters, null);
        return gs;
    }

    public GameState progress() {
        Random rand = new Random();
        int firstDrawerIndex = rand.nextInt(players.size());
        int currentDrawerIndex = firstDrawerIndex;
        int currentGuesserIndex;

        while (true) {
            String availableLetters = "abcdefghijklmnopqrstuvwxyz";
            int numberGuessedWrong = 0;

            Player drawer = players.get(currentDrawerIndex);
            String word = drawer.chooseWord();
            Log.i("game", drawer.name + " chose the word '" + word + "'");

            currentGuesserIndex = currentDrawerIndex;
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
            }

            currentDrawerIndex = (currentDrawerIndex + 1) % players.size();
            if (currentDrawerIndex == firstDrawerIndex)
                break;
        }

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Log.i("score", player.name + ": " + player.score);
        }

        return null;
    }
}
