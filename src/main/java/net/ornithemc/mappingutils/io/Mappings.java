package net.ornithemc.mappingutils.io;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.Mapper;
import net.ornithemc.mappingutils.MappingUtils;

public class Mappings {

	private final Map<String, ClassMapping> classMappings;
	private final Map<String, Collection<ClassMapping>> classMappingsById;

	private MappingNamespace srcNamespace;
	private MappingNamespace dstNamespace;
	private MappingValidator validator;

	private Mappings inverted;

	public Mappings() {
		this(false);
	}

	public Mappings(boolean cacheByIds) {
		this(cacheByIds, MappingNamespace.NONE, MappingNamespace.NONE);
	}

	public Mappings(boolean cacheByIds, MappingNamespace srcNamespace, MappingNamespace dstNamespace) {
		this.classMappings = new LinkedHashMap<>();
		this.classMappingsById = cacheByIds ? new LinkedHashMap<>() : null;

		this.srcNamespace = srcNamespace;
		this.dstNamespace = dstNamespace;

		this.validator = MappingValidator.ALWAYS;
	}

	public MappingNamespace getSrcNamespace() {
		return srcNamespace;
	}

	public MappingNamespace getDstNamespace() {
		return dstNamespace;
	}

	public void setSrcNamespace(MappingNamespace namespace) {
		this.srcNamespace = Objects.requireNonNull(namespace);
	}

	public void setDstNamespace(MappingNamespace namespace) {
		this.dstNamespace = Objects.requireNonNull(namespace);
	}

	public void setSrcNamespace(String namespace) {
		setSrcNamespace(new MappingNamespace(namespace));
	}

	public void setDstNamespace(String namespace) {
		setDstNamespace(new MappingNamespace(namespace));
	}

	public void setValidator(MappingValidator validator) {
		this.validator = validator;
	}

	private ClassMapping findParent(String name, boolean orThrowException) {
		if (!MappingUtils.parseInnerClasses) {
			return null;
		}

		int i = name.lastIndexOf('$');

		if (i < 0) {
			return null;
		}

		String parentName = name.substring(0, i);
		ClassMapping parent = getClass(parentName);

		if (parent == null && orThrowException) {
			throw new IllegalStateException("unable to find parent class mapping " + parentName + " of class mapping " + name);
		}

		return parent;
	}

	public ClassMapping getClass(String name) {
		ClassMapping parent = findParent(name, false);
		return parent == null ? getTopLevelClass(name) : parent.getClass(name);
	}

	public ClassMapping getTopLevelClass(String name) {
		return classMappings.get(ClassMapping.key(name));
	}

	public Collection<ClassMapping> getClasses() {
		Collection<ClassMapping> classes = new LinkedHashSet<>();

		for (ClassMapping c : getTopLevelClasses()) {
			collectClasses(classes, c);
		}

		return classes;
	}

	private void collectClasses(Collection<ClassMapping> classes, ClassMapping c) {
		classes.add(c);

		for (ClassMapping cc : c.getClasses()) {
			collectClasses(classes, cc);
		}
	}

	public Collection<ClassMapping> getTopLevelClasses() {
		return classMappings.values();
	}

	public Collection<ClassMapping> getClasses(String id) {
		if (classMappingsById == null) {
			throw new UnsupportedOperationException("these mappings are not cached by id!");
		}

		return classMappingsById.getOrDefault(id, Collections.emptySet());
	}

	public ClassMapping addClass(String src, String dst) {
		return addClass(new ClassMapping(src, dst));
	}

	private ClassMapping addClass(ClassMapping c) {
		ClassMapping parent = findParent(c.src(), true);

		if (parent == null) {
			c.setRoot(this);

			classMappings.compute(c.key(), (key, value) -> {
				return (ClassMapping)checkReplace(value, c);
			});
		} else {
			parent.addClass(c);
		}

		if (classMappingsById != null) {
			String id = Mapping.getId(c);
			classMappingsById.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(c);
		}

		return c;
	}

	public ClassMapping removeClass(String name) {
		ClassMapping c = getClass(name);
		return (c == null) ? null : removeClass(c);
	}

	public ClassMapping removeClass(ClassMapping c) {
		if (c.root == this) {
			if (c.parent == null) {
				classMappings.remove(c.key());
			} else {
				c.parent.removeClass(c);
			}

			if (classMappingsById != null) {
				String id = Mapping.getId(c);
				getClasses(id).remove(c);
			}

			return c;
		}

		return null;
	}

	public void sort() {
		sort(classMappings);
	}

	public void validate() {
		Iterator<ClassMapping> it = classMappings.values().iterator();

		while (it.hasNext()) {
			ClassMapping c = it.next();

			if (!c.validate()) {
				it.remove();
			}
		}
	}

	public Mappings invert() {
		if (inverted == null) {
			inverted = new Mappings(classMappingsById != null);
			inverted.inverted = this;

			for (ClassMapping c : classMappings.values()) {
				inverted.addClass(c.invert());
			}
		}

		return inverted;
	}

	public Mappings copy() {
		Mappings copy = new Mappings(classMappingsById != null, srcNamespace, dstNamespace);

		for (ClassMapping c : classMappings.values()) {
			c.copy(copy.addClass(c.src(), c.dst));
		}

		return copy;
	}

	public static abstract class Mapping {

		protected final Map<String, Mapping> children;
		protected Map<String, Collection<Mapping>> childrenById;

		protected Mappings root;
		protected Mapping parent;
		protected Mapping inverted;

		protected String src;
		protected String dst;
		protected String jav;

		private Mapping(String src, String dst) {
			this.children = new LinkedHashMap<>();

			this.src = src;
			this.dst = dst;
			this.jav = "";
		}

		@Override
		public final String toString() {
			return getClass().getSimpleName() + "[" + key() + " -> " + dst + "]";
		}

		public abstract MappingTarget target();

		public abstract String key();

		protected boolean isValidChild(MappingTarget target) {
			return false;
		}

		public final String src() {
			return src;
		}

		public final String get() {
			return dst;
		}

		public final void set(String dst) {
			this.dst = validateDst(dst);
		}

		public final String getJavadoc() {
			return jav;
		}

		public final void setJavadoc(String jav) {
			this.jav = validateDst(jav);
		}

		protected final void setRoot(Mappings mappings) {
			this.root = mappings;

			for (Mapping m : children.values()) {
				m.setRoot(mappings);
			}

			if (root.classMappingsById != null) {
				childrenById = new LinkedHashMap<>();
			}
		}

		public Mapping getParent() {
			return parent;
		}

		public final Mapping getChild(MappingTarget target, String key) {
			Mapping m = children.get(key);

			if (m != null && m.target() != target) {
				throw new IllegalStateException("child with key " + m.key() + " has target " + m.target() + " but target " + target + " was requested!");
			}

			return m;
		}

		public final ClassMapping getClass(String name) {
			return getClassByKey(ClassMapping.key(name));
		}

		private ClassMapping getClassByKey(String key) {
			return (ClassMapping)getChild(MappingTarget.CLASS, key);
		}

		public final FieldMapping getField(String name, String desc) {
			return getField(FieldMapping.key(name, desc));
		}

		private FieldMapping getField(String key) {
			return (FieldMapping)getChild(MappingTarget.FIELD, key);
		}

		public final MethodMapping getMethod(String name, String desc) {
			return getMethod(MethodMapping.key(name, desc));
		}

		private MethodMapping getMethod(String key) {
			return (MethodMapping)getChild(MappingTarget.METHOD, key);
		}

		public final ParameterMapping getParameter(String name, int index) {
			return getParameter(ParameterMapping.key(name, index));
		}

		private ParameterMapping getParameter(String key) {
			return (ParameterMapping)getChild(MappingTarget.PARAMETER, key);
		}

		public final Collection<Mapping> getChildren() {
			return children.values();
		}

		public final boolean hasChildren() {
			return !children.isEmpty();
		}

		public final Collection<Mapping> getChildren(MappingTarget target) {
			List<Mapping> c = new LinkedList<>();

			if (isValidChild(target)) {
				for (Mapping m : children.values()) {
					if (m.target() == target) {
						c.add(m);
					}
				}
			}

			return c;
		}

		@SuppressWarnings("unchecked")
		private <M extends Mapping> Collection<M> castChildren(MappingTarget target) {
			return (Collection<M>)getChildren(target);
		}

		public final Collection<ClassMapping> getClasses() {
			return castChildren(MappingTarget.CLASS);
		}

		public final Collection<FieldMapping> getFields() {
			return castChildren(MappingTarget.FIELD);
		}

		public final Collection<MethodMapping> getMethods() {
			return castChildren(MappingTarget.METHOD);
		}

		public final Collection<ParameterMapping> getParameters() {
			return castChildren(MappingTarget.PARAMETER);
		}

		public final Collection<Mapping> getChildren(String id) {
			if (childrenById == null) {
				throw new UnsupportedOperationException("these mappings are not cached by id!");
			}

			return childrenById.getOrDefault(id, Collections.emptySet());
		}

		public final Mapping addChild(MappingTarget target, String key, String dst) {
			switch (target) {
			case CLASS:
				return addClassByKey(key, dst);
			case FIELD:
				return addField(key, dst);
			case METHOD:
				return addMethod(key, dst);
			case PARAMETER:
				return addParameter(key, dst);
			}

			throw new IllegalStateException("invalid child target " + target);
		}

		public final Mapping addChild(Mapping m) {
			if (!isValidChild(m.target()))
				throw new IllegalStateException("invalid child target " + m.target());

			m.setRoot(root);
			m.parent = this;

			children.compute(m.key(), (key, value) -> {
				return checkReplace(value, m);
			});

			if (childrenById != null) {
				String id = getId(m);
				childrenById.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(m);
			}

			return m;
		}

		public final ClassMapping addClass(String src, String dst) {
			return addClassByKey(ClassMapping.key(src), dst);
		}

		private ClassMapping addClassByKey(String key, String dst) {
			return addClass(new ClassMapping(key, dst));
		}

		public ClassMapping addClass(ClassMapping c) {
			return (ClassMapping)addChild(c);
		}

		public final FieldMapping addField(String src, String dst, String desc) {
			return addField(FieldMapping.key(src, desc), dst);
		}

		private FieldMapping addField(String key, String dst) {
			return addField(new FieldMapping(key, dst));
		}

		public FieldMapping addField(FieldMapping f) {
			return (FieldMapping)addChild(f);
		}

		public final MethodMapping addMethod(String src, String dst, String desc) {
			return addMethod(MethodMapping.key(src, desc), dst);
		}

		private MethodMapping addMethod(String key, String dst) {
			return addMethod(new MethodMapping(key, dst));
		}

		public MethodMapping addMethod(MethodMapping m) {
			return (MethodMapping)addChild(m);
		}

		public final ParameterMapping addParameter(String src, String dst, int index) {
			return addParameter(ParameterMapping.key(src, index), dst);
		}

		private ParameterMapping addParameter(String key, String dst) {
			return addParameter(new ParameterMapping(key, dst));
		}

		public ParameterMapping addParameter(ParameterMapping m) {
			return (ParameterMapping)addChild(m);
		}

		public final Mapping removeChild(MappingTarget target, String key) {
			Mapping m = getChild(target, key);
			return m == null ? null : removeChild(m);
		}

		public final Mapping removeChild(Mapping m) {
			if (m.parent == this) {
				children.remove(m.key());

				if (childrenById != null) {
					String id = getId(m);
					getChildren(id).remove(m);
				}

				return m;
			}

			return null;
		}

		public final ClassMapping removeClass(String name) {
			return removeClassByKey(ClassMapping.key(name));
		}

		private ClassMapping removeClassByKey(String key) {
			return removeClass(getClassByKey(key));
		}

		public ClassMapping removeClass(ClassMapping c) {
			return (ClassMapping)removeChild(c);
		}

		public final FieldMapping removeField(String name, String desc) {
			return removeField(FieldMapping.key(name, desc));
		}

		private FieldMapping removeField(String key) {
			return removeField(getField(key));
		}

		public FieldMapping removeField(FieldMapping f) {
			return (FieldMapping)removeChild(f);
		}

		public final MethodMapping removeMethod(String name, String desc) {
			return removeMethod(MethodMapping.key(name, desc));
		}

		private MethodMapping removeMethod(String key) {
			return removeMethod(getMethod(key));
		}

		public MethodMapping removeMethod(MethodMapping m) {
			return (MethodMapping)removeChild(m);
		}

		public final ParameterMapping removeParameter(String name, int index) {
			return removeParameter(ParameterMapping.key(name, index));
		}

		private ParameterMapping removeParameter(String key) {
			return removeParameter(getParameter(key));
		}

		public ParameterMapping removeParameter(ParameterMapping p) {
			return (ParameterMapping)removeChild(p);
		}

		public Mapping invert() {
			root.invert();

			if (inverted == null) {
				inverted = inverted();
				inverted.inverted = this;

				inverted.jav = jav;

				for (Mapping m : children.values()) {
					inverted.addChild(m.invert());
				}
			}

			return inverted;
		}

		protected abstract Mapping inverted();

		protected boolean validate() {
			dst = validateDst(dst);
			jav = validateDst(jav);

			Iterator<Mapping> it = children.values().iterator();

			while (it.hasNext()) {
				Mapping m = it.next();

				if (!m.validate()) {
					it.remove();
				}
			}

			return root.validator.validate(this);
		}

		protected Mapping copy(Mapping copy) {
			copy.jav = jav;

			for (Mapping m : children.values()) {
				m.copy(copy.addChild(m.target(), m.key(), m.dst));
			}

			return copy;
		}

		public static String getId(Mapping m) {
			return getId(m.src());
		}

		public static String getId(String name) {
			int i;

			for (i = name.length(); i > 0; i--) {
				char chr = name.charAt(i - 1);

				if (chr == '$' || chr == '/') {
					break;
				}
				if (chr == '_' && i > 1) {
					char prevChr = name.charAt(i - 2);

					if (prevChr == 'C' || prevChr == 'f' || prevChr == 'm' || prevChr == 'p') {
						break;
					}
				}
			}

			return name.substring(i);
		}
	}

	public static class ClassMapping extends Mapping {

		private ClassMapping(String src, String dst) {
			super(src, dst);
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
		public ClassMapping getParent() {
			return (ClassMapping)parent;
		}

		@Override
		public ClassMapping invert() {
			return (ClassMapping)super.invert();
		}

		@Override
		protected ClassMapping inverted() {
			return new ClassMapping(dst.isEmpty() ? src : getComplete(), getSimplified(src));
		}

		@Override
		protected boolean validate() {
			if (dst.isEmpty()) {
				return super.validate();
			}

			if (MappingUtils.parseInnerClasses) {
				if (dst.contains("$")) {
					throw new IllegalStateException("simple name of " + this + " cannot be nested!");
				}

				String[] srcArgs = src.split("[$]");

				if (srcArgs.length == 1) {
					return super.validate();
				}

				int i = src.lastIndexOf('$');
				String srcParentName = src.substring(0, i);

				if (!parent.src.equals(srcParentName)) {
					throw new IllegalStateException("class mapping " + this + " is not consistent with parent class mapping " + parent);
				}
			}

			return super.validate();
		}

		public String getComplete() {
			// empty dst == map to src
			String name = dst.isEmpty() ? getSimplified(src) : dst;

			if (parent != null && name.lastIndexOf('/') < 0) {
				name = getParent().getComplete() + "$" + name;
			}

			return name;
		}

		public static String getSimplified(String name) {
			int i = name.lastIndexOf('$');
			return name.substring(i + 1);
		}
	}

	public static class FieldMapping extends Mapping {

		private String desc;

		private FieldMapping(String key, String dst) {
			this(key.split("[:]")[0], dst, key.split("[:]")[1]);
		}

		private FieldMapping(String src, String dst, String desc) {
			super(src, dst);

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
		public ClassMapping getParent() {
			return (ClassMapping)parent;
		}

		@Override
		public FieldMapping invert() {
			return (FieldMapping)super.invert();
		}

		@Override
		protected FieldMapping inverted() {
			return new FieldMapping(dst, src, MappingUtils.translateFieldDescriptor(desc, Mapper.of(root)));
		}

		public String getDesc() {
			return desc;
		}
	}

	public static class MethodMapping extends Mapping {

		private final ParameterMapping[] parameters;

		private String desc;

		private MethodMapping(String key, String dst) {
			this(key.split("[:]")[0], dst, key.split("[:]")[1]);
		}

		private MethodMapping(String src, String dst, String desc) {
			super(src, dst);

			this.parameters = new ParameterMapping[parameterCount(desc)];

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
		public ClassMapping getParent() {
			return (ClassMapping)parent;
		}

		@Override
		public MethodMapping invert() {
			return (MethodMapping)super.invert();
		}

		@Override
		protected MethodMapping inverted() {
			return new MethodMapping(dst, src, MappingUtils.translateMethodDescriptor(desc, Mapper.of(root)));
		}

		@Override
		public ParameterMapping addParameter(ParameterMapping p) {
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
		public ParameterMapping removeParameter(ParameterMapping p) {
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

		public ParameterMapping getParameter(int index) {
			return parameters[index];
		}

		public ParameterMapping removeParameter(int index) {
			return removeParameter(getParameter(index));
		}
	}

	public static class ParameterMapping extends Mapping {

		private final int index;

		private ParameterMapping(String key, String dst) {
			this(key.substring(key.indexOf(':') + 1), dst, Integer.parseInt(key.substring(0, key.indexOf(':'))));
		}

		private ParameterMapping(String src, String dst, int index) {
			super(src, dst);

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
		public MethodMapping getParent() {
			return (MethodMapping)parent;
		}

		@Override
		public ParameterMapping invert() {
			return (ParameterMapping)super.invert();
		}

		@Override
		protected ParameterMapping inverted() {
			return new ParameterMapping(dst, src, index);
		}

		public int getIndex() {
			return index;
		}
	}

	private static Mapping checkReplace(Mapping o, Mapping n) {
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

			sort(((Mapping)mapping).children);
		}

		mappings.clear();
		mappings.putAll(sorted);
	}
}
