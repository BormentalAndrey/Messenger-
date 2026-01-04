package com.mystery_of_orient_express.match3_engine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Field {
    private IGameObjectFactory objectFactory;
    private IScoreController scoreController;
    private int size;
    private int kindsCount;
    private Cell[][] cells;
    private List<Match> rowMatches;
    private List<Match> colMatches;

    public Field(IGameObjectFactory objectFactory, IScoreController scoreController, int size, int kindsCount) {
        this.size = size;
        this.kindsCount = kindsCount;
        this.objectFactory = objectFactory;
        this.scoreController = scoreController;
        this.cells = new Cell[this.size][this.size];
        this.rowMatches = new ArrayList<>();
        this.colMatches = new ArrayList<>();
        
        for (int i = 0; i < this.size; ++i) {
            for (int j = 0; j < this.size; ++j) {
                this.cells[i][j] = new Cell();
                if (this.objectFactory != null) {
                    this.cells[i][j].object = this.objectFactory.newGem(i, j);
                }
            }
        }
    }

    public int getSize() { return this.size; }

    public boolean checkIndex(int index) {
        return 0 <= index && index < this.size;
    }

    public CellObject getGem(int i, int j) {
        if (checkIndex(i) && checkIndex(j)) {
            return this.cells[i][j].object;
        }
        return null;
    }

    public Set<CellObject> getAllGems() {
        Set<CellObject> all = new HashSet<>();
        for (int i = 0; i < this.size; ++i) {
            for (int j = 0; j < this.size; ++j) {
                CellObject gem = getGem(i, j);
                if (gem != null) all.add(gem);
            }
        }
        return all;
    }

    public Set<CellObject> removeGems(Set<CellObject> gems) {
        Set<CellObject> chained = new HashSet<>();
        for (int i = 0; i < this.size; ++i) {
            for (int j = 0; j < this.size; ++j) {
                CellObject thisGem = this.getGem(i, j);
                if (thisGem != null && gems.contains(thisGem)) {
                    this.cells[i][j].object = null;
                    
                    if (thisGem.effect == CellObject.Effects.H_RAY) {
                        for (int nI = 0; nI < this.size; ++nI) {
                            CellObject gem = getGem(nI, j);
                            if (gem != null && gem.activity == -1) chained.add(gem);
                        }
                    } else if (thisGem.effect == CellObject.Effects.V_RAY) {
                        for (int nJ = 0; nJ < this.size; ++nJ) {
                            CellObject gem = getGem(i, nJ);
                            if (gem != null && gem.activity == -1) chained.add(gem);
                        }
                    } else if (thisGem.effect == CellObject.Effects.AREA) {
                        for (int nI = Math.max(0, i-1); nI <= Math.min(this.size-1, i+1); nI++) {
                            for (int nJ = Math.max(0, j-1); nJ <= Math.min(this.size-1, j+1); nJ++) {
                                CellObject gem = getGem(nI, nJ);
                                if (gem != null && gem.activity == -1) chained.add(gem);
                            }
                        }
                    } else if (thisGem.effect == CellObject.Effects.KIND) {
                        int targetKind = (int) (Math.random() * this.kindsCount);
                        for (int nI = 0; nI < this.size; nI++) {
                            for (int nJ = 0; nJ < this.size; nJ++) {
                                CellObject gem = getGem(nI, nJ);
                                if (gem != null && gem.activity == -1 && gem.kind == targetKind) chained.add(gem);
                            }
                        }
                    }
                }
            }
        }
        return chained;
    }

    private void findMatchedGems(boolean rows) {
        List<Match> matched = rows ? this.rowMatches : this.colMatches;
        matched.clear();
        for (int outer = 0; outer < this.size; ++outer) {
            Match current = new Match();
            for (int inner = 0; inner < this.size; ++inner) {
                int r = rows ? inner : outer;
                int c = rows ? outer : inner;
                CellObject gem = this.cells[r][c].object;
                
                boolean validGem = gem != null && gem.activity == -1 && gem.kind >= 0;
                if (validGem && gem.kind == current.kind) {
                    current.length++;
                    if (current.length == 3) {
                        current.i = rows ? inner - 2 : outer;
                        current.j = rows ? outer : inner - 2;
                        matched.add(current);
                    }
                } else {
                    current = new Match();
                    current.length = validGem ? 1 : 0;
                    current.kind = validGem ? gem.kind : -1;
                }
            }
        }
    }

    public Set<CellObject> findMatchedGems() {
        findMatchedGems(true);
        findMatchedGems(false);
        this.scoreController.updateCombo(this.rowMatches.size() + this.colMatches.size());
        
        Set<CellObject> matchedAll = new HashSet<>();
        processSpecialMatches();

        for (Match m : rowMatches) {
            for (int d = 0; d < m.length; d++) matchedAll.add(getGem(m.i + d, m.j));
        }
        for (Match m : colMatches) {
            for (int d = 0; d < m.length; d++) matchedAll.add(getGem(m.i, m.j + d));
        }
        return matchedAll;
    }

    private void processSpecialMatches() {
        for (Match rm : rowMatches) {
            for (Match cm : colMatches) {
                if (rm.i <= cm.i && cm.i < rm.i + rm.length && cm.j <= rm.j && rm.j < cm.j + cm.length) {
                    CellObject gem = getGem(cm.i, rm.j);
                    if (gem != null) gem.effect = CellObject.Effects.AREA;
                }
            }
        }
    }

    public Set<CellObject> findGemsToFall() {
        Set<CellObject> gemsToFall = new HashSet<>();
        for (int i = 0; i < this.size; ++i) {
            for (int j = 0; j < this.size; ++j) {
                if (this.cells[i][j].object != null) continue;

                CellObject newGem;
                if (j == this.size - 1) {
                    newGem = this.objectFactory.newGem(i, j + 1);
                } else {
                    newGem = this.cells[i][j + 1].object;
                    this.cells[i][j + 1].object = null;
                }
                this.cells[i][j].object = newGem;
                if (newGem != null) gemsToFall.add(newGem);
            }
        }
        return gemsToFall;
    }

    public boolean testNoMoves() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (cells[i][j].object != null && cells[i][j].object.effect == CellObject.Effects.KIND) return false;
            }
        }
        // Упрощенная проверка на горизонтальный свайп
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size - 1; j++) {
                if (testSwapInternal(i, j, i, j + 1)) return false;
            }
        }
        // Упрощенная проверка на вертикальный свайп
        for (int i = 0; i < size - 1; i++) {
            for (int j = 0; j < size; j++) {
                if (testSwapInternal(i, j, i + 1, j)) return false;
            }
        }
        return true;
    }

    private boolean testSwapInternal(int i1, int j1, int i2, int j2) {
        swapObjects(i1, j1, i2, j2);
        findMatchedGems(true);
        findMatchedGems(false);
        boolean hasMatch = rowMatches.size() > 0 || colMatches.size() > 0;
        swapObjects(i1, j1, i2, j2);
        return hasMatch;
    }

    public boolean testSwap(int i1, int j1, int i2, int j2) {
        swapObjects(i1, j1, i2, j2);
        findMatchedGems(true);
        findMatchedGems(false);
        CellObject obj1 = getGem(i1, j1);
        CellObject obj2 = getGem(i2, j2);
        boolean success = rowMatches.size() > 0 || colMatches.size() > 0 ||
                (obj1 != null && obj1.effect == CellObject.Effects.KIND) || 
                (obj2 != null && obj2.effect == CellObject.Effects.KIND);
        if (!success) swapObjects(i1, j1, i2, j2);
        return success;
    }

    private void swapObjects(int i1, int j1, int i2, int j2) {
        CellObject temp = this.cells[i1][j1].object;
        this.cells[i1][j1].object = this.cells[i2][j2].object;
        this.cells[i2][j2].object = temp;
    }
}

