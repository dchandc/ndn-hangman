package com.example.cs217b.ndn_hangman;

/**
 * This class is the superclass for all players in this game.
 */
public abstract class Player {
    public String name;
    public int score;
    public long sessionNo;
    public String sid;
    public boolean isLocal;
    public abstract String inputWord();
    public abstract char inputLetter(String letters);
    public abstract void setTurn(boolean turnState);
    public abstract boolean isThinking();
    public abstract void think(String word);
    public abstract void think(char letter);
}