package com.example.cs217b.ndn_hangman;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Dennis on 5/2/2015.
 */
public class NewGameActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newgame);

        try {
            NewGame game = new NewGame(4);
            GameState gs = game.init();
            if (gs == null)
                throw new NullPointerException("NewGame failed to init");

            updateScore(gs.players, gs.currentDrawerIndex, gs.currentGuesserIndex);

            /*
            while (gs.state != GameState.Stage.COMPLETE) {
                gs = game.progress();
                // Update UI here
            }
            */
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

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
}
