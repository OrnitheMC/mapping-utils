package net.ornithemc.mappingutils;

import java.util.LinkedList;
import java.util.List;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;

class MappingsDiffGenerator {

	static void run(Mappings a, Mappings b, MappingsDiff diff) throws Exception {
		new MappingsDiffGenerator(a, b, diff).run();
	}

	private final MappingsDiff diff;
	private final Mappings a;
	private final Mappings b;

	private final List<MappingPair<?>> mappingPairs;

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

	public ClassMapping getClass(DiffSide side, String name) {
		return side == DiffSide.A ? a.getClass(name) : b.getClass(name);
	}

	public FieldMapping getField(DiffSide side, String name, String desc, ClassMapping ca, ClassMapping cb) {
		return side == DiffSide.A ? ca.getField(name, desc) : cb.getField(name, desc);
	}

	public MethodMapping getMethod(DiffSide side, String name, String desc, ClassMapping ca, ClassMapping cb) {
		return side == DiffSide.A ? ca.getMethod(name, desc) : cb.getMethod(name, desc);
	}

	public ParameterMapping getParameter(DiffSide side, int index, MethodMapping ma, MethodMapping mb) {
		return side == DiffSide.A ? ma.getParameter(index) : mb.getParameter(index);
	}

	private void run() throws Exception {
		collectMappingPairs();
		createMappingDiffs();
	}

	private void collectMappingPairs() {
		for (ClassMapping ca : a.getTopLevelClasses()) {
			addClassPair(ca, null);
		}
		for (ClassMapping cb : b.getTopLevelClasses()) {
			ClassMapping ca = a.getClass(cb.src());

			if (ca == null) {
				addClassPair(null, cb);
			}
		}
	}

	private void addClassPair(ClassMapping ca, ClassMapping cb) {
		ClassMappingPair pair = new ClassMappingPair(ca, cb);

		mappingPairs.add(pair);

		if (ca != null) {
			for (FieldMapping fa : ca.getFields()) {
				addFieldPair(fa, null, pair);
			}
			for (MethodMapping ma : ca.getMethods()) {
				addMethodPair(ma, null, pair);
			}
			for (ClassMapping cca : ca.getClasses()) {
				addClassPair(cca, null);
			}
		}
		if (cb != null) {
			for (FieldMapping fb : cb.getFields()) {
				if (ca == null || ca.getField(fb.src(), fb.getDesc()) == null) {
					addFieldPair(null, fb, pair);
				}
			}
			for (MethodMapping mb : cb.getMethods()) {
				if (ca == null || ca.getMethod(mb.src(), mb.getDesc()) == null) {
					addMethodPair(null, mb, pair);
				}
			}
			for (ClassMapping ccb : cb.getClasses()) {
				if (ca == null || ca.getClass(ccb.src()) == null) {
					addClassPair(null, ccb);
				}
			}
		}
	}

	private void addFieldPair(FieldMapping fa, FieldMapping fb, ClassMappingPair parent) {
		FieldMappingPair pair = new FieldMappingPair(fa, fb, parent);

		mappingPairs.add(pair);
	}

	private void addMethodPair(MethodMapping ma, MethodMapping mb, ClassMappingPair parent) {
		MethodMappingPair pair = new MethodMappingPair(ma, mb, parent);

		mappingPairs.add(pair);

		if (ma != null) {
			for (ParameterMapping pa : ma.getParameters()) {
				addParameterPair(pa, null, pair);
			}
		}
		if (mb != null) {
			for (ParameterMapping pb : mb.getParameters()) {
				if (ma == null || ma.getParameter(pb.getIndex()) == null) {
					addParameterPair(null, pb, pair);
				}
			}
		}
	}

	private void addParameterPair(ParameterMapping pa, ParameterMapping pb, MethodMappingPair parent) {
		ParameterMappingPair pair = new ParameterMappingPair(pa, pb, parent);

		mappingPairs.add(pair);
	}

	private void createMappingDiffs() {
		for (MappingPair<?> pair : mappingPairs) {
			Mapping<?> a = pair.get(DiffSide.A);
			Mapping<?> b = pair.get(DiffSide.B);

			if (b == null) {
				diff(a, b, DiffMode.A);
			} else if (a == null) {
				diff(a, b, DiffMode.B);
			} else {
				if (MappingsDiff.isDiffSafe(a.get(), b.get())) {
					diff(a, b, DiffMode.AB);
				}
				if (MappingsDiff.isDiffSafe(a.getJavadoc(), b.getJavadoc())) {
					diff(a, b, DiffMode.JAVADOC);
				}
			}
		}
	}

	private void diff(Mapping<?> a, Mapping<?> b, DiffMode mode) {
		mode.run(a, b, getDiff(a));
	}

	private Diff<?> getDiff(Mapping<?> mapping) {
		if (mapping instanceof ClassMapping) {
			return getDiff((ClassMapping)mapping);
		}
		if (mapping instanceof FieldMapping) {
			return getDiff((FieldMapping)mapping);
		}
		if (mapping instanceof MethodMapping) {
			return getDiff((MethodMapping)mapping);
		}
		if (mapping instanceof ParameterMapping) {
			return getDiff((ParameterMapping)mapping);
		}

		throw new IllegalArgumentException("unable to get diff of unknown mapping type " + mapping.getClass());
	}

	private ClassDiff getDiff(ClassMapping c) {
		ClassDiff cd = diff.getClass(c.src());
		return cd != null ? cd : diff.addClass(c.src(), c.get(), c.get());
	}

	private FieldDiff getDiff(FieldMapping f) {
		ClassDiff cd = getDiff(f.getParent());
		FieldDiff fd = cd.getField(f.src(), f.getDesc());
		return fd != null ? fd : cd.addField(f.src(), f.get(), f.get(), f.getDesc());
	}

	private MethodDiff getDiff(MethodMapping m) {
		ClassDiff cd = getDiff(m.getParent());
		MethodDiff md = cd.getMethod(m.src(), m.getDesc());
		return md != null ? md : cd.addMethod(m.src(), m.get(), m.get(), m.getDesc());
	}

	private ParameterDiff getDiff(ParameterMapping p) {
		MethodDiff md = getDiff(p.getParent());
		ParameterDiff pd = md.getParameter(p.getIndex());
		return pd != null ? pd : md.addParameter(p.src(), p.get(), p.get(), p.getIndex());
	}

	private abstract class MappingPair<T extends Mapping<T>> {

		private T a;
		private T b;

		protected MappingPair(T a, T b) {
			this.a = a;
			this.b = b;
		}

		protected abstract T find(DiffSide side, T mapping);

		public T get(DiffSide side) {
			if (a == null) a = find(DiffSide.A, b);
			if (b == null) b = find(DiffSide.B, a);

			return side == DiffSide.A ? a : b;
		}
	}

	private class ClassMappingPair extends MappingPair<ClassMapping> {

		protected ClassMappingPair(ClassMapping a, ClassMapping b) {
			super(a, b);
		}

		@Override
		protected ClassMapping find(DiffSide side, ClassMapping c) {
			return MappingsDiffGenerator.this.getClass(side, c.src());
		}
	}

	private class FieldMappingPair extends MappingPair<FieldMapping> {

		private final ClassMappingPair parent;

		protected FieldMappingPair(FieldMapping a, FieldMapping b, ClassMappingPair parent) {
			super(a, b);

			this.parent = parent;
		}

		@Override
		protected FieldMapping find(DiffSide side, FieldMapping f) {
			return MappingsDiffGenerator.this.getField(side, f.src(), f.getDesc(), parent.get(DiffSide.A), parent.get(DiffSide.B));
		}
	}

	private class MethodMappingPair extends MappingPair<MethodMapping> {

		private final ClassMappingPair parent;

		protected MethodMappingPair(MethodMapping a, MethodMapping b, ClassMappingPair parent) {
			super(a, b);

			this.parent = parent;
		}

		@Override
		protected MethodMapping find(DiffSide side, MethodMapping f) {
			return MappingsDiffGenerator.this.getMethod(side, f.src(), f.getDesc(), parent.get(DiffSide.A), parent.get(DiffSide.B));
		}
	}

	private class ParameterMappingPair extends MappingPair<ParameterMapping> {

		private final MethodMappingPair parent;

		protected ParameterMappingPair(ParameterMapping a, ParameterMapping b, MethodMappingPair parent) {
			super(a, b);

			this.parent = parent;
		}

		@Override
		protected ParameterMapping find(DiffSide side, ParameterMapping f) {
			return MappingsDiffGenerator.this.getParameter(side, f.getIndex(), parent.get(DiffSide.A), parent.get(DiffSide.B));
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
