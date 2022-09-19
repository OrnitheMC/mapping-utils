package net.ornithemc.mappingutils.io.diff;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.Type;

public class MappingsDiff {

	private final Map<String, ClassDiff> classDiffs = new LinkedHashMap<>();

	public MappingsDiff() {

	}

	public ClassDiff getClass(String name) {
		return classDiffs.get(name);
	}

	public Collection<ClassDiff> getClasses() {
		return classDiffs.values();
	}

	public ClassDiff addClass(String src, String dstA, String dstB) {
		return addClass(new ClassDiff(this, src, dstA, dstB));
	}

	private ClassDiff addClass(ClassDiff c) {
		return classDiffs.compute(c.key(), (key, value) -> {
			return checkReplace(value, c);
		});
	}

	public void removeClass(String name) {
		ClassDiff c = getClass(name);

		if (c != null) {
			removeClass(c);
		}
	}

	private void removeClass(ClassDiff c) {
		classDiffs.remove(c.key());
	}

	public void validate() {
		for (ClassDiff c : classDiffs.values()) {
			c.validate();
		}
	}

	public MappingsDiff copy() {
		MappingsDiff copy = new MappingsDiff();

		for (ClassDiff c : classDiffs.values()) {
			copy.addClass(c.copy());
		}

		return copy;
	}

	public static boolean isDiffSafe(String a, String b) {
		return (a == null || b == null) ? a != b : isDiff(a, b);
	}

	public static boolean isDiff(String a, String b) {
		return a.isEmpty() ? !b.isEmpty() : !a.equals(b);
	}

	public static abstract class Diff<T extends Diff<T>> {

		protected MappingsDiff root;
		protected JavadocDiff javadoc;

		protected String src;
		protected String dstA;
		protected String dstB;

		private Diff(MappingsDiff root, String src, String dstA, String dstB) {
			this.root = Objects.requireNonNull(root);
			this.javadoc = new JavadocDiff();

			this.src = src;
			this.dstA = dstA;
			this.dstB = dstB;
		}

		@Override
		public final String toString() {
			return getClass().getSimpleName() + "[" + key() + " -> --" + dstA + " ++" + dstB + "]";
		}

		public final String src() {
			return src;
		}

		public final String get(DiffSide side) {
			return side == DiffSide.A ? dstA : dstB;
		}

		public final void set(DiffSide side, String dst) {
			if (side == DiffSide.A) {
				this.dstA = dst;
			} else {
				this.dstB = dst;
			}
		}

		public final JavadocDiff getJavadoc() {
			return javadoc;
		}

		public final boolean isDiff() {
			return MappingsDiff.isDiff(dstA, dstB) || javadoc.isDiff();
		}

		public boolean hasChildren() {
			return false;
		}

		protected String key() {
			return src;
		}

		protected void validate() {
			if (javadoc != null) {
				javadoc.validate();
			}

			dstA = validateDst(dstA);
			dstB = validateDst(dstB);
		}

		protected abstract T copy();

	}

	public static class ClassDiff extends Diff<ClassDiff> {

		private final Map<String, FieldDiff> fieldDiffs;
		private final Map<String, MethodDiff> methodDiffs;

		private ClassDiff(MappingsDiff root, String src, String dstA, String dstB) {
			super(root, src, dstA, dstB);

			this.fieldDiffs = new LinkedHashMap<>();
			this.methodDiffs = new LinkedHashMap<>();
		}

		@Override
		public boolean hasChildren() {
			return !fieldDiffs.isEmpty() || !methodDiffs.isEmpty();
		}

		@Override
		protected void validate() {
			super.validate();

			for (FieldDiff f : fieldDiffs.values()) {
				f.validate();
			}
			for (MethodDiff m : methodDiffs.values()) {
				m.validate();
			}

			if (!hasChildren() && !isDiff()) {
				root.removeClass(this);
			}
		}

		@Override
		protected ClassDiff copy() {
			ClassDiff copy = new ClassDiff(root, src, dstA, dstB);
			copy.javadoc = javadoc.copy();

			for (FieldDiff f : fieldDiffs.values()) {
				copy.addField(f.copy());
			}
			for (MethodDiff m : methodDiffs.values()) {
				copy.addMethod(m.copy());
			}

			return copy;
		}

		public FieldDiff getField(String name, String desc) {
			return fieldDiffs.get(name + desc);
		}

		public MethodDiff getMethod(String name, String desc) {
			return methodDiffs.get(name + desc);
		}

		public Collection<FieldDiff> getFields() {
			return fieldDiffs.values();
		}

		public Collection<MethodDiff> getMethods() {
			return methodDiffs.values();
		}

		public FieldDiff addField(String src, String dstA, String dstB, String desc) {
			return addField(new FieldDiff(root, src, dstA, dstB, desc));
		}

		private FieldDiff addField(FieldDiff f) {
			f.parent = this;

			return fieldDiffs.compute(f.key(), (key, value) -> {
				return checkReplace(value, f);
			});
		}

		public MethodDiff addMethod(String src, String dstA, String dstB, String desc) {
			return addMethod(new MethodDiff(root, src, dstA, dstB, desc));
		}

		private MethodDiff addMethod(MethodDiff m) {
			m.parent = this;

			return methodDiffs.compute(m.key(), (key, value) -> {
				return checkReplace(value, m);
			});
		}

		public void removeField(String name, String desc) {
			FieldDiff f = getField(name, desc);

			if (f != null) {
				removeField(f);
			}
		}

		private void removeField(FieldDiff f) {
			fieldDiffs.remove(f.key());
		}

		public void removeMethod(String name, String desc) {
			MethodDiff m = getMethod(name, desc);

			if (m != null) {
				removeMethod(m);
			}
		}

		private void removeMethod(MethodDiff m) {
			methodDiffs.remove(m.key());
		}
	}

	public static class FieldDiff extends Diff<FieldDiff> {

		private ClassDiff parent;
		private String desc;

		private FieldDiff(MappingsDiff root, String src, String dstA, String dstB, String desc) {
			super(root, src, dstA, dstB);

			this.desc = desc;
		}

		@Override
		protected String key() {
			return super.key() + desc;
		}

		@Override
		protected void validate() {
			super.validate();

			if (!isDiff()) {
				parent.removeField(this);
			}
		}

		@Override
		protected FieldDiff copy() {
			FieldDiff copy = new FieldDiff(root, src, dstA, dstB, desc);
			copy.javadoc = javadoc.copy();

			return copy;
		}

		public String getDesc() {
			return desc;
		}

		public ClassDiff getParent() {
			return parent;
		}
	}

	public static class MethodDiff extends Diff<MethodDiff> {

		private final Map<String, ParameterDiff> parameterDiffs;

		private ClassDiff parent;
		private String desc;
		private int parameterCount;

		private MethodDiff(MappingsDiff root, String src, String dstA, String dstB, String desc) {
			super(root, src, dstA, dstB);

			this.parameterDiffs = new LinkedHashMap<>();

			this.desc = desc;
			this.parameterCount = -1;
		}

		@Override
		public boolean hasChildren() {
			return !parameterDiffs.isEmpty();
		}

		@Override
		protected String key() {
			return super.key() + desc;
		}

		@Override
		protected void validate() {
			super.validate();

			for (ParameterDiff p : parameterDiffs.values()) {
				p.validate();
			}

			if (!hasChildren() && !isDiff()) {
				parent.removeMethod(this);
			}
		}

		@Override
		protected MethodDiff copy() {
			MethodDiff copy = new MethodDiff(root, src, dstA, dstB, desc);
			copy.javadoc = javadoc.copy();

			for (ParameterDiff p : parameterDiffs.values()) {
				copy.addParameter(p.copy());
			}

			return copy;
		}

		public String getDesc() {
			return desc;
		}

		public int getParameterCount() {
			if (parameterCount < 0) {
				Type type = Type.getMethodType(desc);
				int argAndRetSize = type.getArgumentsAndReturnSizes();

				parameterCount = argAndRetSize >> 2;
			}

			return parameterCount;
		}

		public ClassDiff getParent() {
			return parent;
		}

		public ParameterDiff getParameter(int index) {
			return parameterDiffs.get(Integer.toString(index));
		}

		public Collection<ParameterDiff> getParameters() {
			return parameterDiffs.values();
		}

		public ParameterDiff addParameter(String src, String dstA, String dstB, int index) {
			return addParameter(new ParameterDiff(root, src, dstA, dstB, index));
		}

		private ParameterDiff addParameter(ParameterDiff p) {
			p.parent = this;

			return parameterDiffs.compute(p.key(), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public void removeParameter(int index) {
			ParameterDiff p = getParameter(index);

			if (p != null) {
				removeParameter(p);
			}
		}

		private void removeParameter(ParameterDiff p) {
			parameterDiffs.remove(p.key());
		}
	}

	public static class ParameterDiff extends Diff<ParameterDiff> {

		private final int index;

		private MethodDiff parent;

		private ParameterDiff(MappingsDiff root, String src, String dstA, String dstB, int index) {
			super(root, src, dstA, dstB);

			if (index < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.index = index;
		}

		@Override
		protected String key() {
			return Integer.toString(index);
		}

		@Override
		protected void validate() {
			super.validate();

			if (!isDiff()) {
				parent.removeParameter(this);
			}
		}

		@Override
		protected ParameterDiff copy() {
			ParameterDiff copy = new ParameterDiff(root, src, dstA, dstB, index);
			copy.javadoc = javadoc.copy();

			return copy;
		}

		public int getIndex() {
			return index;
		}

		public MethodDiff getParent() {
			return parent;
		}
	}

	public static class JavadocDiff {

		private String javadocA;
		private String javadocB;

		private Diff<?> parent;

		private JavadocDiff() {

		}

		private JavadocDiff(String javadocA, String javadocB) {
			this.javadocA = javadocA;
			this.javadocB = javadocB;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[--" + javadocA + " ++" + javadocB + "]";
		}

		public String get(DiffSide side) {
			return side == DiffSide.A ? javadocA : javadocB;
		}

		public void set(DiffSide side, String javadoc) {
			if (side == DiffSide.A) {
				this.javadocA = javadoc;
			} else {
				this.javadocB = javadoc;
			}
		}

		public boolean isDiff() {
			return MappingsDiff.isDiff(javadocA, javadocB);
		}

		public Diff<?> getParent() {
			return parent;
		}

		protected void validate() {
			javadocA = validateDst(javadocA);
			javadocB = validateDst(javadocB);
		}

		protected JavadocDiff copy() {
			JavadocDiff copy = new JavadocDiff(javadocA, javadocB);

			return copy;
		}
	}

	private static <T extends Diff<T>> T checkReplace(T o, T n) {
		if (o != null && n != null) {
			System.err.println("replacing diff " + o + " with " + n);
		}

		return n;
	}

	private static String validateDst(String dst) {
		return dst == null ? "" : dst;
	}
}
