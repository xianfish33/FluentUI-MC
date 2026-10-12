package xianfish.fluentui.client.ui.animation;

public enum Easing {
    LINEAR        { @Override public float apply(float t) { return t; } },
    EASE_IN_QUAD  { @Override public float apply(float t) { return t * t; } },
    EASE_OUT_QUAD { @Override public float apply(float t) { return 1 - (1 - t) * (1 - t); } },
    EASE_IN_CUBIC { @Override public float apply(float t) { return t * t * t; } },
    EASE_OUT_CUBIC{ @Override public float apply(float t) { return 1 - (float) Math.pow(1 - t, 3); } },
    EASE_OUT_QUINT{ @Override public float apply(float t) { return 1 - (float) Math.pow(1 - t, 5); } },
    EASE_IN_OUT_CUBIC {
        @Override public float apply(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
        }
    },
    EASE_OUT_BACK {
        @Override public float apply(float t) {
            float c1 = 1.70158f; return 1 + (c1 + 1) * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
        }
    };

    public abstract float apply(float t);
}
