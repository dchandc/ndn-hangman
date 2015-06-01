package com.example.cs217b.ndn_hangman;

public class NetPlayer extends Player{
    String inputWord;
    char inputLetter;
    boolean isThinking;

    public NetPlayer(String name, long sessionNo, String sid, boolean isLocal) {
        this.name = name;
        this.score = 0;
        this.isLocal = isLocal;
        this.sid = sid;
        this.sessionNo = sessionNo;

        inputWord = "";
        inputLetter = '\0';
        isThinking = false;
    }

    @Override
    public String chooseWord() {
        if (!isThinking) {
            String temp = inputWord;
            inputWord = "";
            return temp;
        }

        return "";
    }

    @Override
    public char chooseLetter(String ignored) {
        if (!isThinking) {
            char temp = inputLetter;
            inputLetter = '\0';
            return temp;
        }

        return '\0';
    }

    @Override
    public void setTurn(boolean turnState) {
        isThinking = turnState;
    }

    @Override
    public boolean isThinking() {
        return isThinking;
    }

    @Override
    public void think(String word) {
        inputWord = word;
        isThinking = false;
    }

    @Override
    public void think(char letter) {
        inputLetter = letter;
        isThinking = false;
    }
}
