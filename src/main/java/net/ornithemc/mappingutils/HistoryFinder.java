package net.ornithemc.mappingutils;

import java.util.Collection;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.graph.MappingHistory;
import net.ornithemc.mappingutils.io.diff.graph.Version;
import net.ornithemc.mappingutils.io.diff.graph.VersionGraph;

class HistoryFinder {

	static Collection<MappingHistory> run(VersionGraph tree, String key) throws Exception {
		return run(tree, null, key);
	}

	static Collection<MappingHistory> run(VersionGraph tree, MappingTarget target, String key) throws Exception {
		Collection<MappingHistory> histories = Finder.run(tree, target, key);

		for (MappingHistory history : histories) {
			run(tree, history);
		}

		return histories;
	}

	static void run(VersionGraph tree, MappingHistory history) throws Exception {
		new HistoryFinder(tree, history).run();
	}

	private final VersionGraph graph;
	private final MappingHistory history;

	private HistoryFinder(VersionGraph graph, MappingHistory history) {
		this.graph = graph;
		this.history = history;
	}

	private void run() throws Exception {
		graph.walk(v -> find(v), p -> { });
	}

	private void find(Version v) throws Exception {
		if (v.isRoot()) {
			for (Mapping m : v.getMappings().getTopLevelClasses()) {
				m = find(m);

				if (m != null) {
					history.setMapping(v, m);
				}
			}
		} else {
			for (Version p : v.getParents()) {
				for (Diff d : v.getDiff(p).getTopLevelClasses()) {
					d = find(d);

					if (d != null && d.isDiff()) {
						history.setDiff(p, v, d);
					}
				}
			}
		}
	}

	private Mapping find(Mapping m) {
		if (matches(m)) {
			return m;
		}

		for (Mapping cm : m.getChildren()) {
			m = find(cm);

			if (m != null) {
				return m;
			}
		}

		return null;
	}

	private Diff find(Diff d) {
		if (matches(d)) {
			return d;
		}

		for (Diff cd : d.getChildren()) {
			d = find(cd);

			if (d != null) {
				return d;
			}
		}

		return null;
	}

	private boolean matches(Mapping m) {
		return matches(history, m);
	}

	private boolean matches(Diff d) {
		return matches(history, d);
	}

	private static boolean matches(MappingHistory h, Mapping m) {
		if (h == null && m == null) {
			return true; // both null: match
		}
		if (h == null || m == null) {
			return false; // only one of them null: no match
		}

		return matches(h, m.target(), m.key()) && matches(h.getParent(), m.getParent());
	}

	private static boolean matches(MappingHistory h, Diff d) {
		if (h == null && d == null) {
			return true; // both null: match
		}
		if (h == null || d == null) {
			return false; // only one of them null: no match
		}

		return matches(h, d.target(), d.key()) && matches(h.getParent(), d.getParent());
	}

	private static boolean matches(MappingHistory h, MappingTarget target, String key) {
		return h.getTarget() == target && h.getKey().equals(key);
	}
}
