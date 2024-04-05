package net.ornithemc.mappingutils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.graph.Version;
import net.ornithemc.mappingutils.io.diff.graph.VersionGraph;

class Propagator {

	static void run(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) throws IOException {
		new Propagator(options, graph, changes, version).run();
	}

	private static final Mode[] PROPAGATION_MODE_ORDER = { Mode.MAPPINGS, Mode.JAVADOCS };
	// we first propagate up to find sources of the mapping,
	// then propagate the change down from there
	private static final PropagationDirection[] PROPAGATION_DIRECTION_ORDER = { PropagationDirection.UP, PropagationDirection.DOWN };

	private final PropagationOptions options;
	private final VersionGraph graph;

	private final Collection<Version> barriers;

	private Map<Version, MappingsDiff> queuedChanges;
	private Map<Version, MappingsDiff> workingChanges;

	private final PropagationQueue propagation;

	private Propagator(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) {
		changes.validate();

		this.options = options;
		this.graph = graph;

		this.barriers = new HashSet<>();

		this.queuedChanges = new HashMap<>();
		this.workingChanges = new HashMap<>();

		this.propagation = new PropagationQueue();

		Version v = graph.getVersion(version);

		if (v == null) {
			throw new IllegalStateException("version " + version + " does not appear in the graph!");
		}

		if (!options.dir.up()) {
			barriers.add(v);
		}
		if (!options.dir.down()) {
			barriers.addAll(v.getChildren());
		}

		queuedChanges.put(v, changes);
	}

	private void run() throws IOException {
		while (!queuedChanges.isEmpty()) {
			prepareQueuedChanges();

			for (Map.Entry<Version, MappingsDiff> entry : workingChanges.entrySet()) {
				propagateChanges(entry.getKey(), entry.getValue());
			}
		}

		graph.write();
	}

	private void prepareQueuedChanges() {
		Map<Version, MappingsDiff> old = workingChanges;
		workingChanges = queuedChanges;
		queuedChanges = old;

		queuedChanges.clear();
	}

	private void propagateChanges(Version v, MappingsDiff changes) throws IOException {
		for (Diff change : changes.getTopLevelClasses()) {
			propagateChange(v, change);
		}
	}

	private void propagateChange(Version v, Diff change) throws IOException {
		for (Mode mode : PROPAGATION_MODE_ORDER) {
			Operation op = mode.operation(change);

			if (op != Operation.NONE) {
				propagateChange(v, change, mode, op);
			}
		}

		for (Diff childChange : change.getChildren()) {
			propagateChange(v, childChange);
		}
	}

	private void propagateChange(Version v, Diff change, Mode mode, Operation op) throws IOException {
		propagation.offer(PropagationDirection.UP, v);

		while (!propagation.isEmpty()) {
			PropagationQueue.Entry e = propagation.poll();

			if (e != null) {
				PropagationDirection dir = e.direction();
				Version n = e.version();

				if (dir == PropagationDirection.UP) {
					propagateChange(null, n, change, dir, mode, op);
				} else {
					for (Version c : n.getChildren()) {
						propagateChange(n, c, change, dir, mode, op);
					}
				}
			}
		}

		propagation.reset();
	}

	private void propagateChange(Version s, Version v, Diff change, PropagationDirection dir, Mode mode, Operation op) throws IOException {
		if (v.isRoot()) {
			Mappings mappings = v.getMappings();
			Mapping m = applyChange(v, mappings, change, mode, op);

			if (m != null) {
				// success, now propagate in opposite direction
				if (dir == PropagationDirection.UP) {
					propagation.offer(PropagationDirection.DOWN, v);
				}

				v.markDirty();
			}
		} else {
			DiffSide side = (dir == PropagationDirection.UP) ? DiffSide.B : DiffSide.A;
			boolean insert = barriers.contains(v);

			for (Version p : v.getParents()) {
				if (dir == PropagationDirection.DOWN && p != s) {
					continue;
				}

				MappingsDiff diffs = v.getDiff(p);
				Diff d = applyChange(v, diffs, change, side, mode, op, insert);

				if (d == null) {
					// change not applied to this version, propagate further
					if (dir == PropagationDirection.UP) {
						propagation.offer(dir, p);
					} else {
						// change came down from some version, but
						// could be propagated up to other parents
						propagation.offer(PropagationDirection.UP, v);
						propagation.offer(dir, v);
					}
				} else {
					// change applied, now propagate in the opposite direction
					if (dir == PropagationDirection.UP) {
						propagation.offer(PropagationDirection.DOWN, v);
					}
					if (options.lenient && !insert) {
						queueSiblingChange(v, diffs, d, change, side, dir, mode, op);
					}

					v.markDirty();
				}
			}
		}
	}

	private Mapping applyChange(Version v, Mappings mappings, Diff change, Mode mode, Operation op) {
		MappingTarget target = change.target();
		String key = change.key();
		String src = change.src();
		Mapping m = null;

		Diff parentChange = change.getParent();
		Mapping parent = null;

		// retrieve parent and check if mapping already exists
		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings of " + v);
			}

			m = mappings.getClass(src);
		} else {
			parent = applyChange(v, mappings, parentChange, mode, Operation.NONE);

			if (parent == null) {
				if (op != Operation.NONE) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - parent mapping does not exist! (were the diffs provided in the wrong order?)");
					return null;
				}
			} else {
				m = parent.getChild(target, key);
			}
		}

		if (op == Operation.NONE) {
			if (m == null) {
				// add dummy mapping
				if (parent == null) {
					m = mappings.addClass(src, "");
				} else {
					m = parent.addChild(target, key, "");
				}
			}

			return m;
		}

		// now apply the change
		if (mode == Mode.MAPPINGS) {
			String o = change.get(DiffSide.A);
			String n = change.get(DiffSide.B);

			switch (op) {
			case ADD:
				if (m == null) {
					if (parent == null) {
						m = mappings.addClass(src, o);
					} else {
						m = parent.addChild(target, key, o);
					}

					m.set(n);
				} else {
					System.out.println("ignoring invalid change " + change + " to " + v + " - mapping already exists!");
					m = null;
				}

				break;
			case REMOVE:
				if (m == null) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
				} else {
					if (parent == null) {
						m = mappings.removeClass(src);
					} else {
						m = parent.removeChild(target, key);
					}
				}

				break;
			case CHANGE:
				if (m == null) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
				} else {
					if (m.get().equals(o)) {
						m.set(n);
					} else {
						System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not match!");
						m = null;
					}
				}

				break;
			default:
				throw new IllegalStateException("unknown operation " + op);
			}
		}
		if (mode == Mode.JAVADOCS) {
			if (m == null) {
				return null;
			}

			JavadocDiff jchange = change.getJavadoc();
			String o = jchange.get(DiffSide.A);
			String n = jchange.get(DiffSide.B);

			if (m.getJavadoc().equals(o)) {
				m.setJavadoc(n);
			} else {
				System.out.println("ignoring invalid change " + jchange + " to " + v + " - javadoc does not match!");
				m = null;
			}
		}

		return m;
	}

	private Diff applyChange(Version v, MappingsDiff diff, Diff change, DiffSide side, Mode mode, Operation op, boolean insert) {
		MappingTarget target = change.target();
		String key = change.key();
		String src = change.src();
		Diff d = null;

		Diff parentChange = change.getParent();
		Diff parent = null;

		// retrieve parent and check if diff already exists
		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + target + " from the root diff for " + v);
			}

			d = diff.getClass(src);

			if (d == null && insert) {
				d = diff.addClass(src, "", "");
			}
		} else {
			parent = applyChange(v, diff, parentChange, side, mode, Operation.NONE, insert);

			if (parent == null) {
				if (op != Operation.NONE && insert) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - parent diff does not exist! (were the diffs provided in the wrong order?)");
					return null;
				}
			} else {
				d = parent.getChild(target, key);

				if (d == null && insert) {
					d = parent.addChild(target, key, "", "");
				}
			}
		}

		if (op == Operation.NONE) {
			if (d == null) {
				// add dummy diff
				if (parent == null) {
					d = diff.addClass(src, "", "");
				} else {
					d = parent.addChild(target, key, "", "");
				}
			}

			return d;
		}

		// now apply the change
		if (mode == Mode.MAPPINGS) {
			if (d == null || (!d.isDiff() && !insert)) {
				return null;
			}

			String o = change.get(DiffSide.A);
			String n = change.get(DiffSide.B);

			if (!d.isDiff()) {
				// might be dummy
				d.set(side, o);
				d.set(side.opposite(), o);
			}
			if (d.get(side).equals(o)) {
				d.set(side, n);
			} else {
				System.out.println("ignoring invalid change " + change + " to " + v + " - diff does not match!");
			}
		}
		if (mode == Mode.JAVADOCS) {
			JavadocDiff jchange = change.getJavadoc();
			JavadocDiff jd = (d == null) ? null : d.getJavadoc();

			if (jd == null || (!(d.isDiff() && d.get(side.opposite()).isEmpty()) && !jd.isDiff() && !insert)) {
				return null;
			}

			String o = jchange.get(DiffSide.A);
			String n = jchange.get(DiffSide.B);

			if (!jd.isDiff()) {
				// might be dummy
				jd.set(side, o);
				jd.set(side.opposite(), o);
			}
			if (jd.get(side).equals(o)) {
				jd.set(side, n);
			} else {
				System.out.println("ignoring invalid change " + jchange + " to " + v + " - diff does not match!");
				d = null;
			}
		}

		return d;
	}

	private final Scanner scanner = new Scanner(System.in);

	private void queueSiblingChange(Version v, MappingsDiff diffs, Diff d, Diff change, DiffSide side, PropagationDirection dir, Mode mode, Operation op) {
		if (change.get(DiffSide.A).isEmpty() == d.get(side.opposite()).isEmpty()) {
			// mapping (does not) exists on both sides
			// so do not try to propagate to siblings
			return;
		}

		if (d.target() == MappingTarget.PARAMETER) {
			return;
		}

		Diff sibling = findSibling(v, diffs, d, change, side, dir, mode, op);

		if (sibling != null) {
			side = side.opposite();

			if (dir == PropagationDirection.UP) {
				for (Version p : v.getParents()) {
					queueSiblingChange(p, sibling, change, side, mode, op);
				}
			} else {
				queueSiblingChange(v, sibling, change, side, mode, op);
			}
		}
	}

	private Diff findSibling(Version v, MappingsDiff diffs, Diff d, Diff change, DiffSide side, PropagationDirection dir, Mode mode, Operation op) {
		MappingTarget target = d.target();
		String id = Mapping.getId(d.src());

		List<Diff> siblings = new ArrayList<>();

		if (target == MappingTarget.CLASS) {
			for (Diff c : diffs.getClasses(id)) {
				if (c != d && c.isDiff()) {
					siblings.add(c);
				}
			}
		} else if (target == MappingTarget.FIELD || target == MappingTarget.METHOD) {
			List<Diff> parents = new ArrayList<>();

			Diff dparent = d.getParent();
			Diff siblingParent = findSibling(v, diffs, dparent, change.getParent(), side, dir, mode, op);

			parents.add(dparent);
			if (siblingParent != null) {
				parents.add(siblingParent);
			}

			for (Diff parent : parents) {
				for (Diff c : parent.getChildren(id)) {
					if (c != d && c.target() == target && c.isDiff()) {
						siblings.add(c);
					}
				}
			}
		} else {
			return null; // parameters not yet supported
		}

		if (siblings.isEmpty()) {
			return null;
		}

		Iterator<Diff> it = siblings.iterator();

		siblingLoop:
		while (it.hasNext()) {
			Diff sibling = it.next();

			for (DiffSide s : DiffSide.values()) {
				if (mode == Mode.MAPPINGS) {
					// for the side that the change was applied to,
					// we need to check against the value before the change
					String dst = (s == side) ? change.get(DiffSide.A) : d.get(s);
					String siblingDst = sibling.get(s);

					if (dst.isEmpty() == siblingDst.isEmpty()) {
						it.remove();
						continue siblingLoop;
					}
					if (s != side) {
						if (target == MappingTarget.CLASS) {
							String simple = dst.substring(dst.lastIndexOf('/') + 1);
							String siblingSimple = siblingDst.substring(siblingDst.lastIndexOf('/') + 1);

							if (simple.length() > siblingSimple.length() ? simple.endsWith(siblingSimple) : siblingSimple.endsWith(simple)) {
								continue;
							}
						} else {
							if (change.get(DiffSide.A).equals(siblingDst)) {
								continue;
							}
						}

						it.remove();
						continue siblingLoop;
					}
				}
				if (mode == Mode.JAVADOCS) {
					// for the side that the change was applied to,
					// we need to check against the value before the change
					JavadocDiff jd = d.getJavadoc();
					JavadocDiff jsibling = sibling.getJavadoc();
					JavadocDiff jchange = change.getJavadoc();

					String dst = (s == side) ? jchange.get(DiffSide.A) : jd.get(s);
					String siblingDst = jsibling.get(s);

					if (dst.isEmpty() == siblingDst.isEmpty()) {
						it.remove();
						continue siblingLoop;
					}
					if (s != side && !jchange.get(DiffSide.A).equals(siblingDst)) {
						it.remove();
						continue siblingLoop;
					}
				}
			}
		}

		if (siblings.isEmpty()) {
			return null;
		}

		Diff sibling = siblings.get(0);

		if (siblings.size() > 1) {
			if (target == MappingTarget.CLASS) {
				throw new RuntimeException("multiple siblings for change: " + change + ": [" + String.join(", ", siblings.stream().map(Object::toString).collect(Collectors.toList())) + "]");
			}

			System.out.println("multiple propagation candidates for " + d);
			for (int i = 0; i < siblings.size(); i++) {
				System.out.println(i + ": " + siblings.get(i));
			}
			System.out.println(siblings.size() + ": none");
			while (true) {
				String cmd = scanner.nextLine();
				int i;
				try {
					i = Integer.parseInt(cmd);
				} catch (NumberFormatException e) {
					e.printStackTrace();
					continue;
				}
				if (i >= 0 && i < siblings.size()) {
					sibling = siblings.get(i);
					System.out.println("chose " + sibling);
					break;
				}
				if (i == siblings.size()) {
					sibling = null;
					System.out.println("chose none");
					break;
				}
            }
		}

		return sibling;
	}

	private Diff queueSiblingChange(Version v, Diff sibling, Diff change, DiffSide side, Mode mode, Operation op) {
		MappingsDiff changes = queuedChanges.computeIfAbsent(v, key -> new MappingsDiff());
		return queueSiblingChange(v, changes, sibling, change, side, mode, op);
	}

	private Diff queueSiblingChange(Version v, MappingsDiff changes, Diff sibling, Diff change, DiffSide side, Mode mode, Operation op) {
		Diff siblingChange = null;
		Diff parentChange = change.getParent();

		if (parentChange == null) {
			if (sibling.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + sibling.toString() + " from the root diff for " + v);
			}

			Stack<Diff> siblingParents = new Stack<>();

			for (Diff siblingParent = sibling.getParent(); siblingParent != null; siblingParent = siblingParent.getParent()) {
				Diff siblingParentChange = changes.getClass(siblingParent.src());

				if (siblingParentChange == null) {
					siblingParents.add(siblingParent);
				}
			}
			while (!siblingParents.isEmpty()) {
				changes.addClass(siblingParents.pop().src(), "", "");
			}

			siblingChange = changes.getClass(sibling.src());

			if (siblingChange == null) {
				siblingChange = changes.addClass(sibling.src(), sibling.get(side), sibling.get(side));
			}

			if (op != Operation.NONE) {
				if (mode == Mode.MAPPINGS) {
					String from = sibling.get(side);
					String to = change.get(DiffSide.B);

					String fromSimple = from.substring(from.lastIndexOf('/') + 1);
					String toSimple = to.substring(to.lastIndexOf('/') + 1);

					int offset = fromSimple.length() - toSimple.length();

					if (offset > 0) {
						to = fromSimple.substring(0, offset) + toSimple.substring(offset);
					} else {
						to = toSimple.substring(-offset);
					}

					siblingChange.set(DiffSide.A, from);
					siblingChange.set(DiffSide.B, to);
				}
				if (mode == Mode.JAVADOCS) {
					JavadocDiff siblingJavadoc = sibling.getJavadoc();
					JavadocDiff javadocChange = change.getJavadoc();
					JavadocDiff siblingJavadocChange = siblingChange.getJavadoc();
					siblingJavadocChange.set(DiffSide.A, siblingJavadoc.get(side));
					siblingJavadocChange.set(DiffSide.B, javadocChange.get(DiffSide.B));
				}
			}
		} else {
			Diff siblingParent = sibling.getParent();

			if (siblingParent == null) {
				queueSiblingChange(v, changes, sibling, parentChange, side, mode, mode == Mode.MAPPINGS ? op : Operation.NONE);

				siblingChange = changes.getClass(sibling.src());

				if (siblingChange == null) {
					siblingChange = changes.addClass(sibling.src(), "", "");
				}
			} else {
				Diff siblingParentChange = queueSiblingChange(v, changes, siblingParent, parentChange, side, mode, Operation.NONE);
				siblingChange = siblingParentChange.getChild(sibling.target(), sibling.key());

				if (siblingChange == null) {
					siblingChange = siblingParentChange.addChild(sibling.target(), sibling.key(), "", "");
				}
			}

			if (op != Operation.NONE) {
				if (mode == Mode.MAPPINGS) {
					String from = sibling.get(side);
					String to = change.get(DiffSide.B);

					String fromSimple = from.substring(from.lastIndexOf('/') + 1);
					String toSimple = to.substring(to.lastIndexOf('/') + 1);

					int offset = fromSimple.length() - toSimple.length();

					if (offset > 0) {
						to = fromSimple.substring(0, offset) + toSimple.substring(offset);
					} else {
						to = toSimple.substring(0, -offset);
					}

					siblingChange.set(DiffSide.A, from);
					siblingChange.set(DiffSide.B, to);
				}
				if (mode == Mode.JAVADOCS) {
					JavadocDiff siblingJavadoc = sibling.getJavadoc();
					JavadocDiff javadocChange = change.getJavadoc();
					JavadocDiff siblingJavadocChange = siblingChange.getJavadoc();
					siblingJavadocChange.set(DiffSide.A, siblingJavadoc.get(side));
					siblingJavadocChange.set(DiffSide.B, javadocChange.get(DiffSide.B));
				}
			}
		}

		return siblingChange;
	}

	private enum Mode {

		MAPPINGS {

			@Override
			public Operation operation(Diff change) {
				return Operation.of(change.get(DiffSide.A), change.get(DiffSide.B));
			}
		},
		JAVADOCS {

			@Override
			public Operation operation(Diff change) {
				return Operation.of(change.getJavadoc().get(DiffSide.A), change.getJavadoc().get(DiffSide.B));
			}
		};

		public Operation operation(Diff change) {
			return Operation.NONE;
		}
	}

	private enum Operation {

		NONE, CHANGE, ADD, REMOVE;

		public static Operation of(String a, String b) {
			if (MappingsDiff.safeIsDiff(a, b)) {
				if (a.isEmpty()) {
					return ADD;
				}
				if (b.isEmpty()) {
					return REMOVE;
				}

				return CHANGE;
			}

			return NONE;
		}
	}

	private static class PropagationQueue {

		private final Map<PropagationDirection, Queue<Version>> queues;
		private final Map<PropagationDirection, Set<Version>> versions;

		public PropagationQueue() {
			this.queues = new EnumMap<>(PropagationDirection.class);
			this.versions = new EnumMap<>(PropagationDirection.class);

			for (PropagationDirection dir : PropagationDirection.values()) {
				// order ensures each version is tested only once in each direction
				// up = towards root/smaller depth
				// down = away from root/larger depth
				this.queues.put(dir, new PriorityQueue<>((v1, v2) -> {
					return v1.getDepth() - v2.getDepth();
				}));
				this.versions.put(dir, new HashSet<>());
			}
		}

		@Override
		public String toString() {
			return queues.toString();
		}

		public boolean offer(PropagationDirection dir, Version v) {
			return versions.get(dir).add(v) && queues.get(dir).offer(v);
		}

		public Entry poll() {
			for (PropagationDirection dir : PROPAGATION_DIRECTION_ORDER) {
				Version v = queues.get(dir).poll();
				if (v != null) {
					return new Entry(dir, v);
				}
			}
			return null;
		}

		public boolean isEmpty() {
			for (PropagationDirection dir : PROPAGATION_DIRECTION_ORDER) {
				if (!queues.get(dir).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		public void reset() {
			for (PropagationDirection dir : PROPAGATION_DIRECTION_ORDER) {
				versions.get(dir).clear();
			}
		}

		public static class Entry {

			private final PropagationDirection dir;
			private final Version version;

			public Entry(PropagationDirection dir, Version version) {
				this.dir = dir;
				this.version = version;
			}

			public PropagationDirection direction() {
				return dir;
			}

			public Version version() {
				return version;
			}
		}
	}
}
