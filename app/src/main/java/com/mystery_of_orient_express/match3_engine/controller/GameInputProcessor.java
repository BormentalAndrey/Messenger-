package com.mystery_of_orient_express.match3_engine.controller;

import com.badlogic.gdx.InputProcessor;

public class GameInputProcessor implements InputProcessor {
    private IGameFieldInputController controller;
    private int boardOffset, cellSize, offsetX, offsetY;
    private int touchedX = -1, touchedY = -1;

    public GameInputProcessor(IGameFieldInputController controller) {
        this.controller = controller;
    }

    public void resize(int cellSize, int offsetX, int offsetY, int boardOffset) {
        this.cellSize = cellSize;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.boardOffset = boardOffset;
    }

    // ИЗМЕНЕНО: Сделан public для использования в GameFieldControl
    public int getOffset(boolean x) {
        return x ? this.offsetX : this.offsetY;
    }

    // ИЗМЕНЕНО: Сделан public
    public float indexToCoord(float index, boolean x) {
        return this.boardOffset + this.getOffset(x) + (index + 0.5f) * this.cellSize;
    }

    // ИЗМЕНЕНО: Сделан public
    public float sizeToCoord(float size) {
        return size * this.cellSize;
    }

    public int coordToIndex(float coord, boolean x) {
        return (int) ((coord - this.boardOffset - this.getOffset(x)) / this.cellSize);
    }

    // ... (остальные методы touchDown, touchUp, scrolled без изменений) ...
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!controller.canMove()) return false;
        int i = coordToIndex(screenX, true);
        int j = coordToIndex(screenY, false);
        if (controller.checkIndex(i) && controller.checkIndex(j)) {
            touchedX = screenX; touchedY = screenY; return true;
        }
        return false;
    }

    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        trySwap(screenX, screenY, 0.25f * cellSize);
        touchedX = -1; touchedY = -1; return true;
    }

    public boolean trySwap(int screenX, int screenY, float swapDistance) {
        if (touchedX == -1) return false;
        int dx = screenX - touchedX, dy = screenY - touchedY;
        if (Math.abs(dx) > swapDistance || Math.abs(dy) > swapDistance) {
            int i1 = coordToIndex(touchedX, true), j1 = coordToIndex(touchedY, false);
            int i2 = i1, j2 = j1;
            if (Math.abs(dx) > Math.abs(dy)) i2 += dx > 0 ? 1 : -1;
            else j2 += dy > 0 ? 1 : -1;
            if (controller.checkIndex(i2) && controller.checkIndex(j2)) controller.swap(i1, j1, i2, j2);
            return true;
        }
        return false;
    }

    @Override public boolean touchDragged(int x, int y, int p) { return trySwap(x, y, cellSize); }
    @Override public boolean mouseMoved(int x, int y) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
    @Override public boolean keyDown(int k) { return false; }
    @Override public boolean keyUp(int k) { return false; }
    @Override public boolean keyTyped(char c) { return false; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { touchedX = -1; return true; }
}

