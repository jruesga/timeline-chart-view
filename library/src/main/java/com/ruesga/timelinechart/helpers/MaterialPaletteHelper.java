/*
 * Copyright (C) 2015 Jorge Ruesga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ruesga.timelinechart.helpers;

import android.graphics.Color;

/**
 * An color palette helper class
 */
public class MaterialPaletteHelper {

    /**
     * Create an spectrum color palette from a base color. Max 8 colors; after that
     * color are filled but with a reused color.
     */
    public static int[] createMaterialSpectrumPalette(int color, final int count) {
        int[] palette = new int[count];
        if (count > 0) {
            final boolean isDarkColor = isDarkColor(color);

            final float[] opacity = isDarkColor
                    ? new float[]{.75f, .50f, .25f, .10f, .85f, .75f, .50f, .25f}
                    : new float[]{.85f, .75f, .50f, .25f, .75f, .50f, .25f, .10f};
            for (int i = 0; i < count; i++) {
                final int op = i % opacity.length;
                int mask = (isDarkColor && op < 4) || (!isDarkColor && op >= 4)
                        ? Color.WHITE: Color.BLACK;
                float alpha = opacity[op];
                palette[i] = applyMaskColor(color, mask, alpha);
            }
        }
        return palette;
    }

    /**
     * Method that returns if a color belongs to a light or dark color spectrum.
     */
    public static boolean isDarkColor(int color){
        double base = 0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color);
        return (1 - base / 255) > 0.5;
    }

    private static int applyMaskColor(int color, int mask, float alpha) {
        int[] rgb = {Color.red(color), Color.green(color), Color.blue(color)};
        int[] maskRgb = {Color.red(mask), Color.green(mask), Color.blue(mask)};
        for(int j = 0; j < 3; j++) {
            rgb[j] = Math.round(rgb[j] * alpha) + Math.round(maskRgb[j] * (1 - alpha));
            if (rgb[j] > 255) {
                rgb[j] = 255;
            } else if (rgb[j] < 0) {
                rgb[j] = 0;
            }
        }
        return Color.rgb(rgb[0], rgb[1], rgb[2]);
    }

    public static int getComplementaryColor(int color) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
        hsv[0] = (hsv[0] + 180) % 360;
        return Color.HSVToColor(hsv);
    }
}
