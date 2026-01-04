package com.mystery_of_orient_express.match3_engine.view;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.mystery_of_orient_express.match3_engine.model.IGameControl;
import com.mystery_of_orient_express.match3_engine.model.IScoreController;

public class ScoreControl implements IGameControl, IScoreController {
    private static final String[] digitNames = { "score_zero.png", "score_one.png", "score_two.png", "score_three.png", "score_four.png", "score_five.png", "score_six.png", "score_seven.png", "score_eight.png", "score_nine.png" };
    private static final String emptyName = "score_empty.png";
    private static final int scoreDigits = 8;
    
    private int score = 0, combo = 0;
    private int scoreX, scoreY, itemWidth, itemHeight;

    @Override
    public void updateCombo(int matches) { this.combo = (matches == 0) ? 0 : this.combo + matches; }

    @Override
    public void updateScore(int s) { this.score += Math.max(1, combo) * s; }

    @Override
    public void load(AssetManager am) {
        am.load(emptyName, Texture.class);
        for (String n : digitNames) am.load(n, Texture.class);
    }

    @Override
    public void resize(int x, int y, int width, int height) {
        this.itemHeight = (int) Math.min(72, 0.8f * height);
        this.itemWidth = (int) (0.75f * itemHeight);
        this.scoreX = x + (width - itemWidth * scoreDigits) / 2;
        this.scoreY = y + (height - itemHeight) / 2;
    }

    @Override
    public void render(float delta, SpriteBatch batch, AssetManager am) {
        int temp = score;
        for (int i = 0; i < scoreDigits; i++) {
            Texture img;
            if (temp > 0 || i == 0) {
                img = am.get(digitNames[temp % 10], Texture.class);
                temp /= 10;
            } else {
                img = am.get(emptyName, Texture.class);
            }
            batch.draw(img, scoreX + itemWidth * (scoreDigits - 1 - i), scoreY, itemWidth, itemHeight);
        }
    }

    @Override
    public InputProcessor getInputProcessor() { return null; }
    public int getCombo() { return combo; }
}

