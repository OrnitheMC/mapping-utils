package net.ornithemc.mappingutils.io.diff;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.MappingTarget;

public class MappingsDiff {

	private final Map<String, ClassDiff> classDiffs;

	private MappingsDiffValidator validator;

	public MappingsDiff() {
		this.classDiffs = new LinkedHashMap<>();

		this.validator = MappingsDiffValidator.ALWAYS;
	}

	public void setValidator(MappingsDiffValidator validator) {
		this.validator = validator;
	}

	private ClassDiff findParent(String key, boolean orThrowException) {
		int i = key.lastIndexOf('$');

		if (i < 0) {
			return null;
		}

		String parentKey = key.substring(0, i);
		ClassDiff parent = getClass(parentKey);

		if (parent == null && orThrowException) {
			throw new IllegalStateException("unable to find parent class diff " + parentKey + " of class diff " + key);
		}

		return parent;
	}

	public ClassDiff getClass(String key) {
		ClassDiff parent = findParent(key, false);
		return parent == null ? getTopLevelClass(key) : parent.getClass(key);
	}

	public ClassDiff getTopLevelClass(String key) {
		return classDiffs.get(key);
	}

	public Collection<ClassDiff> getTopLevelClasses() {
		return classDiffs.values();
	}

	public ClassDiff addClass(String src, String dstA, String dstB) {
		return addClass(new ClassDiff(this, src, dstA, dstB));
	}

	private ClassDiff addClass(ClassDiff c) {
		ClassDiff parent = findParent(c.key(), true);

		if (parent == null) {
			return classDiffs.compute(c.key(), (key, value) -> {
				return (ClassDiff)checkReplace(value, c);
			});
		}

		return (ClassDiff)parent.addChild(c);
	}

	public ClassDiff removeClass(String key) {
		ClassDiff c = getClass(key);

		if (c != null) {
			if (c.parent == null) {
				classDiffs.remove(key);
			} else {
				c.parent.removeChild(key);
			}
		}

		return c;
	}

	public void validate() {
		Iterator<ClassDiff> it = classDiffs.values().iterator();

		while (it.hasNext()) {
			ClassDiff c = it.next();

			if (!c.validate()) {
				it.remove();
			}
		}
	}

	public MappingsDiff copy() {
		MappingsDiff copy = new MappingsDiff();

		for (ClassDiff c : classDiffs.values()) {
			c.copy(copy.addClass(c.key(), c.dstA, c.dstB));
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

		protected final MappingsDiff root;
		protected final JavadocDiff javadoc;
		protected final Map<String, Diff<?>> children;

		protected Diff<?> parent;

		protected String src;
		protected String dstA;
		protected String dstB;

		private Diff(MappingsDiff root, String src, String dstA, String dstB) {
			this.root = Objects.requireNonNull(root);
			this.javadoc = new JavadocDiff();

			this.children = new LinkedHashMap<>();

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
			return parent;
		}

		public Diff<?> getChild(MappingTarget target, String key) {
			return getChild(key);
		}

		@SuppressWarnings("unchecked")
		protected <D extends Diff<D>> D getChild(String key) {
			return (D)children.get(key);
		}

		public Collection<Diff<?>> getChildren() {
			return children.values();
		}

		@SuppressWarnings("unchecked")
		protected <D extends Diff<D>> Collection<D> getChildren(MappingTarget target) {
			List<D> c = new LinkedList<>();

			for (Diff<?> d : children.values()) {
				if (d.target() == target) {
					c.add((D)d);
				}
			}

			return c;
		}

		public boolean hasChildren() {
			return !children.isEmpty();
		}

		public Diff<?> addChild(MappingTarget target, String key, String dstA, String dstB) {
			switch (target) {
			case CLASS:
				return addChild(new ClassDiff(root, key, dstA, dstB));
			case FIELD:
				return addChild(new FieldDiff(root, key, dstA, dstB));
			case METHOD:
				return addChild(new MethodDiff(root, key, dstA, dstB));
			case PARAMETER:
				return addChild(new ParameterDiff(root, key, dstA, dstB));
			default:
				throw new IllegalStateException("cannot add child diff of target " + target);
			}
		}

		protected Diff<?> addChild(Diff<?> d) {
			d.parent = this;

			return children.compute(d.key(), (key, value) -> {
				return checkReplace(value, d);
			});
		}

		public Diff<?> removeChild(MappingTarget target, String key) {
			return removeChild(key);
		}

		@SuppressWarnings("unchecked")
		protected <D extends Diff<D>> D removeChild(String key) {
			return (D)children.remove(key);
		}

		protected boolean validate() {
			javadoc.validate();

			dstA = validateDst(dstA);
			dstB = validateDst(dstB);

			if (!isDiff()) {
				clear();
			}

			Iterator<Diff<?>> it = children.values().iterator();

			while (it.hasNext()) {
				Diff<?> d = it.next();

				if (!d.validate()) {
					it.remove();
				}
			}

			return (root.validator.validate(this) && (isDiff() || javadoc.isDiff())) || hasChildren();
		}

		protected Diff<?> copy(Diff<?> copy) {
			copy.javadoc.javadocA = javadoc.javadocA;
			copy.javadoc.javadocB = javadoc.javadocB;

			for (Diff<?> d : children.values()) {
				d.copy(copy.addChild(d.target(), d.key(), d.dstA, d.dstB));
			}

			return copy;
		}
	}

	public static class ClassDiff extends Diff<ClassDiff> {

		private ClassDiff(MappingsDiff root, String src, String dstA, String dstB) {
			super(root, src, dstA, dstB);
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.CLASS;
		}

		@Override
		public ClassDiff getParent() {
			return (ClassDiff)parent;
		}

		public ClassDiff getClass(String key) {
			return getChild(key);
		}

		public FieldDiff getField(String name, String desc) {
			return getField(FieldDiff.key(name, desc));
		}

		public FieldDiff getField(String key) {
			return getChild(key);
		}

		public MethodDiff getMethod(String name, String desc) {
			return getMethod(MethodDiff.key(name, desc));
		}

		public MethodDiff getMethod(String key) {
			return getChild(key);
		}

		public Collection<ClassDiff> getClasses() {
			return getChildren(MappingTarget.CLASS);
		}

		public Collection<FieldDiff> getFields() {
			return getChildren(MappingTarget.FIELD);
		}

		public Collection<MethodDiff> getMethods() {
			return getChildren(MappingTarget.METHOD);
		}

		public ClassDiff addClass(String src, String dstA, String dstB) {
			return (ClassDiff)addChild(new ClassDiff(root, src, dstA, dstB));
		}

		public FieldDiff addField(String name, String dstA, String dstB, String desc) {
			return addField(FieldDiff.key(name, desc), dstA, dstB);
		}

		public FieldDiff addField(String key, String dstA, String dstB) {
			return (FieldDiff)addChild(new FieldDiff(root, key, dstA, dstB));
		}

		public MethodDiff addMethod(String name, String dstA, String dstB, String desc) {
			return addMethod(MethodDiff.key(name, desc), dstA, dstB);
		}

		public MethodDiff addMethod(String key, String dstA, String dstB) {
			return (MethodDiff)addChild(new MethodDiff(root, key, dstA, dstB));
		}

		public ClassDiff removeClass(String key) {
			return removeChild(key);
		}

		public FieldDiff removeField(String name, String desc) {
			return removeField(FieldDiff.key(name, desc));
		}

		public FieldDiff removeField(String key) {
			return removeChild(key);
		}

		public MethodDiff removeMethod(String name, String desc) {
			return removeMethod(FieldDiff.key(name, desc));
		}

		public MethodDiff removeMethod(String key) {
			return removeChild(key);
		}
	}

	public static class FieldDiff extends Diff<FieldDiff> {

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
			return (ClassDiff)parent;
		}

		public String getDesc() {
			return desc;
		}
	}

	public static class MethodDiff extends Diff<MethodDiff> {

		private final ParameterDiff[] parameters;

		private String desc;

		private MethodDiff(MappingsDiff root, String key, String dstA, String dstB) {
			this(root, key.split("[:]")[0], dstA, dstB, key.split("[:]")[1]);
		}

		private MethodDiff(MappingsDiff root, String src, String dstA, String dstB, String desc) {
			super(root, src, dstA, dstB);

			this.parameters = new ParameterDiff[parameterCount(desc)];

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
			return (ClassDiff)parent;
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
			return getChild(key);
		}

		public Collection<ParameterDiff> getParameters() {
			return getChildren(MappingTarget.PARAMETER);
		}

		public ParameterDiff addParameter(String name, String dstA, String dstB, int index) {
			return addParameter(ParameterDiff.key(index, name), dstA, dstB);
		}

		public ParameterDiff addParameter(String key, String dstA, String dstB) {
			return (ParameterDiff)addChild(new ParameterDiff(root, key, dstA, dstB));
		}

		public ParameterDiff removeParameter(int index) {
			ParameterDiff p = getParameter(index);

			if (p != null) {
				removeChild(p.key());
			}

			return p;
		}

		public ParameterDiff removeParameter(String key) {
			return removeChild(key);
		}
	}

	public static class ParameterDiff extends Diff<ParameterDiff> {

		private final int index;

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
			return (MethodDiff)parent;
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
			return new JavadocDiff(javadocA, javadocB);
		}
	}

	private static Diff<?> checkReplace(Diff<?> o, Diff<?> n) {
		if (o != null && n != null) {
			System.err.println("replacing diff " + o + " with " + n);
		}

		return n;
	}

	private static String validateDst(String dst) {
		return dst == null ? "" : dst;
	}
}
