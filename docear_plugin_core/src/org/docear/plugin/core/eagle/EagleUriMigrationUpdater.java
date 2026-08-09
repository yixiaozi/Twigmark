package org.docear.plugin.core.eagle;

import org.docear.plugin.core.mindmap.AMindmapUpdater;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;

public class EagleUriMigrationUpdater extends AMindmapUpdater {
	private final EagleImageMigrator.Result aggregate = new EagleImageMigrator.Result();

	public EagleUriMigrationUpdater(final String title) {
		super(title);
	}

	public boolean updateMindmap(final MapModel map) {
		final EagleImageMigrator.Result result = EagleImageMigrator.migrateMap(map);
		aggregate.scanned += result.scanned;
		aggregate.alreadyEagle += result.alreadyEagle;
		aggregate.keptPath += result.keptPath;
		aggregate.migrated += result.migrated;
		aggregate.imported += result.imported;
		aggregate.unmatched += result.unmatched;
		aggregate.unmatchedDetails.addAll(result.unmatchedDetails);
		aggregate.migratedDetails.addAll(result.migratedDetails);
		LogUtils.info("Eagle migrate " + map.getTitle() + ": " + result.summaryText());
		return result.migrated > 0 || result.imported > 0;
	}

	public EagleImageMigrator.Result getAggregate() {
		return aggregate;
	}
}
