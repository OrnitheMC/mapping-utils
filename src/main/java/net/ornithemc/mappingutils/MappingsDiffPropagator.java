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

	static void run(MappingsDiffTree tree, MappingsDiff changes, String version) throws Exception {
		new MappingsDiffPropagator(tree, changes, version).run();
	}

	private final MappingsDiffTree tree;
	private final MappingsDiff changes;
	private final String version;

	private MappingsDiffPropagator(MappingsDiffTree tree, MappingsDiff changes, String version) {
		this.tree = tree;
		this.changes = changes;
		this.version = version;
	}

	private void run() throws Exception {
		Version v = tree.getVersion(version);

		if (v == null) {
			throw new IllegalStateException("mappings for version " + version + " do not exist!");
		}

		for (Diff<?> d : changes.getTopLevelClasses()) {
			propagateChange(v, d);
		}

		tree.write();
	}

	private void propagateChange(Version v, Diff<?> change) throws Exception {
		DiffMode mode = DiffMode.NONE;

		if (change.isDiff()) {
			mode = mode.with(DiffMode.MAPPINGS);
		}
		if (change.getJavadoc().isDiff()) {
			mode = mode.with(DiffMode.JAVADOCS);
		}

		propagateChange(v, change, DiffSide.B, mode);

		for (Diff<?> childChange : change.getChildren()) {
			propagateChange(v, childChange);
		}
	}

	private void propagateChange(Version v, Diff<?> change, DiffSide side, DiffMode mode) throws Exception {
		if (mode == DiffMode.NONE) {
			return;
		}

		// side A = propagate down
		// side B = propagate up
		// we first propagate up to find the source of the mapping,
		// then propagate it down from there

		if (v.isRoot()) {
			if (side == DiffSide.B) {
				Result<Mapping<?>> result = applyChange(v.getMappings(), change, side, mode);

				if (result.success()) {
					v.markDirty();

					// source of the mapping is root, now propagate down
					for (Version c : v.getChildren()) {
						propagateChange(c, change, DiffSide.A, mode);
					}
				}
			}
		} else {
			Result<Diff<?>> result = applyChange(v.getDiff(), change, side, mode);

			if (result.success()) {
				v.markDirty();

				// check if we're propagating up or down
				if (side == DiffSide.B) {
					// found source of the mapping, now propagate down
					for (Version c : v.getChildren()) {
						propagateChange(c, change, DiffSide.A, mode);
					}
				}
			}

			// part of the change that was not yet applied
			mode = mode.without(result.mode);

			// keep propagating that part in the same direction
			if (side == DiffSide.B) {
				propagateChange(v.getParent(), change, side, mode);
			} else {
				for (Version c : v.getChildren()) {
					propagateChange(c, change, side, mode);
				}
			}
		}
	}

	private Result<Mapping<?>> applyChange(Mappings mappings, Diff<?> change, DiffSide side, DiffMode mode) {
		Mapping<?> m;
		Result<Mapping<?>> result;

		MappingTarget target = change.target();
		String key = change.key();
		String n = change.get(DiffSide.B);

		Diff<?> parentChange = change.getParent();

		if (parentChange == null) {
			if (change.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + change.target() + " from the root mappings");
			}

			m = mappings.getClass(key);

			if (m == null) {
				if (n.isEmpty()) {
					System.out.println("ignoring invalid diff " + change + " - mapping does not exist in root!");
				} else {
					m = mappings.addClass(change.src(), change.src());
				}
			} else {
				if (n.isEmpty()) {
					mappings.removeClass(key);
				} else {
					m.set(n);
				}
			}
		} else {
			Result<Mapping<?>> parentResult = applyChange(mappings, parentChange, side, DiffMode.NONE);

			if (parentResult.subject == null) {
				throw new IllegalStateException("unable to apply " + parentChange);
			}

			m = parentResult.subject.getChild(target, key);

			if (m == null) {
				if (n.isEmpty()) {
					System.out.println("ignoring invalid diff " + change + " - mapping does not exist in root!");
				} else {
					m = parentResult.subject.addChild(target, key, n);
				}
			} else {
				if (n.isEmpty()) {
					parentResult.subject.removeChild(target, key);
				} else {
					m.set(n);
				}
			}
		}

		result = new Result<>(m, mode);

		if (m != null) {
			if (mode.is(DiffMode.JAVADOCS)) {
				m.setJavadoc(change.getJavadoc().get(DiffSide.B));
			}
		}

		return result;
	}

	private Result<Diff<?>> applyChange(MappingsDiff diff, Diff<?> change, DiffSide side, DiffMode mode) {
		Diff<?> d;
		Result<Diff<?>> result;

		Diff<?> parentChange = change.getParent();

		if (parentChange == null) {
			if (change.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + change.target() + " from the root diff");
			}

			d = diff.getClass(change.src());
		} else {
			Result<Diff<?>> parentResult = applyChange(diff, parentChange, side, DiffMode.NONE);

			if (parentResult.subject == null) {
				throw new IllegalStateException("unable to apply " + parentChange);
			}

			MappingTarget target = change.target();
			String key = change.key();

			d = parentResult.subject.getChild(target, key);
		}

		result = new Result<>(d);

		if (d != null) {
			if (mode.is(DiffMode.MAPPINGS)) {
				if (d.isDiff()) {
					d.set(side, change.get(DiffSide.B));
					result.with(DiffMode.MAPPINGS);
				}
			}
			if (mode.is(DiffMode.JAVADOCS)) {
				JavadocDiff jchange = change.getJavadoc();
				JavadocDiff jd = d.getJavadoc();

				if (jd.isDiff()) {
					jd.set(side, jchange.get(DiffSide.B));
					result.with(DiffMode.JAVADOCS);
				}
			}
		}

		return result;
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
	}

	private class Result<T> {

		// the mapping/diff the change was applied to
		public final T subject;

		// the type of change that was applied
		public DiffMode mode;

		public Result(T subject) {
			this(subject, DiffMode.NONE);
		}

		public Result(T subject, DiffMode mode) {
			this.subject = subject;

			this.mode = mode;
		}

		public void with(DiffMode mode) {
			this.mode = this.mode.with(mode);
		}

		public boolean success() {
			return mode != DiffMode.NONE;
		}
	}
}
