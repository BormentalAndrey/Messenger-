package com.mystery_of_orient_express.match3_engine.controller;

import java.util.Set;
import com.mystery_of_orient_express.match3_engine.model.CellObject;
import com.mystery_of_orient_express.match3_engine.model.GameObject;
import com.mystery_of_orient_express.match3_engine.model.IAnimation;

public class DisappearAnimation implements IAnimation {
    private static final float totalDuration = 0.1666666f;
    private static final float totalDurationInv = 6.0f;
    private IAnimationHandler handler;
    private CellObject[] gemsArray;
    private float currentDuration;
    private float gemSize;

    public DisappearAnimation(Set<CellObject> gems, float gemSize, IAnimationHandler handler) {
        this.handler = handler;
        this.gemsArray = gems.toArray(new CellObject[0]);
        for (CellObject gem : this.gemsArray) {
            gem.activity = 1;
        }
        this.gemSize = gemSize;
        this.currentDuration = 0;
    }

    @Override
    public void update(float delta) {
        this.currentDuration += delta;
        if (this.currentDuration >= totalDuration) {
            for (CellObject gem : this.gemsArray) {
                gem.activity = -1;
            }
            this.handler.onComplete(this);
        } else {
            float newSize = this.gemSize * (1 - this.currentDuration * totalDurationInv);
            for (CellObject gem : this.gemsArray) {
                GameObject go = (GameObject) gem;
                go.sizeY = go.sizeX = newSize;
            }
        }
    }
}

