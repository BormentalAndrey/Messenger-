package com.mystery_of_orient_express.match3_engine.model;

public class GameObject extends CellObject {
    public float posX, posY;
    public float sizeX, sizeY;

    public GameObject(int kind, float posX, float posY, float sizeX, float sizeY) {
        this.kind = kind;
        this.posX = posX;
        this.posY = posY;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.activity = -1; // Инициализация как спокойного объекта
    }
}

