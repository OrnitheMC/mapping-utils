package net.ornithemc.mappingutils.io.diff.graph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import net.ornithemc.mappingutils.CheckedConsumer;
import net.ornithemc.mappingutils.FileUtils;
import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public class VersionGraph {

	private final Format format;
	private final Map<String, Version> versions;

	private Version root;

	private VersionGraph(Format format) {
		this.format = format;
		this.versions = new HashMap<>();
	}

	public Format getFormat() {
		return format;
	}

	public Version root() {
		return root;
	}

	public Version getVersion(String version) {
		return versions.get(version);
	}

	public boolean hasVersion(String version) {
		return versions.containsKey(version);
	}

	public void walk(CheckedConsumer<Version> versionVisitor, CheckedConsumer<Collection<Version>> pathVisitor) throws Exception {
		walk(root, false, versionVisitor, pathVisitor);
	}

	public void walk(String startVersion, CheckedConsumer<Version> versionVisitor, CheckedConsumer<Collection<Version>> pathVisitor) throws Exception {
		walk(startVersion, false, versionVisitor, pathVisitor);
	}

	public void walkToRoot(String startVersion, CheckedConsumer<Version> versionVisitor, CheckedConsumer<Collection<Version>> pathVisitor) throws Exception {
		walk(startVersion, true, versionVisitor, pathVisitor);
	}

	private void walk(String startVersion, boolean towardsRoot, CheckedConsumer<Version> versionVisitor, CheckedConsumer<Collection<Version>> pathVisitor) throws Exception {
		Version version = versions.get(startVersion);

		if (version == null) {
			walk(version, towardsRoot, versionVisitor, pathVisitor);
		} else {
			throw new IllegalArgumentException("no version " + startVersion + " is present in this graph!");
		}
	}

	private void walk(Version start, boolean towardsRoot, CheckedConsumer<Version> versionVisitor, CheckedConsumer<Collection<Version>> pathVisitor) throws Exception {
		Deque<GraphWalker> walkers = new LinkedList<>();
		Set<Version> visited = new HashSet<>();

		GraphWalker walker = new GraphWalker();
		walker.walk(start);

		walkers.add(walker);

		while (!walkers.isEmpty()) {
			walker = walkers.poll();

			if (visited.add(walker.head)) {
				versionVisitor.accept(walker.head);
			}
			if (walker.head.children.isEmpty()) {
				pathVisitor.accept(walker.path);
			}

			for (Version v : (towardsRoot ? walker.head.parents : walker.head.children)) {
				walker = new GraphWalker(walker);
				walker.walk(v);

				walkers.add(walker);
			}
		}
	}

	public Collection<Version> getPathFromRoot(String version) throws Exception {
		Queue<Collection<Version>> paths = new PriorityQueue<>((p1, p2) -> {
			return p1.size() - p2.size();
		});

		walkToRoot(version, v -> { }, p -> paths.add(p));

		if (paths.isEmpty()) {
			return Collections.emptyList();
		}

		return paths.poll();
	}

	public Collection<MappingsDiff> getDiffsFromRoot(String version) throws Exception {
		Collection<Version> path = getPathFromRoot(version);
		Collection<MappingsDiff> diffs = new LinkedList<>();

		Iterator<Version> it = path.iterator();

		Version v = null;
		Version p = null;

		while (it.hasNext()) {
			p = v;
			v = it.next();

			if (p != null) {
				diffs.add(v.getDiff(p));
			}
		}

		return diffs;
	}

	public void write() throws Exception {
		for (Version v : versions.values()) {
			if (v.isRoot()) {
				v.writeMappings();
			} else {
				v.writeDiffs();
			}
		}
	}

	private VersionGraph resolve(Path dir) throws Exception {
		iterateVersions(dir, (parent, version, path) -> {
			if (parent == null) {
				if (root != null) {
					throw new IllegalStateException("multiple roots present: " + root + ", " + version);
				}

				root = addVersion(version);
				root.paths.put(root, path);
			} else {
				Version v = addVersion(version);
				Version p = addVersion(parent);

				v.parents.add(p);
				p.children.add(v);

				v.paths.put(p, path);
			}
		});

		if (root == null) {
			throw new IllegalStateException("version graph does not have a root!");
		}

		walk(v -> { }, p -> { }); // validate graph

		return this;
	}

	private void iterateVersions(Path path, VersionConsumer operation) throws IOException {
		FileUtils.iterate(path, file -> {
			String fileName = file.getName();

			// root mappings
			if (fileName.endsWith(format.mappingsExtension())) {
				int versionLength = fileName.length() - format.mappingsExtension().length();
				String version = fileName.substring(0, versionLength);

				operation.accept(null, version, file.toPath());
			}
			// diff
			if (fileName.endsWith(format.diffExtension())) {
				int versionsLength = fileName.length() - format.diffExtension().length();
				String rawVersions = fileName.substring(0, versionsLength);

				String[] versions = rawVersions.split("[#]");

				if (versions.length != 2) {
					return;
				}

				String parent = versions[0];
				String version = versions[1];

				operation.accept(parent, version, file.toPath());
			}
		});
	}

	private Version addVersion(String version) {
		return versions.computeIfAbsent(version, key -> new Version(version, format));
	}

	public static VersionGraph of(Format format, Path path) throws Exception {
		return new VersionGraph(format).resolve(path);
	}

	@FunctionalInterface
	private interface VersionConsumer {

		void accept(String parent, String version, Path path);

	}

	private static class GraphWalker {

		private final Collection<Version> path;
		private Version head;

		public GraphWalker() {
			this.path = new LinkedHashSet<>();
		}

		public GraphWalker(GraphWalker walker) {
			this.path = new LinkedHashSet<>(walker.path);
			this.head = walker.head;
		}

		public void walk(Version v) {
			if (!path.add(v)) {
				throwLoopException(v);
			}

			head = v;
		}

		private void throwLoopException(Version v) {
			List<Version> path = new LinkedList<>(this.path);
			int prevOccurance = path.indexOf(v);
			List<Version> loop = path.subList(prevOccurance, path.size());

			throw new InvalidVersionGraphException("found a loop in the version graph: (" + prevOccurance + ") " + loop + " + " + v);
		}
	}
}
