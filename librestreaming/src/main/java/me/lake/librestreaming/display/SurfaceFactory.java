package me.lake.librestreaming.display;

import android.view.Surface;

public interface SurfaceFactory {

     Surface createSurface(int width, int height);

    void stop();
}
