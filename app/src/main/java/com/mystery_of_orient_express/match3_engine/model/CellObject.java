package com.mystery_of_orient_express.match3_engine.model;

public class CellObject {
    public enum Effects { NONE, H_RAY, V_RAY, AREA, KIND };
    
    // activity: 
    // -1: спокойное состояние (на месте)
    //  0: падение (FallAnimation)
    //  1: исчезновение (DisappearAnimation)
    //  2: обмен местами (SwapAnimation)
    public int activity = -1;
    public int kind = -1;
    public Effects effect = Effects.NONE;
}

