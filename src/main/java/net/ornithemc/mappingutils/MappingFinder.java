package net.ornithemc.mappingutils;

import java.util.Collection;
import java.util.LinkedHashSet;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.tree.MappingHistory;
import net.ornithemc.mappingutils.io.diff.tree.MappingsDiffTree;
import net.ornithemc.mappingutils.io.diff.tree.Version;

class MappingFinder {

	static Collection<MappingHistory> run(MappingsDiffTree tree, String s) throws Exception {
		return run(tree, null, s);
	}

	static Collection<MappingHistory> run(MappingsDiffTree tree, MappingTarget target, String key) throws Exception {
		return new MappingFinder(tree, target, key).run();
	}

	private final MappingsDiffTree tree;
	private final MappingTarget target;
	private final String key;

	private final Collection<MappingHistory> mappings;

	private MappingFinder(MappingsDiffTree tree, MappingTarget target, String key) {
		this.tree = tree;
		this.target = target;
		this.key = key;

		this.mappings = new LinkedHashSet<>();
	}

	private Collection<MappingHistory> run() throws Exception {
		find(tree.root());
		return mappings;
	}

	private void find(Version v) throws Exception {
		if (v.isRoot()) {
			for (Mapping<?> m : v.getMappings().getTopLevelClasses()) {
				check(m);
			}
		} else {
			for (Diff<?> d : v.getDiff().getTopLevelClasses()) {
				check(d);
			}
		}

		for (Version cv : v.getChildren()) {
			find(cv);
		}
	}

	private void check(Mapping<?> m) {
		if (matches(m)) {
			mappings.add(MappingHistory.of(m));
		}

		for (Mapping<?> cm : m.getChildren()) {
			check(cm);
		}
	}

	private void check(Diff<?> d) {
		if (matches(d)) {
			mappings.add(MappingHistory.of(d));
		}

		for (Diff<?> cd : d.getChildren()) {
			check(cd);
		}
	}

	private boolean matches(Mapping<?> m) {
		if (target != null && target != m.target()) {
			return false;
		}

		switch (m.target()) {
		case CLASS:
			if (key.endsWith("/")) { // looking for matches within a package
				return m.src().startsWith(key) || m.get().startsWith(key);
			} else if (key.indexOf('/') < 0) {
				// search key does not contain package
				if (key.indexOf('$') < 0) {
					// search for match to simplified outer + inner name
					return stripPackage(m.src()).equals(key) || stripPackage(m.get()).equals(key);
				} else {
					// search for match to inner name
					return stripOuterName(m.src()).equals(key) || stripOuterName(m.get()).equals(key);
				}
			} else {
				// search for match to complete name
				return m.src().equals(key) || m.get().equals(key);
			}
		case FIELD:
		case METHOD:
		case PARAMETER:
			if (key.indexOf(':') < 0) {
				// search for matches to name
				return m.src().equals(key) || m.get().equals(key);
			} else {
				// search for matches to key
				return m.key().equals(key);
			}
		default:
			return false;
		}
	}

	private boolean matches(Diff<?> d) {
		if (target != null && target != d.target()) {
			return false;
		}

		switch (d.target()) {
		case CLASS:
			if (key.endsWith("/")) { // looking for matches within a package
				return d.src().startsWith(key) || d.get(DiffSide.A).startsWith(key) || d.get(DiffSide.B).startsWith(key);
			} else if (key.indexOf('/') < 0) {
				// search key does not contain package
				if (key.indexOf('$') < 0) {
					// search for match to simplified outer + inner name
					return stripPackage(d.src()).equals(key) || stripPackage(d.get(DiffSide.A)).equals(key) || stripPackage(d.get(DiffSide.B)).equals(key);
				} else {
					// search for match to inner name
					return stripOuterName(d.src()).equals(key) || stripOuterName(d.get(DiffSide.A)).equals(key) || stripOuterName(d.get(DiffSide.B)).equals(key);
				}
			} else {
				// search for match to complete name
				return d.src().equals(key) || d.get(DiffSide.A).equals(key) || d.get(DiffSide.B).equals(key);
			}
		case FIELD:
		case METHOD:
		case PARAMETER:
			if (key.indexOf(':') < 0) {
				// search for matches to name
				return d.src().equals(key) || d.get(DiffSide.A).equals(key) || d.get(DiffSide.B).equals(key);
			} else {
				// search for matches to key
				return d.key().equals(key);
			}
		default:
			return false;
		}
	}

	private static String stripPackage(String className) {
		return className.substring(className.lastIndexOf('/') + 1);
	}

	private static String stripOuterName(String className) {
		return className.substring(className.lastIndexOf('$') + 1);
	}
}
