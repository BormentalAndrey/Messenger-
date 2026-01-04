package com.mystery_of_orient_express.match3_engine.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.mystery_of_orient_express.match3_engine.model.IGameControl;
import java.util.ArrayList;
import java.util.List;

public class GameScreen extends ScreenAdapter implements IScreen, InputProcessor {
    private int screenWidth, screenHeight, gameFieldSize;
    private SpriteBatch batch;
    private AssetManager assetManager;
    private List<IGameControl> controls;
    private InputProcessor currentProcessor = null;

    public GameScreen(SpriteBatch batch) {
        this.batch = batch;
        this.controls = new ArrayList<>();
        ScoreControl scoreControl = new ScoreControl();
        this.controls.add(new GameFieldControl(scoreControl, 8));
        this.controls.add(scoreControl);
    }

    @Override
    public void load(AssetManager assetManager) {
        this.assetManager = assetManager;
        assetManager.load("video.png", Texture.class);
        for (IGameControl c : controls) c.load(assetManager);
    }

    @Override
    public void resize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.gameFieldSize = Math.min(width, (int) (0.9f * height));
        for (IGameControl c : controls) {
            if (c instanceof ScoreControl) c.resize(0, gameFieldSize, width, height - gameFieldSize);
            else if (c instanceof GameFieldControl) c.resize((width - gameFieldSize) / 2, 0, gameFieldSize, gameFieldSize);
        }
    }

    @Override
    public void render(float delta) {
        // 1. Очистка экрана (обязательно для Android)
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 2. Начало сессии отрисовки
        batch.begin();

        // Отрисовка фонового изображения
        if (assetManager.isLoaded("video.png")) {
            batch.draw(assetManager.get("video.png", Texture.class), 0, 0, screenWidth, screenHeight);
        }

        // Отрисовка всех контроллеров (поле, счет и т.д.)
        for (IGameControl c : controls) {
            c.render(delta, batch, assetManager);
        }

        // 3. Завершение сессии отрисовки
        batch.end();
    }

    @Override
    public InputProcessor getInputProcessor() { return this; }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        if (pointer != 0) return false;
        int ly = screenHeight - y;
        for (IGameControl c : controls) {
            InputProcessor ip = c.getInputProcessor();
            if (ip != null && ip.touchDown(x, ly, pointer, button)) {
                currentProcessor = ip;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        if (currentProcessor != null) {
            currentProcessor.touchUp(x, screenHeight - y, pointer, button);
            currentProcessor = null;
        }
        return true;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        if (currentProcessor != null) currentProcessor.touchDragged(x, screenHeight - y, pointer);
        return true;
    }

    @Override public boolean scrolled(float amountX, float amountY) { return false; }
    @Override public boolean mouseMoved(int x, int y) { return false; }
    @Override public boolean keyDown(int k) { return false; }
    @Override public boolean keyUp(int k) { return false; }
    @Override public boolean keyTyped(char c) { return false; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { 
        currentProcessor = null; 
        return true; 
    }
}

