package org.tpunn.autoblade;

public interface ScoreManager {
    int get();
    void set(int score);
    void add(int delta);
}
