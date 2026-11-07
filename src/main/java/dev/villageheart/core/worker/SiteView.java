package dev.villageheart.core.worker;

import dev.villageheart.core.HeartLocation;
import java.util.List;

/** One valid building that offers a job: its marker, ALL its job blocks of that job, and its slot count. */
public record SiteView(HeartLocation building, List<HeartLocation> jobBlocks, int slots) {
	public SiteView {
		jobBlocks = List.copyOf(jobBlocks);
		if (slots < 0) throw new IllegalArgumentException("slots must be >= 0: " + slots);
	}
}
