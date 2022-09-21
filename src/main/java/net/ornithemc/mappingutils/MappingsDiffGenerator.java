package net.ornithemc.mappingutils;

import java.util.LinkedList;
import java.util.List;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;

class MappingsDiffGenerator {

	static void run(Mappings a, Mappings b, MappingsDiff diff) throws Exception {
		new MappingsDiffGenerator(a, b, diff).run();
	}

	private final MappingsDiff diff;
	private final Mappings a;
	private final Mappings b;

	private final List<MappingPair> mappingPairs;

	private MappingsDiffGenerator(Mappings a, Mappings b, MappingsDiff diff) {
		if (!a.getSrcNamespace().equals(b.getSrcNamespace())) {
			throw new IllegalArgumentException("src namespaces do not match!");
		}
		if (!a.getDstNamespace().equals(b.getDstNamespace())) {
			throw new IllegalArgumentException("dst namespaces do not match!");
		}

		a.validate();
		b.validate();

		this.diff = diff;
		this.a = a;
		this.b = b;

		this.mappingPairs = new LinkedList<>();
	}

	private void run() throws Exception {
		collectMappingPairs();
		createMappingDiffs();
	}

	private void collectMappingPairs() {
		for (ClassMapping ca : a.getTopLevelClasses()) {
			addMappingPair(null, ca, null);
		}
		for (ClassMapping cb : b.getTopLevelClasses()) {
			ClassMapping ca = a.getClass(cb.src());

			if (ca == null) {
				addMappingPair(null, null, cb);
			}
		}
	}

	private void addMappingPair(MappingPair parent, Mapping<?> a, Mapping<?> b) {
		MappingPair pair = new MappingPair(parent, a, b);
		a = pair.get(DiffSide.A);
		b = pair.get(DiffSide.B);

		mappingPairs.add(pair);

		if (a != null) {
			for (Mapping<?> ca : a.getChildren()) {
				addMappingPair(pair, ca, null);
			}
		}
		if (b != null) {
			for (Mapping<?> cb : b.getChildren()) {
				if (a == null || a.getChild(cb.target(), cb.key()) == null) {
					addMappingPair(pair, null, cb);
				}
			}
		}
	}

	private void createMappingDiffs() {
		for (MappingPair pair : mappingPairs) {
			Mapping<?> a = pair.get(DiffSide.A);
			Mapping<?> b = pair.get(DiffSide.B);

			if (b == null) {
				diff(a, b, DiffMode.A);
			} else if (a == null) {
				diff(a, b, DiffMode.B);
			} else {
				if (MappingsDiff.safeIsDiff(a.get(), b.get())) {
					diff(a, b, DiffMode.AB);
				}
				if (MappingsDiff.safeIsDiff(a.getJavadoc(), b.getJavadoc())) {
					diff(a, b, DiffMode.JAVADOC);
				}
			}
		}
	}

	private void diff(Mapping<?> a, Mapping<?> b, DiffMode mode) {
		mode.run(a, b, addDiff(a == null ? b : a));
	}

	public Mapping<?> findMapping(DiffSide side, MappingPair parent, MappingTarget target, String key) {
		Mapping<?> m = null;

		if (parent == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings");
			}

			m = (side == DiffSide.A) ? a.getClass(key) : b.getClass(key);
		} else {
			Mapping<?> parentMapping = parent.get(side);

			if (parentMapping != null) {
				m = parentMapping.getChild(target, key);
			}
		}

		return m;
	}

	private Diff<?> addDiff(Mapping<?> mapping) {
		Diff<?> d;
		Mapping<?> parentMapping = mapping.getParent();

		if (parentMapping == null || mapping.target() == MappingTarget.CLASS) {
			if (mapping.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + mapping.target() + " from the root diff");
			}

			d = diff.getClass(mapping.key());

			if (d == null) {
				d = diff.addClass(mapping.src());
			}
		} else {
			Diff<?> parent = addDiff(parentMapping);

			if (parent == null) {
				throw new IllegalStateException("unable to get diff for " + parentMapping);
			}

			MappingTarget target = mapping.target();
			String key = mapping.key();

			d = parent.getChild(target, key);

			if (d == null) {
				d = parent.addChild(target, key, "", "");
			}
		}

		return d;
	}

	private class MappingPair {

		private Mapping<?> a;
		private Mapping<?> b;

		protected MappingPair(MappingPair parent, Mapping<?> a, Mapping<?> b) {
			if (a != null && b != null && a.target() != b.target()) {
				throw new IllegalArgumentException("mismatched targets for mapping pair: " + a.target() + " and " + b.target());
			}

			if (a == null) {
				a = findMapping(DiffSide.A, parent, b.target(), b.key());
			}
			if (b == null) {
				b = findMapping(DiffSide.B, parent, a.target(), a.key());
			}

			this.a = a;
			this.b = b;
		}

		@Override
		public String toString() {
			return a + " == " + b;
		}

		public Mapping<?> get(DiffSide side) {
			return side == DiffSide.A ? a : b;
		}
	}

	private enum DiffMode {

		A() {

			@Override
			public void run(Mapping<?> a, Mapping<?> b, Diff<?> d) {
				d.set(DiffSide.A, a.get());
				d.set(DiffSide.B, "");
				d.getJavadoc().set(DiffSide.A, a.getJavadoc());
				d.getJavadoc().set(DiffSide.B, "");
			}
		},
		B() {

			@Override
			public void run(Mapping<?> a, Mapping<?> b, Diff<?> d) {
				d.set(DiffSide.A, "");
				d.set(DiffSide.B, b.get());
				d.getJavadoc().set(DiffSide.A, "");
				d.getJavadoc().set(DiffSide.B, b.getJavadoc());
			}
		},
		AB() {

			@Override
			public void run(Mapping<?> a, Mapping<?> b, Diff<?> d) {
				d.set(DiffSide.A, a.get());
				d.set(DiffSide.B, b.get());
			}
		},
		JAVADOC() {

			@Override
			public void run(Mapping<?> a, Mapping<?> b, Diff<?> d) {
				d.getJavadoc().set(DiffSide.A, a.getJavadoc());
				d.getJavadoc().set(DiffSide.B, b.getJavadoc());
			}
		};

		public abstract void run(Mapping<?> a, Mapping<?> b, Diff<?> d);

	}
}
