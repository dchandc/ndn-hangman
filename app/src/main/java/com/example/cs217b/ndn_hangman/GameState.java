package com.example.cs217b.ndn_hangman;

import java.util.ArrayList;

/**
 * Created by Dennis on 5/2/2015.
 */
public class GameState {
    public ArrayList<Player> players;
    public int numberGuessedWrong;
    public int currentDrawerIndex;
    public int currentGuesserIndex;
    public String availableLetters;
    public String word;
    public enum Stage {INIT, RUNNING, COMPLETE};
    public Stage state;

    public GameState(ArrayList<Player> players, int numberGuessedWrong, int currentDrawerIndex,
                     int currentGuesserIndex, String availableLetters, String word) {
        this.players = players;
        this.numberGuessedWrong = numberGuessedWrong;
        this.currentDrawerIndex = currentDrawerIndex;
        this.currentGuesserIndex = currentGuesserIndex;
        this.availableLetters = availableLetters;
        this.word = word;

        state = Stage.INIT;
    }
}
