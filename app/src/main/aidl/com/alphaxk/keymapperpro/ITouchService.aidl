package com.alphaxk.keymapperpro;

interface ITouchService {
    void injectMultiTouch(in int[] ids, in float[] xs, in float[] ys);
    void injectMouseMove(float dx, float dy);
}
