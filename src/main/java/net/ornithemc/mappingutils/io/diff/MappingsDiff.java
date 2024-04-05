package net.ornithemc.mappingutils.io.diff;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings.Mapping;

public class MappingsDiff {

	private final Map<String, ClassDiff> classDiffs;
	private final Map<String, Collection<ClassDiff>> classDiffsById;

	private MappingsDiffValidator validator;

	public MappingsDiff() {
		this(false);
	}

	public MappingsDiff(boolean cacheById) {
		this.classDiffs = new LinkedHashMap<>();
		this.classDiffsById = cacheById ? new LinkedHashMap<>() : null;

		this.validator = MappingsDiffValidator.ALWAYS;
	}

	public void setValidator(MappingsDiffValidator validator) {
		this.validator = validator;
	}

	private ClassDiff findParent(String name, boolean orThrowException) {
		int i = name.lastIndexOf('$');

		if (i < 0) {
			return null;
		}

		String parentName = name.substring(0, i);
		ClassDiff parent = getClass(parentName);

		if (parent == null && orThrowException) {
			throw new IllegalStateException("unable to find parent class mapping " + parentName + " of class mapping " + name);
		}

		return parent;
	}

	public ClassDiff getClass(String name) {
		ClassDiff parent = findParent(name, false);
		return parent == null ? getTopLevelClass(name) : parent.getClass(name);
	}

	public ClassDiff getTopLevelClass(String name) {
		return classDiffs.get(ClassDiff.key(name));
	}

	public Collection<ClassDiff> getClasses() {
		Collection<ClassDiff> classes = new LinkedHashSet<>();

		for (ClassDiff c : getTopLevelClasses()) {
			collectClasses(classes, c);
		}

		return classes;
	}

	private void collectClasses(Collection<ClassDiff> classes, ClassDiff c) {
		classes.add(c);

		for (ClassDiff cc : c.getClasses()) {
			collectClasses(classes, cc);
		}
	}

	public Collection<ClassDiff> getTopLevelClasses() {
		return classDiffs.values();
	}

	public Collection<ClassDiff> getClasses(String id) {
		if (classDiffsById == null) {
			throw new UnsupportedOperationException("these diffs are not cached by id!");
		}

		return classDiffsById.getOrDefault(id, Collections.emptySet());
	}

	public ClassDiff addClass(String src, String dstA, String dstB) {
		return addClass(new ClassDiff(src, dstA, dstB));
	}

	private ClassDiff addClass(ClassDiff c) {
		ClassDiff parent = findParent(c.src(), true);

		if (parent == null) {
			c.setRoot(this);
			c.parent = null;

			classDiffs.compute(c.key(), (key, value) -> {
				return (ClassDiff)checkReplace(value, c);
			});
		} else {
			parent.addClass(c);
		}

		if (classDiffsById != null) {
			String id = Mapping.getId(c.src());
			classDiffsById.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(c);
		}

		return c;
	}

	public ClassDiff removeClass(String name) {
		ClassDiff c = getClass(name);
		return (c == null) ? null : removeClass(c);
	}

	public ClassDiff removeClass(ClassDiff c) {
		if (c.root == this) {
			if (c.parent == null) {
				classDiffs.remove(c.key());
			} else {
				c.parent.removeClass(c);
			}

			if (classDiffsById != null) {
				String id = Mapping.getId(c.src());
				getClasses(id).remove(c);
			}

			return c;
		}

		return null;
	}

	public void sort() {
		sort(classDiffs);
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
		MappingsDiff copy = new MappingsDiff(classDiffsById != null);

		for (ClassDiff c : classDiffs.values()) {
			c.copy(copy.addClass(c.src(), c.dstA, c.dstB));
		}

		return copy;
	}

	public static boolean isDiff(String a, String b) {
		return a.isEmpty() ? !b.isEmpty() : !a.equals(b);
	}

	public static boolean safeIsDiff(String a, String b) {
		return (a == null || a.isEmpty()) ? !(b == null || b.isEmpty()) : !a.equals(b);
	}

	public static abstract class Diff {

		protected final Map<String, Diff> children;
		protected Map<String, Collection<Diff>> childrenById;
		protected final JavadocDiff jav;

		protected MappingsDiff root;
		protected Diff parent;

		protected String src;
		protected String dstA;
		protected String dstB;

		private Diff(String src, String dstA, String dstB) {
			this.children = new LinkedHashMap<>();
			this.jav = new JavadocDiff();

			this.src = src;
			this.dstA = dstA;
			this.dstB = dstB;
		}

		@Override
		public final String toString() {
			return getClass().getSimpleName() + "[" + key() + " -> --" + dstA + " ++" + dstB + "]";
		}

		public abstract MappingTarget target();

		public abstract String key();

		protected boolean isValidChild(MappingTarget target) {
			return false;
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
			return jav;
		}

		public final boolean isDiff() {
			return MappingsDiff.isDiff(dstA, dstB);
		}

		protected final void setRoot(MappingsDiff diff) {
			this.root = diff;

			for (Diff d : children.values()) {
				d.setRoot(diff);
			}

			if (root.classDiffsById != null) {
				childrenById = new LinkedHashMap<>();
			}
		}

		public Diff getParent() {
			return parent;
		}

		public final Diff getChild(MappingTarget target, String key) {
			Diff d = children.get(key);

			if (d != null && d.target() != target) {
				throw new IllegalStateException("child with key " + d.key() + " has target " + d.target() + " but target " + target + " was requested!");
			}

			return d;
		}

		public final ClassDiff getClass(String name) {
			return getClassByKey(ClassDiff.key(name));
		}

		private ClassDiff getClassByKey(String key) {
			return (ClassDiff)getChild(MappingTarget.CLASS, key);
		}

		public final FieldDiff getField(String name, String desc) {
			return getField(FieldDiff.key(name, desc));
		}

		private FieldDiff getField(String key) {
			return (FieldDiff)getChild(MappingTarget.FIELD, key);
		}

		public final MethodDiff getMethod(String name, String desc) {
			return getMethod(MethodDiff.key(name, desc));
		}

		private MethodDiff getMethod(String key) {
			return (MethodDiff)getChild(MappingTarget.METHOD, key);
		}

		public final ParameterDiff getParameter(String name, int index) {
			return getParameter(ParameterDiff.key(name, index));
		}

		private ParameterDiff getParameter(String key) {
			return (ParameterDiff)getChild(MappingTarget.PARAMETER, key);
		}

		public final Collection<Diff> getChildren() {
			return children.values();
		}

		public final boolean hasChildren() {
			return !children.isEmpty();
		}

		public final Collection<Diff> getChildren(MappingTarget target) {
			List<Diff> c = new LinkedList<>();

			if (isValidChild(target)) {
				for (Diff d : children.values()) {
					if (d.target() == target) {
						c.add(d);
					}
				}
			}

			return c;
		}

		@SuppressWarnings("unchecked")
		private <M extends Diff> Collection<M> castChildren(MappingTarget target) {
			return (Collection<M>)getChildren(target);
		}

		public final Collection<ClassDiff> getClasses() {
			return castChildren(MappingTarget.CLASS);
		}

		public final Collection<FieldDiff> getFields() {
			return castChildren(MappingTarget.FIELD);
		}

		public final Collection<MethodDiff> getMethods() {
			return castChildren(MappingTarget.METHOD);
		}

		public final Collection<ParameterDiff> getParameters() {
			return castChildren(MappingTarget.PARAMETER);
		}

		public final Collection<Diff> getChildren(String id) {
			if (childrenById == null) {
				throw new UnsupportedOperationException("these diffs are not cached by id!");
			}

			return childrenById.getOrDefault(id, Collections.emptySet());
		}

		public final Diff addChild(MappingTarget target, String key, String dstA, String dstB) {
			switch (target) {
			case CLASS:
				return addClassByKey(key, dstA, dstB);
			case FIELD:
				return addField(key, dstA, dstB);
			case METHOD:
				return addMethod(key, dstA, dstB);
			case PARAMETER:
				return addParameter(key, dstA, dstB);
			}

			throw new IllegalStateException("invalid child target " + target);
		}

		public final Diff addChild(Diff d) {
			if (!isValidChild(d.target()))
				throw new IllegalStateException("invalid child target " + d.target());

			d.setRoot(root);
			d.parent = this;

			children.compute(d.key(), (key, value) -> {
				return checkReplace(value, d);
			});

			if (childrenById != null) {
				String id = Mapping.getId(d.src());
				childrenById.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(d);
			}

			return d;
		}

		public final ClassDiff addClass(String src, String dstA, String dstB) {
			return addClassByKey(ClassDiff.key(src), dstA, dstB);
		}

		private ClassDiff addClassByKey(String key, String dstA, String dstB) {
			return addClass(new ClassDiff(key, dstA, dstB));
		}

		public ClassDiff addClass(ClassDiff c) {
			return (ClassDiff)addChild(c);
		}

		public final FieldDiff addField(String src, String dstA, String dstB, String desc) {
			return addField(FieldDiff.key(src, desc), dstA, dstB);
		}

		private FieldDiff addField(String key, String dstA, String dstB) {
			return addField(new FieldDiff(key, dstA, dstB));
		}

		public FieldDiff addField(FieldDiff f) {
			return (FieldDiff)addChild(f);
		}

		public final MethodDiff addMethod(String src, String dstA, String dstB, String desc) {
			return addMethod(MethodDiff.key(src, desc), dstA, dstB);
		}

		private MethodDiff addMethod(String key, String dstA, String dstB) {
			return addMethod(new MethodDiff(key, dstA, dstB));
		}

		public MethodDiff addMethod(MethodDiff m) {
			return (MethodDiff)addChild(m);
		}

		public final ParameterDiff addParameter(String src, String dstA, String dstB, int index) {
			return addParameter(ParameterDiff.key(src, index), dstA, dstB);
		}

		private ParameterDiff addParameter(String key, String dstA, String dstB) {
			return addParameter(new ParameterDiff(key, dstA, dstB));
		}

		public ParameterDiff addParameter(ParameterDiff m) {
			return (ParameterDiff)addChild(m);
		}

		public final Diff removeChild(MappingTarget target, String key) {
			Diff d = getChild(target, key);
			return d == null ? null : removeChild(d);
		}

		public final Diff removeChild(Diff d) {
			if (d.parent == this) {
				children.remove(d.key());

				if (childrenById != null) {
					String id = Mapping.getId(d.src());
					getChildren(id).remove(d);
				}

				return d;
			}

			return null;
		}

		public final ClassDiff removeClass(String name) {
			return removeClassByKey(ClassDiff.key(name));
		}

		private ClassDiff removeClassByKey(String key) {
			return removeClass(getClassByKey(key));
		}

		public ClassDiff removeClass(ClassDiff c) {
			return (ClassDiff)removeChild(c);
		}

		public final FieldDiff removeField(String name, String desc) {
			return removeField(FieldDiff.key(name, desc));
		}

		private FieldDiff removeField(String key) {
			return removeField(getField(key));
		}

		public FieldDiff removeField(FieldDiff f) {
			return (FieldDiff)removeChild(f);
		}

		public final MethodDiff removeMethod(String name, String desc) {
			return removeMethod(MethodDiff.key(name, desc));
		}

		private MethodDiff removeMethod(String key) {
			return removeMethod(getMethod(key));
		}

		public MethodDiff removeMethod(MethodDiff m) {
			return (MethodDiff)removeChild(m);
		}

		public final ParameterDiff removeParameter(String name, int index) {
			return removeParameter(ParameterDiff.key(name, index));
		}

		private ParameterDiff removeParameter(String key) {
			return removeParameter(getParameter(key));
		}

		public ParameterDiff removeParameter(ParameterDiff p) {
			return (ParameterDiff)removeChild(p);
		}

		protected final boolean validate() {
			jav.validate();

			dstA = validateDst(dstA);
			dstB = validateDst(dstB);

			if (!isDiff()) {
				clear();
			}

			Iterator<Diff> it = children.values().iterator();

			while (it.hasNext()) {
				Diff d = it.next();

				if (!d.validate()) {
					it.remove();
				}
			}

			return (root.validator.validate(this) && (isDiff() || jav.isDiff())) || hasChildren();
		}

		protected Diff copy(Diff copy) {
			copy.jav.javA = jav.javA;
			copy.jav.javB = jav.javB;

			for (Diff d : children.values()) {
				d.copy(copy.addChild(d.target(), d.key(), d.dstA, d.dstB));
			}

			return copy;
		}
	}

	public static class ClassDiff extends Diff {

		private ClassDiff(String src, String dstA, String dstB) {
			super(src, dstA, dstB);
		}

		private static String key(String name) {
			return name;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.CLASS;
		}

		@Override
		public String key() {
			return key(src);
		}

		@Override
		protected boolean isValidChild(MappingTarget target) {
			return target == MappingTarget.CLASS || target == MappingTarget.FIELD || target == MappingTarget.METHOD;
		}

		@Override
		public ClassDiff getParent() {
			return (ClassDiff)parent;
		}
	}

	public static class FieldDiff extends Diff {

		private String desc;

		private FieldDiff(String key, String dstA, String dstB) {
			this(key.split("[:]")[0], dstA, dstB, key.split("[:]")[1]);
		}

		private FieldDiff(String src, String dstA, String dstB, String desc) {
			super(src, dstA, dstB);

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

	public static class MethodDiff extends Diff {

		private final ParameterDiff[] parameters;

		private String desc;

		private MethodDiff(String key, String dstA, String dstB) {
			this(key.split("[:]")[0], dstA, dstB, key.split("[:]")[1]);
		}

		private MethodDiff(String src, String dstA, String dstB, String desc) {
			super(src, dstA, dstB);

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
		protected boolean isValidChild(MappingTarget target) {
			return target == MappingTarget.PARAMETER;
		}

		@Override
		public ClassDiff getParent() {
			return (ClassDiff)parent;
		}

		@Override
		public ParameterDiff addParameter(ParameterDiff p) {
			p = super.addParameter(p);

			if (p != null) {
				if (p.index < 0 || p.index >= parameters.length) {
					throw new IndexOutOfBoundsException(p.index + " given but 0-" + parameters.length + " expected");
				}

				parameters[p.index] = p;
			}

			return p;
		}

		@Override
		public ParameterDiff removeParameter(ParameterDiff p) {
			p = super.removeParameter(p);

			if (p != null) {
				parameters[p.index] = null;
			}

			return p;
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

		public ParameterDiff removeParameter(int index) {
			return removeParameter(getParameter(index));
		}
	}

	public static class ParameterDiff extends Diff {

		private final int index;

		private ParameterDiff(String key, String dstA, String dstB) {
			this(key.substring(key.indexOf(':') + 1), dstA, dstB, Integer.parseInt(key.substring(0, key.indexOf(':'))));
		}

		private ParameterDiff(String src, String dstA, String dstB, int index) {
			super(src, dstA, dstB);

			if (index < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.index = index;
		}

		private static String key(String name, int index) {
			return Integer.toString(index) + ":" + name;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.PARAMETER;
		}

		@Override
		public String key() {
			return key(src, index);
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

		private String javA;
		private String javB;

		private Diff parent;

		private JavadocDiff() {
			this("", "");
		}

		private JavadocDiff(String javA, String javB) {
			this.javA = javA;
			this.javB = javB;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[--" + javA + " ++" + javB + "]";
		}

		public String get(DiffSide side) {
			return side == DiffSide.A ? javA : javB;
		}

		public void set(DiffSide side, String jav) {
			jav = validateDst(jav);

			if (side == DiffSide.A) {
				this.javA = jav;
			} else {
				this.javB = jav;
			}
		}

		public void clear() {
			this.javA = this.javB = "";
		}

		public boolean isDiff() {
			return MappingsDiff.isDiff(javA, javB);
		}

		public Diff getParent() {
			return parent;
		}

		protected void validate() {
			javA = validateDst(javA);
			javB = validateDst(javB);
		}

		protected JavadocDiff copy() {
			return new JavadocDiff(javA, javB);
		}
	}

	private static Diff checkReplace(Diff o, Diff n) {
		if (o != null && n != null) {
			System.err.println("replacing mapping " + o + " with " + n);
		}

		return n;
	}

	private static String validateDst(String dst) {
		return dst == null ? "" : dst;
	}

	private static <T> void sort(Map<String, T> mappings) {
		Map<String, T> sorted = new TreeMap<>((k1, k2) -> {
			// attempt to sort by name only
			// if name matches, sort by name + desc
			int l1 = k1.indexOf(':');
			int l2 = k2.indexOf(':');

			if (l1 < 0) l1 = k1.length();
			if (l2 < 0) l2 = k2.length();

			return l1 == l2 ? k1.compareTo(k2) : l1 - l2;
		});

		for (Entry<String, T> entry : mappings.entrySet()) {
			String key = entry.getKey();
			T mapping = entry.getValue();

			sorted.put(key, mapping);

			sort(((Diff)mapping).children);
		}

		mappings.clear();
		mappings.putAll(sorted);
	}
}
