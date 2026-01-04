package com.mystery_of_orient_express.match3_engine.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class SplashScreen extends ScreenAdapter implements IScreen {
    private int screenWidth, screenHeight;
    private SpriteBatch batch;
    private AssetManager assetManager;

    public SplashScreen(SpriteBatch batch) { this.batch = batch; }

    @Override
    public void resize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    @Override
    public void load(AssetManager assetManager) {
        this.assetManager = assetManager;
        assetManager.load("splash.png", Texture.class);
        assetManager.load("loaded.png", Texture.class);
        assetManager.finishLoading();
    }

    @Override
    public void render(float delta) {
        float progress = assetManager.getProgress();
        Texture splash = assetManager.get("splash.png", Texture.class);
        Texture loaded = assetManager.get("loaded.png", Texture.class);

        batch.begin();
        batch.draw(splash, 0, 0, screenWidth, screenHeight);
        // Рисуем прогресс-бар снизу
        batch.draw(loaded, 0, 0, screenWidth * progress, screenHeight * 0.05f, 
                   0, 0, (int)(loaded.getWidth() * progress), loaded.getHeight(), false, false);
        batch.end();
    }

    @Override
    public InputProcessor getInputProcessor() { return null; }
}

