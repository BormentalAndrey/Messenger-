package com.mystery_of_orient_express.match3_engine.controller;

import com.badlogic.gdx.InputProcessor;

public class GameInputProcessor implements InputProcessor {

    private IGameFieldInputController controller;
    private int boardOffset;
    private int cellSize;
    private int offsetX;
    private int offsetY;

    private int touchedX = -1;
    private int touchedY = -1;

    public GameInputProcessor(IGameFieldInputController controller) {
        this.controller = controller;
    }

    public void resize(int cellSize, int offsetX, int offsetY, int boardOffset) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.cellSize = cellSize;
        this.boardOffset = boardOffset;
    }

    private int getOffset(boolean x) {
        return x ? this.offsetX : this.offsetY;
    }

    public int coordToIndex(float coord, boolean x) {
        return (int) ((coord - this.boardOffset - this.getOffset(x)) / this.cellSize);
    }

    public boolean trySwap(int screenX, int screenY, float swapDistance) {
        if (touchedX == -1) return false;
        
        int dx = screenX - this.touchedX;
        int dy = screenY - this.touchedY;
        
        if (Math.abs(dx) > swapDistance || Math.abs(dy) > swapDistance) {
            int i1 = this.coordToIndex(this.touchedX, true);
            int j1 = this.coordToIndex(this.touchedY, false);

            int i2 = i1;
            int j2 = j1;

            if (Math.abs(dx) > Math.abs(dy)) {
                i2 += dx > 0 ? 1 : -1;
            } else {
                j2 += dy > 0 ? 1 : -1;
            }

            if (this.controller.checkIndex(i2) && this.controller.checkIndex(j2)) {
                this.controller.swap(i1, j1, i2, j2);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!this.controller.canMove()) return false;

        int i = this.coordToIndex(screenX, true);
        int j = this.coordToIndex(screenY, false);
        if (this.controller.checkIndex(i) && this.controller.checkIndex(j)) {
            this.touchedX = screenX;
            this.touchedY = screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        this.trySwap(screenX, screenY, 0.25f * this.cellSize);
        this.touchedX = -1;
        this.touchedY = -1;
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (this.trySwap(screenX, screenY, (float)this.cellSize)) {
            this.touchedX = -1;
            this.touchedY = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        this.touchedX = -1;
        this.touchedY = -1;
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) { return false; }

    @Override
    public boolean keyDown(int keycode) { return false; }

    @Override
    public boolean keyUp(int keycode) { return false; }

    @Override
    public boolean keyTyped(char character) { return false; }
}

