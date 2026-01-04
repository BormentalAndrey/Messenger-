package com.mystery_of_orient_express.match3_engine.controller;

import java.util.Set;
import com.mystery_of_orient_express.match3_engine.model.CellObject;
import com.mystery_of_orient_express.match3_engine.model.GameObject;
import com.mystery_of_orient_express.match3_engine.model.IAnimation;

public class FallAnimation implements IAnimation {
    private static final float totalDuration = 0.0666666f;
    private static final float totalDurationInv = 15.0f;
    private IAnimationHandler handler;
    private CellObject[] gemsArray;
    private float currentDuration;
    private float fallLength;

    public FallAnimation(Set<CellObject> gems, float fallLength, IAnimationHandler handler) {
        this.handler = handler;
        this.gemsArray = gems.toArray(new CellObject[0]);
        for (CellObject gem : this.gemsArray) {
            gem.activity = 0;
        }
        this.fallLength = fallLength;
        this.currentDuration = 0;
    }

    @Override
    public void update(float delta) {
        float currentDelta = Math.min(totalDuration - this.currentDuration, delta);
        float deltaLength = this.fallLength * currentDelta * totalDurationInv;
        for (CellObject gem : this.gemsArray) {
            ((GameObject) gem).posY -= deltaLength;
        }
        this.currentDuration += delta;
        if (this.currentDuration >= totalDuration) {
            for (CellObject gem : this.gemsArray) {
                gem.activity = -1;
            }
            this.handler.onComplete(this);
        }
    }
}

