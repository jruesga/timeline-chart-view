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

/**
 * An utility class for deal with arrays
 */
public class ArraysHelper {

    /**
     * Perform a sort operation over the values array and update the indexes array.
     */
    public static void sort(double[] values, int[] indexes) {
        int i = 0;
        int count = values.length;
        for (; i < count; i++) {
            if (i == (count - 1)) return;
            double v1 = values[i], v2 = values[i + 1];
            int i1 = indexes[i], i2 = indexes[i + 1];
            if (v1 <= v2) continue;
            values[i] = v2; values[i + 1] = v1;
            indexes[i] = i2; indexes[i + 1] = i1;
            i = -1;
        }
    }
}
