package com.mystery_of_orient_express.match3_engine.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.mystery_of_orient_express.match3_engine.controller.DisappearAnimation;
import com.mystery_of_orient_express.match3_engine.controller.FallAnimation;
import com.mystery_of_orient_express.match3_engine.controller.GameInputProcessor;
import com.mystery_of_orient_express.match3_engine.controller.IAnimationHandler;
import com.mystery_of_orient_express.match3_engine.controller.IGameFieldInputController;
import com.mystery_of_orient_express.match3_engine.controller.SwapAnimation;
import com.mystery_of_orient_express.match3_engine.model.CellObject;
import com.mystery_of_orient_express.match3_engine.model.Field;
import com.mystery_of_orient_express.match3_engine.model.GameObject;
import com.mystery_of_orient_express.match3_engine.model.IAnimation;
import com.mystery_of_orient_express.match3_engine.model.IGameControl;
import com.mystery_of_orient_express.match3_engine.model.IGameObjectFactory;

public class GameFieldControl implements IGameControl, IAnimationHandler, IGameFieldInputController, IGameObjectFactory {
    private static final String[] gemNames = { "gem_yellow.png", "gem_red.png", "gem_green.png", "gem_blue.png", "gem_purple.png", "gem_white.png" };
    private static final String[] soundNames = { "knock.wav", "mystery3_3.wav", "mystery3_4.wav" };

    private ScoreControl scoreControl;
    private Field field;
    private List<GameObject> objects = new ArrayList<>();
    private List<IAnimation> animations = new ArrayList<>();
    private GameInputProcessor gameInputProcessor;
    private int boardSize;
    private static final float cellSize = 1f;
    private static final float gemSize = 1f;
    private boolean canMove = false;
    private boolean needKnock = false;

    public GameFieldControl(ScoreControl scoreControl, int fieldSize) {
        this.scoreControl = scoreControl;
        this.gameInputProcessor = new GameInputProcessor(this);
        this.field = new Field(this, this.scoreControl, fieldSize, gemNames.length);
    }

    @Override
    public void load(AssetManager assetManager) {
        assetManager.load("field.png", Texture.class);
        for (String name : gemNames) assetManager.load(name, Texture.class);
        for (String name : soundNames) assetManager.load(name, Sound.class);
    }

    @Override
    public void resize(int x, int y, int width, int height) {
        this.boardSize = width;
        int fieldSize = this.field.getSize();
        int calculatedCellSize = (int) (0.96f * width / fieldSize);
        int boardOffset = (width - calculatedCellSize * fieldSize) / 2;
        this.gameInputProcessor.resize(calculatedCellSize, x, y, boardOffset);
    }

    @Override
    public void render(float delta, SpriteBatch batch, AssetManager assetManager) {
        // 1. Обновление логики
        for (int i = 0; i < animations.size(); i++) {
            animations.get(i).update(delta);
        }

        updateFieldState(assetManager);

        // 2. Отрисовка (batch.begin() уже вызван в GameScreen)
        if (assetManager.isLoaded("field.png")) {
            Texture boardImage = assetManager.get("field.png");
            batch.draw(boardImage, gameInputProcessor.getOffset(true), gameInputProcessor.getOffset(false), boardSize, boardSize);
        }

        for (GameObject obj : objects) {
            drawObject(batch, assetManager, obj);
        }
    }

    private void drawImage(SpriteBatch batch, Texture image, float x, float y, float width, float height) {
        batch.draw(image, gameInputProcessor.indexToCoord(x, true), gameInputProcessor.indexToCoord(y, false),
                gameInputProcessor.sizeToCoord(width), gameInputProcessor.sizeToCoord(height));
    }

    public void drawObject(SpriteBatch batch, AssetManager assetManager, GameObject obj) {
        if (obj.kind != -1) {
            if (!assetManager.isLoaded(gemNames[obj.kind])) return;
            
            Texture image = assetManager.get(gemNames[obj.kind], Texture.class);
            float minX = obj.posX - 0.5f * obj.sizeX;
            float minY = obj.posY - 0.5f * obj.sizeY;
            
            if (obj.effect != CellObject.Effects.NONE) {
                float offset = 0.08f;
                if (obj.effect == CellObject.Effects.AREA || obj.effect == CellObject.Effects.H_RAY) {
                    drawImage(batch, image, minX - offset, minY, obj.sizeX, obj.sizeY);
                    drawImage(batch, image, minX + offset, minY, obj.sizeX, obj.sizeY);
                }
                if (obj.effect == CellObject.Effects.AREA || obj.effect == CellObject.Effects.V_RAY) {
                    drawImage(batch, image, minX, minY - offset, obj.sizeX, obj.sizeY);
                    drawImage(batch, image, minX, minY + offset, obj.sizeX, obj.sizeY);
                }
            }
            drawImage(batch, image, minX, minY, obj.sizeX, obj.sizeY);
        } else if (obj.effect == CellObject.Effects.KIND) {
            float sX = 0.5f * obj.sizeX;
            float sY = 0.5f * obj.sizeY;
            double step = 2 * Math.PI / gemNames.length;
            for (int i = 0; i < gemNames.length; i++) {
                if (assetManager.isLoaded(gemNames[i])) {
                    Texture img = assetManager.get(gemNames[i], Texture.class);
                    drawImage(batch, img, obj.posX - 0.25f * sX + 0.3f * sX * (float)Math.sin(i * step),
                            obj.posY - 0.25f * sY + 0.3f * sY * (float)Math.cos(i * step), sX, sY);
                }
            }
        }
    }

    @Override public InputProcessor getInputProcessor() { return gameInputProcessor; }
    @Override public boolean canMove() { return canMove; }
    @Override public boolean checkIndex(int index) { return field.checkIndex(index); }

    @Override
    public GameObject newGem(int i, int j) {
        int kind = (int) (Math.random() * gemNames.length);
        GameObject newGem = new GameObject(kind, i, j, gemSize, gemSize);
        objects.add(newGem);
        return newGem;
    }

    public void updateFieldState(AssetManager assetManager) {
        if (!animations.isEmpty()) return;

        Set<CellObject> gemsToFall = field.findGemsToFall();
        if (!gemsToFall.isEmpty()) {
            needKnock = true;
            animations.add(new FallAnimation(gemsToFall, cellSize, this));
            return;
        }

        if (needKnock) {
            needKnock = false;
            if (assetManager.isLoaded(soundNames[0])) assetManager.get(soundNames[0], Sound.class).play();
        }

        Set<CellObject> matchedAll = field.findMatchedGems();
        if (!matchedAll.isEmpty()) {
            animations.add(new DisappearAnimation(matchedAll, gemSize, this));
            int combo = scoreControl.getCombo();
            int soundIdx = Math.min(combo > 0 ? combo - 1 : 0, 2);
            if (assetManager.isLoaded(soundNames[soundIdx])) assetManager.get(soundNames[soundIdx], Sound.class).play(0.3f);
            return;
        }

        if (field.testNoMoves()) {
            animations.add(new DisappearAnimation(field.getAllGems(), gemSize, this));
            return;
        }
        canMove = true;
    }

    @Override
    public void swap(int i1, int j1, int i2, int j2) {
        boolean success = field.testSwap(i1, j1, i2, j2);
        animations.add(new SwapAnimation((GameObject)field.getGem(i1, j1), (GameObject)field.getGem(i2, j2), !success, this));
        if (success) canMove = false;
    }

    @Override
    public void onComplete(IAnimation animation) {
        if (animation instanceof DisappearAnimation) {
            DisappearAnimation da = (DisappearAnimation) animation;
            Set<CellObject> chained = field.removeGems(da.gems);
            if (!chained.isEmpty()) {
                animations.add(new DisappearAnimation(chained, gemSize, this));
            }
            for (CellObject gem : da.gems) {
                objects.remove((GameObject)gem);
            }
        }
        animations.remove(animation);
    }
}

