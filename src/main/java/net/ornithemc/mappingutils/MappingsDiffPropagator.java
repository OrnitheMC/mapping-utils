package net.ornithemc.mappingutils;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.tree.MappingsDiffTree;
import net.ornithemc.mappingutils.io.diff.tree.Version;

class MappingsDiffPropagator {

	static void run(PropagationDirection dir, MappingsDiffTree tree, MappingsDiff changes, String version) throws Exception {
		new MappingsDiffPropagator(dir, tree, changes, version).run();
	}

	private final PropagationDirection dir;
	private final MappingsDiffTree tree;
	private final MappingsDiff changes;
	private final String version;

	private MappingsDiffPropagator(PropagationDirection dir, MappingsDiffTree tree, MappingsDiff changes, String version) {
		this.dir = dir;
		this.tree = tree;
		this.changes = changes;
		this.version = version;
	}

	private void run() throws Exception {
		Version v = tree.getVersion(version);

		if (v == null) {
			throw new IllegalStateException("mappings for version " + version + " do not exist!");
		}

		for (Diff<?> change : changes.getTopLevelClasses()) {
			propagateChange(v, change);
		}

		tree.write();
	}

	private void propagateChange(Version v, Diff<?> change) throws Exception {
		DiffMode mode = DiffMode.of(change);
		Operation op = Operation.of(change, mode);

		// we first propagate up to find the source of the mapping,
		// then propagate the change down from there
		propagateChange(v, change, PropagationDirection.UP, mode, op);

		for (Diff<?> childChange : change.getChildren()) {
			propagateChange(v, childChange);
		}
	}

	private void propagateChange(Version v, Diff<?> change, PropagationDirection dir, DiffMode mode, Operation op) throws Exception {
		if (mode == DiffMode.NONE) {
			return;
		}

		Result<?> result;

		if (v.isRoot()) {
			result = applyChange(v, v.getMappings(), change, mode, Operation.of(change, mode));
		} else {
			DiffSide side = (dir == PropagationDirection.UP) ? DiffSide.B : DiffSide.A;
			boolean insert = (dir == PropagationDirection.UP) ? !this.dir.up() : !this.dir.down();

			result = applyChange(v, v.getDiff(), change, side, mode, Operation.of(change, mode), insert);
		}

		if (result.success()) {
			v.markDirty();

			// found the source of the mapping, now propagate down
			for (Version c : v.getChildren()) {
				propagateChange(c, change, PropagationDirection.DOWN, result.mode(), result.operation());
			}
		}

		// the part of the change that was not yet applied
		mode = mode.without(result.mode());

		// keep propagating that part in the same direction
		if (dir == PropagationDirection.UP) {
			if (!v.isRoot()) {
				propagateChange(v.getParent(), change, dir, mode, op);
			}
		} else {
			for (Version c : v.getChildren()) {
				propagateChange(c, change, dir, mode, op);
			}
		}
	}

	private Result<Mapping<?>> applyChange(Version v, Mappings mappings, Diff<?> change, DiffMode mode, Operation op) {
		MappingTarget target = change.target();
		String key = change.key();
		Mapping<?> m = null;

		String o = change.get(DiffSide.A);
		String n = change.get(DiffSide.B);

		Diff<?> parentChange = change.getParent();

		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings of " + v);
			}

			m = mappings.getClass(key);

			if (mode.is(DiffMode.MAPPINGS)) {
				if (op == Operation.ADD) {
					if (m == null) {
						m = mappings.addClass(key, n);
					} else {
						System.out.println("ignoring invalid change " + change + " to " + v + " - mapping already exists!");
						mode = mode.without(DiffMode.MAPPINGS);
						op = Operation.NONE;
					}
				}
				if (op == Operation.REMOVE) {
					if (m == null) {
						System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
						mode = mode.without(DiffMode.MAPPINGS);
						op = Operation.NONE;
					} else {
						m = mappings.removeClass(key);
					}
				}
			}
		} else {
			Result<Mapping<?>> parentResult = applyChange(v, mappings, parentChange, DiffMode.NONE, Operation.NONE);
			Mapping<?> parent = parentResult.subject();

			if (parent == null) {
				if (op != Operation.NONE) {
//					System.out.println("ignoring invalid change " + change + " to " + v + " - parent mapping does not exist! (were the diffs provided in the wrong order?)");
					mode = DiffMode.NONE;
					op = Operation.NONE;
				}
			} else {
				m = parent.getChild(target, key);

				if (mode.is(DiffMode.MAPPINGS)) {
					if (op == Operation.ADD) {
						if (m == null) {
							m = parent.addChild(target, key, n);
						} else {
							System.out.println("ignoring invalid change " + change + " to " + v + " - mapping already exists!");
							mode = mode.without(DiffMode.MAPPINGS);
							op = Operation.NONE;
						}
					}
					if (op == Operation.REMOVE) {
						if (m == null) {
							System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
							mode = mode.without(DiffMode.MAPPINGS);
							op = Operation.NONE;
						} else {
							m = parent.removeChild(target, key);
						}
					}
				}
			}
		}

		if (mode.is(DiffMode.MAPPINGS) && op == Operation.CHANGE) {
			if (m == null) {
				System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
				mode = mode.without(DiffMode.MAPPINGS);
				op = Operation.NONE;
			} else if (m.get().equals(o)) {
				m.set(n);
			} else {
				System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not match!");
				mode = mode.without(DiffMode.MAPPINGS);
				op = Operation.NONE;
			}
		}

		JavadocDiff jchange = change.getJavadoc();

		if (mode.is(DiffMode.JAVADOCS) && jchange.isDiff()) {
			if (m == null) {
				System.out.println("ignoring invalid change " + jchange + " to " + v + " - mapping does not exist!");
				mode = mode.without(DiffMode.JAVADOCS);
			} else {
				m.setJavadoc(jchange.get(DiffSide.B));
			}
		}

		return new Result<>(m, mode, op);
	}

	private Result<Diff<?>> applyChange(Version v, MappingsDiff diff, Diff<?> change, DiffSide side, DiffMode mode, Operation op, boolean insert) {
		MappingTarget target = change.target();
		String key = change.key();
		Diff<?> d = null;

		String o = change.get(DiffSide.A);
		String n = change.get(DiffSide.B);

		Diff<?> parentChange = change.getParent();

		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + target + " from the root diff for " + v);
			}

			d = diff.getClass(key);

			if (d == null && insert) {
				d = diff.addClass(key, "", "");
			}
		} else {
			Result<Diff<?>> parentResult = applyChange(v, diff, parentChange, side, mode, op, insert);
			Diff<?> parent = parentResult.subject();

			if (parent == null) {
				if (insert) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - parent diff does not exist! (were the diffs provided in the wrong order?)");
					op = Operation.NONE;
				}
			} else {
				d = parent.getChild(target, key);

				if (d == null && insert) {
					d = parent.addChild(target, key, "", "");
				}
			}
		}

		if (mode.is(DiffMode.MAPPINGS)) {
			if (d == null || !d.isDiff() || !change.isDiff()) {
				mode = mode.without(DiffMode.MAPPINGS);
				op = Operation.NONE;
			} else {
				if (d.get(side).equals(o)) {
					d.set(side, n);
				} else {
					System.out.println("ignoring invalid change " + change + " to " + v + " - diff does not match!");
					op = Operation.NONE;
				}
			}
		}

		JavadocDiff jchange = change.getJavadoc();

		if (mode.is(DiffMode.JAVADOCS) && jchange.isDiff()) {
			if (d == null) {
				if (insert) {
					System.out.println("ignoring invalid change " + jchange + " to " + v + " - diff does not exist!");
				}

				mode = mode.without(DiffMode.JAVADOCS);
			} else {
				d.getJavadoc().set(side, jchange.get(DiffSide.B));
			}
		}

		return new Result<>(d, mode, op);
	}

	private enum DiffMode {

		NONE    (0b00),
		MAPPINGS(0b01),
		JAVADOCS(0b10),
		BOTH    (0b11);

		private static final DiffMode[] ALL;

		static {

			DiffMode[] values = values();
			ALL = new DiffMode[values.length];

			for (DiffMode mode : values) {
				ALL[mode.flags] = mode;
			}
		}

		private final int flags;

		private DiffMode(int flags) {
			this.flags = flags;
		}

		public boolean is(DiffMode mode) {
			return this == mode || this == BOTH;
		}

		public DiffMode with(DiffMode mode) {
			return ALL[flags | mode.flags];
		}

		public DiffMode without(DiffMode mode) {
			return ALL[flags & (~mode.flags)];
		}

		public static DiffMode of(Diff<?> diff) {
			DiffMode mode = NONE;

			if (diff.isDiff()) {
				mode = mode.with(DiffMode.MAPPINGS);
			}
			if (diff.getJavadoc().isDiff()) {
				mode = mode.with(DiffMode.JAVADOCS);
			}

			return mode;
		}
	}

	private enum Operation {

		NONE, CHANGE, ADD, REMOVE;

		public static Operation of(Diff<?> diff, DiffMode mode) {
			if (diff.isDiff() && mode.is(DiffMode.MAPPINGS)) {
				if (diff.get(DiffSide.A).isEmpty()) {
					return ADD;
				}
				if (diff.get(DiffSide.B).isEmpty()) {
					return REMOVE;
				}

				return CHANGE;
			} else {
				return NONE;
			}
		}
	}

	private class Result<T> {

		private final T subject;
		private final DiffMode mode;
		private final Operation op;

		public Result(T subject, DiffMode mode, Operation op) {
			this.subject = subject;
			this.mode = mode;
			this.op = op;
		}

		public T subject() {
			return subject;
		}

		public DiffMode mode() {
			return mode;
		}

		public Operation operation() {
			return op;
		}

		public boolean success() {
			return mode != DiffMode.NONE;
		}
	}
}
