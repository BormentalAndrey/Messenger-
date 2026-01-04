package com.mystery_of_orient_express.match3_engine.controller;

public interface IGameFieldInputController {
    boolean canMove();
    boolean checkIndex(int index);
    void swap(int i1, int j1, int i2, int j2);
}

