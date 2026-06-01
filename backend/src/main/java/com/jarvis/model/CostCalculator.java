package com.jarvis.model;

/** Token cost math (spec §13 Cost / Token Monitor). */
public final class CostCalculator {

    private CostCalculator() {}

    public static double cost(ModelDescriptor model, int promptTokens, int completionTokens) {
        if (model == null) {
            return 0;
        }
        return promptTokens / 1000.0 * model.costInputPer1k()
                + completionTokens / 1000.0 * model.costOutputPer1k();
    }
}
