//   Copyright (c) Shantanu Kumar. All rights reserved.
//   The use and distribution terms for this software are covered by the
//   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
//   which can be found in the file LICENSE at the root of this distribution.
//   By using this software in any fashion, you are agreeing to be bound by
//   the terms of this license.
//   You must not remove this notice, or any other, from this software.


package calfpath;

public class VolatileInt {

    public /*volatile*/ int value = 0;

    public static VolatileInt create(int init) {
        return new VolatileInt(init);
    }

    public VolatileInt(int init) {
        this.value = init;
    }

    public int get() {
        return value;
    }

    public static long deref(VolatileInt v) {
        return v.value;
    }

    public void set(int newValue) {
        this.value = newValue;
    }

    public static void reset(VolatileInt v, int newValue) {
        v.value = newValue;
    }

}
