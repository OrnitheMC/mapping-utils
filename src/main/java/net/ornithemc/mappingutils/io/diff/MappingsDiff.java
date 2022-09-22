package net.ornithemc.mappingutils.io.diff;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.MappingTarget;

public class MappingsDiff {

	private final Map<String, ClassDiff> classDiffs = new LinkedHashMap<>();

	public MappingsDiff() {

	}

	public ClassDiff getClass(String key) {
		return classDiffs.get(key);
	}

	public Collection<ClassDiff> getClasses() {
		return classDiffs.values();
	}

	public ClassDiff addClass(String src) {
		return addClass(src, "", "");
	}

	public ClassDiff addClass(String src, String dstA, String dstB) {
		return addClass(new ClassDiff(this, src, dstA, dstB));
	}

	private ClassDiff addClass(ClassDiff c) {
		return classDiffs.compute(c.key(), (key, value) -> {
			return checkReplace(value, c);
		});
	}

	public void removeClass(String key) {
		ClassDiff c = getClass(key);

		if (c != null) {
			removeClass(c);
		}
	}

	private ClassDiff removeClass(ClassDiff c) {
		return classDiffs.remove(c.key());
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

	public static boolean isDiff(String a, String b) {
		return a.isEmpty() ? !b.isEmpty() : !a.equals(b);
	}

	public static boolean safeIsDiff(String a, String b) {
		return (a == null || a.isEmpty()) ? !(b == null || b.isEmpty()) : !a.equals(b);
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

		public abstract MappingTarget target();

		public String key() {
			return src;
		}

		public final String src() {
			return src;
		}

		public final String get(DiffSide side) {
			return side == DiffSide.A ? dstA : dstB;
		}

		public final void set(DiffSide side, String dst) {
			dst = validateDst(dst);

			if (side == DiffSide.A) {
				this.dstA = dst;
			} else {
				this.dstB = dst;
			}
		}

		public final void clear() {
			this.dstA = this.dstB = "";
		}

		public final JavadocDiff getJavadoc() {
			return javadoc;
		}

		public final boolean isDiff() {
			return MappingsDiff.isDiff(dstA, dstB);
		}

		public Diff<?> getParent() {
			return null;
		}

		public Diff<?> getChild(MappingTarget target, String key) {
			throw new IllegalStateException("cannot get child diff of target " + target);
		}

		public Diff<?> addChild(MappingTarget target, String key, String dstA, String dstB) {
			throw new IllegalStateException("cannot add child diff of target " + target);
		}

		public Diff<?> removeChild(MappingTarget target, String key) {
			throw new IllegalStateException("cannot remove child diff of target " + target);
		}

		public Collection<Diff<?>> getChildren() {
			return Collections.emptyList();
		}

		public boolean hasChildren() {
			return false;
		}

		protected void validate() {
			javadoc.validate();

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
		public MappingTarget target() {
			return MappingTarget.CLASS;
		}

		@Override
		public Diff<?> getChild(MappingTarget target, String key) {
			switch (target) {
			case FIELD:
				return getField(key);
			case METHOD:
				return getMethod(key);
			default:
				return super.getChild(target, key);
			}
		}

		@Override
		public Diff<?> addChild(MappingTarget target, String key, String dstA, String dstB) {
			switch (target) {
			case FIELD:
				return addField(key, dstA, dstB);
			case METHOD:
				return addMethod(key, dstA, dstB);
			default:
				return super.addChild(target, key, dstA, dstB);
			}
		}

		@Override
		public Diff<?> removeChild(MappingTarget target, String key) {
			switch (target) {
			case FIELD:
				return removeField(key);
			case METHOD:
				return removeMethod(key);
			default:
				return super.removeChild(target, key);
			}
		}

		@Override
		public Collection<Diff<?>> getChildren() {
			Collection<Diff<?>> children = new LinkedList<>();

			children.addAll(fieldDiffs.values());
			children.addAll(methodDiffs.values());

			return children;
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

			if (!hasChildren() && !isDiff() && !javadoc.isDiff()) {
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
			return getField(FieldDiff.key(name, desc));
		}

		public FieldDiff getField(String key) {
			return fieldDiffs.get(key);
		}

		public MethodDiff getMethod(String name, String desc) {
			return getMethod(MethodDiff.key(name, desc));
		}

		public MethodDiff getMethod(String key) {
			return methodDiffs.get(key);
		}

		public Collection<FieldDiff> getFields() {
			return fieldDiffs.values();
		}

		public Collection<MethodDiff> getMethods() {
			return methodDiffs.values();
		}

		public FieldDiff addField(String name, String desc) {
			return addField(FieldDiff.key(name, desc));
		}

		public FieldDiff addField(String key) {
			return addField(key, "", "");
		}

		public FieldDiff addField(String name, String dstA, String dstB, String desc) {
			return addField(FieldDiff.key(name, desc), dstA, dstB);
		}

		public FieldDiff addField(String key, String dstA, String dstB) {
			return addField(new FieldDiff(root, key, dstA, dstB));
		}

		private FieldDiff addField(FieldDiff f) {
			f.parent = this;

			return fieldDiffs.compute(f.key(), (key, value) -> {
				return checkReplace(value, f);
			});
		}

		public MethodDiff addMethod(String name, String desc) {
			return addMethod(MethodDiff.key(name, desc));
		}

		public MethodDiff addMethod(String key) {
			return addMethod(key, "", "");
		}

		public MethodDiff addMethod(String name, String dstA, String dstB, String desc) {
			return addMethod(MethodDiff.key(name, desc), dstA, dstB);
		}

		public MethodDiff addMethod(String key, String dstA, String dstB) {
			return addMethod(new MethodDiff(root, key, dstA, dstB));
		}

		private MethodDiff addMethod(MethodDiff m) {
			m.parent = this;

			return methodDiffs.compute(m.key(), (key, value) -> {
				return checkReplace(value, m);
			});
		}

		public FieldDiff removeField(String name, String desc) {
			return removeField(FieldDiff.key(name, desc));
		}

		public FieldDiff removeField(String key) {
			FieldDiff f = getField(key);

			if (f != null) {
				removeField(f);
			}

			return f;
		}

		private FieldDiff removeField(FieldDiff f) {
			return fieldDiffs.remove(f.key());
		}

		public MethodDiff removeMethod(String name, String desc) {
			return removeMethod(FieldDiff.key(name, desc));
		}

		public MethodDiff removeMethod(String key) {
			MethodDiff m = getMethod(key);

			if (m != null) {
				removeMethod(m);
			}

			return m;
		}

		private MethodDiff removeMethod(MethodDiff m) {
			return methodDiffs.remove(m.key());
		}
	}

	public static class FieldDiff extends Diff<FieldDiff> {

		private ClassDiff parent;
		private String desc;

		private FieldDiff(MappingsDiff root, String key, String dstA, String dstB) {
			this(root, key.split("[:]")[0], dstA, dstB, key.split("[:]")[1]);
		}

		private FieldDiff(MappingsDiff root, String src, String dstA, String dstB, String desc) {
			super(root, src, dstA, dstB);

			this.desc = desc;
		}

		private static String key(String name, String desc) {
			return name + ":" + desc;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.FIELD;
		}

		@Override
		public String key() {
			return key(src, desc);
		}

		@Override
		public ClassDiff getParent() {
			return parent;
		}

		@Override
		protected void validate() {
			super.validate();

			if (!isDiff() && !javadoc.isDiff()) {
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
	}

	public static class MethodDiff extends Diff<MethodDiff> {

		private final ParameterDiff[] parameters;
		private final Map<String, ParameterDiff> parameterDiffs;

		private ClassDiff parent;
		private String desc;

		private MethodDiff(MappingsDiff root, String key, String dstA, String dstB) {
			this(root, key.split("[:]")[0], dstA, dstB, key.split("[:]")[1]);
		}

		private MethodDiff(MappingsDiff root, String src, String dstA, String dstB, String desc) {
			super(root, src, dstA, dstB);

			this.parameters = new ParameterDiff[parameterCount(desc)];
			this.parameterDiffs = new LinkedHashMap<>();

			this.desc = desc;
		}

		private static String key(String name, String desc) {
			return name + ":" + desc;
		}

		private static int parameterCount(String desc) {
			Type type = Type.getMethodType(desc);
			int argAndRetSize = type.getArgumentsAndReturnSizes();

			return argAndRetSize >> 2;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.METHOD;
		}

		@Override
		public String key() {
			return key(src, desc);
		}

		@Override
		public ClassDiff getParent() {
			return parent;
		}

		@Override
		public Diff<?> getChild(MappingTarget target, String key) {
			if (target == MappingTarget.PARAMETER) {
				return getParameter(key);
			} else {
				return super.getChild(target, key);
			}
		}

		@Override
		public Diff<?> addChild(MappingTarget target, String key, String dstA, String dstB) {
			if (target == MappingTarget.PARAMETER) {
				return addParameter(key, dstA, dstB);
			} else {
				return super.addChild(target, key, dstA, dstB);
			}
		}

		@Override
		public Diff<?> removeChild(MappingTarget target, String key) {
			if (target == MappingTarget.PARAMETER) {
				return removeParameter(key);
			} else {
				return super.removeChild(target, key);
			}
		}

		@Override
		public Collection<Diff<?>> getChildren() {
			Collection<Diff<?>> children = new LinkedList<>();

			children.addAll(parameterDiffs.values());

			return children;
		}

		@Override
		public boolean hasChildren() {
			return !parameterDiffs.isEmpty();
		}

		@Override
		protected void validate() {
			super.validate();

			for (ParameterDiff p : parameterDiffs.values()) {
				p.validate();
			}

			if (!hasChildren() && !isDiff() && !javadoc.isDiff()) {
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
			return parameters.length;
		}

		public ParameterDiff getParameter(int index) {
			return parameters[index];
		}

		public ParameterDiff getParameter(String key) {
			return parameterDiffs.get(key);
		}

		public Collection<ParameterDiff> getParameters() {
			return parameterDiffs.values();
		}

		public ParameterDiff addParameter(String name, int index) {
			return addParameter(ParameterDiff.key(index, name));
		}

		public ParameterDiff addParameter(String key) {
			return addParameter(key, "", "");
		}

		public ParameterDiff addParameter(String name, String dstA, String dstB, int index) {
			return addParameter(ParameterDiff.key(index, name), dstA, dstB);
		}

		public ParameterDiff addParameter(String key, String dstA, String dstB) {
			return addParameter(new ParameterDiff(root, key, dstA, dstB));
		}

		private ParameterDiff addParameter(ParameterDiff p) {
			p.parent = this;

			return parameterDiffs.compute(p.key(), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public ParameterDiff removeParameter(int index) {
			ParameterDiff p = getParameter(index);

			if (p != null) {
				removeParameter(p);
			}

			return p;
		}

		public ParameterDiff removeParameter(String key) {
			ParameterDiff p = getParameter(key);

			if (p != null) {
				removeParameter(p);
			}

			return p;
		}

		private ParameterDiff removeParameter(ParameterDiff p) {
			return parameterDiffs.remove(p.key());
		}
	}

	public static class ParameterDiff extends Diff<ParameterDiff> {

		private final int index;

		private MethodDiff parent;

		private ParameterDiff(MappingsDiff root, String key, String dstA, String dstB) {
			this(root, key.substring(key.indexOf(':') + 1), dstA, dstB, Integer.parseInt(key.substring(0, key.indexOf(':'))));
		}

		private ParameterDiff(MappingsDiff root, String src, String dstA, String dstB, int index) {
			super(root, src, dstA, dstB);

			if (index < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.index = index;
		}

		private static String key(int index, String name) {
			return Integer.toString(index) + ":" + name;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.PARAMETER;
		}

		@Override
		public String key() {
			return key(index, src);
		}

		@Override
		public MethodDiff getParent() {
			return parent;
		}

		@Override
		protected void validate() {
			super.validate();

			if (!isDiff() && !javadoc.isDiff()) {
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
	}

	public static class JavadocDiff {

		private String javadocA;
		private String javadocB;

		private Diff<?> parent;

		private JavadocDiff() {
			this("", "");
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
			javadoc = validateDst(javadoc);

			if (side == DiffSide.A) {
				this.javadocA = javadoc;
			} else {
				this.javadocB = javadoc;
			}
		}

		public void clear() {
			this.javadocA = this.javadocB = "";
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
