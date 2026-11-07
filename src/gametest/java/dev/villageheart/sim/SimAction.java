package dev.villageheart.sim;

import dev.villageheart.core.sim.ScenarioAction;

/** One scenario action type. Add a class and one entry in {@link SimActions#ALL}. */
@FunctionalInterface
interface SimAction {
	void run(SimulationRun ctx, ScenarioAction action) throws Exception;
}
